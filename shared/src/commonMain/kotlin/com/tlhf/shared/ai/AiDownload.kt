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
