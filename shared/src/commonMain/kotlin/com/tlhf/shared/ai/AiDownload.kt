package com.tlhf.shared.ai

/**
 * Model download state + pure helpers. The actual byte-moving loop lives on
 * each platform (Android: HttpURLConnection with Range resume, ported from the
 * web app's Capacitor plugin; iOS: URLSession), but every rule below is shared:
 *
 * - WiFi-only downloads by default (user can allow cellular)
 * - resume from a `.part` file via the HTTP Range header
 * - up to 5 attempts, backoff min(4s * attempt, 15s)
 * - 416 (range unsatisfiable) restarts from scratch
 * - a download only counts as finished when its byte count matches the
 *   catalog's expected size exactly — a stream that closes cleanly but
 *   early (CDN/proxy truncation) is NOT a finished download
 * - model auto-unloads from RAM after 30 minutes idle
 */
enum class ModelState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    PAUSED,
    DOWNLOADED,
    LOADING,
    READY,
    ERROR
}

data class DownloadProgress(val bytesDownloaded: Long, val totalBytes: Long) {
    val fraction: Float
        get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    val percent: Int get() = (fraction * 100).toInt()
    val label: String get() = "${formatBytes(bytesDownloaded)} / ${formatBytes(totalBytes)}"
}

/**
 * The completeness rule, shared so Android, iOS and the tests all agree:
 * with a known expected size the byte count must match it EXACTLY — fewer
 * bytes means a truncated file that will fail to load, more means the bytes
 * on disk are not the file the catalog described. An unknown expectation
 * (<= 0) cannot fail a download: the caller either passes the response's
 * total in its place, or there is simply nothing to check against.
 */
fun isCompleteDownload(bytesDownloaded: Long, expectedBytes: Long): Boolean = when {
    bytesDownloaded < 0 -> false
    expectedBytes <= 0 -> true
    else -> bytesDownloaded == expectedBytes
}

/** What is actually on disk for a model file, derived after e.g. process death. */
enum class DownloadFileState { NONE, PARTIAL, COMPLETE }

/**
 * Derives download state purely from what's on disk, so a relaunched app can
 * tell "nothing there" from "resumable partial" from "all bytes present"
 * without trusting any in-memory state (which died with the process).
 * COMPLETE covers both the renamed destination file and a `.part` that
 * already holds every expected byte (process died between the last byte
 * and the rename — a resume can finish it without re-downloading).
 */
fun downloadFileState(destExists: Boolean, partBytes: Long, expectedBytes: Long): DownloadFileState = when {
    destExists -> DownloadFileState.COMPLETE
    partBytes <= 0 -> DownloadFileState.NONE
    expectedBytes > 0 && partBytes >= expectedBytes -> DownloadFileState.COMPLETE
    else -> DownloadFileState.PARTIAL
}

/**
 * Progress for a leftover `.part` file. An unknown expected size (<= 0) is
 * reported as total -1 so the label shows "?" instead of a fake 100%.
 */
fun partialProgress(partBytes: Long, expectedBytes: Long): DownloadProgress =
    DownloadProgress(
        bytesDownloaded = maxOf(0L, partBytes),
        totalBytes = if (expectedBytes > 0) expectedBytes else -1L
    )

/** 30-minute idle auto-unload, matching the web app. */
const val AI_IDLE_UNLOAD_MS = 30 * 60 * 1000L

fun shouldAutoUnload(lastUsedMs: Long, nowMs: Long): Boolean =
    nowMs - lastUsedMs >= AI_IDLE_UNLOAD_MS

const val MAX_DOWNLOAD_ATTEMPTS = 5

/** Backoff between download retries: min(4s * attempt, 15s). */
fun downloadBackoffMs(attempt: Int): Long = minOf(4_000L * attempt, 15_000L)

fun formatBytes(bytes: Long): String = when {
    bytes < 0 -> "?"
    bytes < 1_024L -> "$bytes B"
    bytes < 1_048_576L -> "${bytes / 1_024L} KB"
    bytes < 1_073_741_824L -> "${bytes / 1_048_576L} MB"
    else -> String.format("%.2f GB", bytes / 1_073_741_824.0)
}
