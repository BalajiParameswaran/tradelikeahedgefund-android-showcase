package com.tradelikeahedgefund.app.ai

import com.tlhf.shared.ai.MAX_DOWNLOAD_ATTEMPTS
import com.tlhf.shared.ai.downloadBackoffMs
import com.tlhf.shared.ai.isCompleteDownload
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Resumable model-file downloader — Kotlin port of the web app's
 * OnDeviceAIPlugin download loop:
 *
 * - Range-resume from a `.part` file; 416 restarts from scratch
 * - up to 5 attempts, backoff min(4s * attempt, 15s)
 * - pause / cancel / resume
 * - progress callbacks throttled to ~300ms
 *
 * Two fixes over the original port, both learned from real phone failures:
 *
 * - Completeness is validated against the catalog's expected byte count
 *   ([isCompleteDownload]). A stream that closes cleanly but early used to
 *   be renamed into place as a "finished" model that then failed to load.
 * - Pause closes the connection instead of parking mid-stream. Servers kill
 *   idle sockets, and each kill used to burn one of the 5 attempts — a long
 *   pause effectively failed the download. Pausing now costs no attempts;
 *   resume re-opens with a Range request from the `.part` length.
 *
 * Runs on its own thread; all listener calls come from that thread —
 * the UI must hop to the main thread (the Compose screens do).
 */
class ModelDownloader {

    interface Listener {
        fun onProgress(bytesDownloaded: Long, totalBytes: Long)
        /** error == null means the file downloaded completely. */
        fun onDone(error: String?)
    }

    /**
     * Control-flow signal, not an error: the read loop throws this when a
     * pause is requested so the connection closes and [downloadLoop] can
     * park without consuming a retry attempt.
     */
    private class PausedDownload : Exception("download paused")

    @Volatile private var thread: Thread? = null
    @Volatile private var pauseRequested = false
    @Volatile private var cancelRequested = false
    @Volatile private var lastUrl: String? = null

    /**
     * The connection the worker thread is currently blocked on, so pause()
     * and cancel() can disconnect it and unblock a read immediately instead
     * of waiting out the 20s read timeout.
     */
    @Volatile private var activeConnection: HttpURLConnection? = null

    @Volatile var bytesDownloaded: Long = 0L
        private set
    @Volatile var totalBytes: Long = -1L
        private set

    val isRunning: Boolean get() = thread?.isAlive == true

    /**
     * @param expectedBytes the catalog's size for this model (<= 0 = unknown);
     * the download is only accepted when the byte count matches it.
     * @return null when the download started, or a human-readable reason why not
     * ("wifi_required", "download already running").
     */
    fun start(
        url: String,
        destFile: File,
        expectedBytes: Long,
        wifiOnly: Boolean,
        isWifiNow: () -> Boolean,
        listener: Listener
    ): String? {
        if (thread?.isAlive == true) return "download already running"
        if (wifiOnly && !isWifiNow()) return "wifi_required"
        pauseRequested = false
        cancelRequested = false
        thread = Thread { downloadLoop(url, destFile, expectedBytes, listener) }.also { it.start() }
        return null
    }

    fun pause() {
        pauseRequested = true
        // Close the connection now: the read loop throws PausedDownload and
        // the loop parks. Holding the socket open while parked is what let
        // the server kill it and burn retry attempts.
        activeConnection?.disconnect()
    }

    /**
     * @return null when the download resumed/started (or there was nothing
     * to do), or "wifi_required" when the WiFi-only rule blocks it. The
     * WiFi check applies on every path that (re)opens a connection —
     * including the remembered-URL fast path, which used to skip it.
     */
    fun resume(
        url: String,
        destFile: File,
        expectedBytes: Long,
        wifiOnly: Boolean,
        isWifiNow: () -> Boolean,
        listener: Listener
    ): String? {
        if (thread?.isAlive == true) {
            if (!pauseRequested) return null // already downloading, nothing to resume
            // The worker is parked in the pause wait; unparking it re-opens
            // the connection, so the WiFi rule applies here too.
            if (wifiOnly && !isWifiNow()) return "wifi_required"
            pauseRequested = false
            return null
        }
        if (!cancelRequested && destFile.exists()) return null // already complete
        val part = File(destFile.absolutePath + ".part")
        if (!part.exists()) return null
        cancelRequested = false
        // Route both URL sources through start() so its checks (already
        // running, WiFi-only) can never be bypassed.
        return start(lastUrl ?: url, destFile, expectedBytes, wifiOnly, isWifiNow, listener)
    }

    fun cancel() {
        cancelRequested = true
        pauseRequested = false
        // Disconnect so a worker blocked in read() notices now, not when
        // the 20s read timeout eventually fires.
        activeConnection?.disconnect()
    }

    fun delete(destFile: File) {
        cancelRequested = true
        activeConnection?.disconnect()
        destFile.delete()
        File(destFile.absolutePath + ".part").delete()
        bytesDownloaded = 0
        totalBytes = -1
    }

    /**
     * Bytes already on disk in the `.part` file (0 when there is none).
     * After process death this file IS the resume state — in-memory fields
     * are gone, but the next start/resume picks up from exactly this length.
     */
    fun partBytes(destFile: File): Long {
        val part = File(destFile.absolutePath + ".part")
        return if (part.exists()) part.length() else 0L
    }

    private fun downloadLoop(
        url: String,
        destFile: File,
        expectedBytes: Long,
        listener: Listener
    ) {
        lastUrl = url
        var attempt = 0
        while (true) {
            if (cancelRequested) { listener.onDone("cancelled"); break }
            // Park BEFORE starting an attempt so waking from pause doesn't
            // count as one. The thread stays alive, so isRunning stays true.
            if (pauseRequested) { waitWhilePaused(); continue }
            attempt++
            try {
                downloadOnce(url, destFile, expectedBytes, listener)
                listener.onDone(if (cancelRequested) "cancelled" else null)
                break
            } catch (e: PausedDownload) {
                // Pause is not a failure: hand the attempt back and park.
                // The next downloadOnce re-opens with a Range request from
                // the .part length, so no bytes are re-downloaded.
                attempt--
                waitWhilePaused()
            } catch (e: Exception) {
                if (cancelRequested) { listener.onDone("cancelled"); break }
                if (pauseRequested) {
                    // The failure is the pause itself (pause() disconnected
                    // the socket mid-read, or the truncated-stream check
                    // fired on the cut connection) — not a real error, so
                    // it must not consume an attempt either.
                    attempt--
                    waitWhilePaused()
                    continue
                }
                if (attempt >= MAX_DOWNLOAD_ATTEMPTS) { listener.onDone(e.message ?: "download failed"); break }
                try { Thread.sleep(downloadBackoffMs(attempt)) } catch (_: InterruptedException) {}
            }
        }
        thread = null
    }

    /** Sleeps while a pause is in effect; the worker thread stays alive. */
    private fun waitWhilePaused() {
        while (pauseRequested && !cancelRequested) {
            try { Thread.sleep(200) } catch (_: InterruptedException) {}
        }
    }

    private fun downloadOnce(urlStr: String, destFile: File, expectedBytes: Long, listener: Listener) {
        val part = File(destFile.absolutePath + ".part")
        var existing = if (part.exists()) part.length() else 0L
        // A .part LONGER than the model can never become the model —
        // those bytes aren't the catalog's file, so don't build on them.
        if (expectedBytes > 0 && existing > expectedBytes) { part.delete(); existing = 0 }
        bytesDownloaded = existing

        if (expectedBytes > 0 && existing == expectedBytes && existing > 0) {
            // Process died after the last byte but before the rename:
            // the file is already fully on disk, finish without network.
            finishDownload(part, destFile, expectedBytes, listener)
            return
        }
        if (pauseRequested && !cancelRequested) throw PausedDownload()

        var conn = open(urlStr, existing)
        var code = conn.responseCode
        var contentLen = conn.getHeaderFieldLong("Content-Length", -1)
        if (code == 416) {
            closeConnection(conn)
            part.delete()
            existing = 0
            bytesDownloaded = 0
            conn = open(urlStr, 0)
            code = conn.responseCode
            contentLen = conn.getHeaderFieldLong("Content-Length", -1)
        }
        if (code == 206) {
            totalBytes = existing + contentLen
        } else if (code == 200) {
            totalBytes = contentLen
            if (existing > 0) { part.delete(); existing = 0; bytesDownloaded = 0 }
        } else {
            closeConnection(conn)
            throw Exception("download failed (http $code)")
        }
        try {
            conn.inputStream.use { input ->
                RandomAccessFile(part, "rw").use { raf ->
                    raf.seek(existing)
                    val buf = ByteArray(65536)
                    var lastEmit = 0L
                    while (true) {
                        if (cancelRequested) return
                        // Never park mid-stream: bail out (closing the
                        // connection via the finally below) and let
                        // downloadLoop park with no socket held open.
                        if (pauseRequested) throw PausedDownload()
                        val n = input.read(buf)
                        if (n == -1) break
                        raf.write(buf, 0, n)
                        bytesDownloaded += n
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 300) {
                            lastEmit = now
                            listener.onProgress(bytesDownloaded, totalBytes)
                        }
                    }
                }
            }
        } finally {
            closeConnection(conn)
        }
        // The stream ended — but a clean close is not proof of a complete
        // file (CDNs/proxies can truncate politely). Trust the byte count,
        // not the close. On mismatch keep the .part file: throwing here
        // makes the retry loop resume from its length via Range instead
        // of renaming a corrupt model into place.
        val effectiveExpected = if (expectedBytes > 0) expectedBytes else totalBytes
        if (!isCompleteDownload(bytesDownloaded, effectiveExpected)) {
            throw Exception("incomplete download: got $bytesDownloaded of $effectiveExpected bytes")
        }
        finishDownload(part, destFile, expectedBytes, listener)
    }

    /** Renames a byte-complete .part into place and verifies the result. */
    private fun finishDownload(part: File, destFile: File, expectedBytes: Long, listener: Listener) {
        if (destFile.exists()) destFile.delete()
        if (!part.renameTo(destFile)) throw Exception("could not save model file")
        val actual = destFile.length()
        if (expectedBytes > 0 && actual != expectedBytes) {
            // Proven the wrong size — remove it so nothing downstream
            // mistakes it for a downloaded model.
            destFile.delete()
            throw Exception("incomplete download: got $actual of $expectedBytes bytes")
        }
        bytesDownloaded = actual
        totalBytes = bytesDownloaded
        listener.onProgress(bytesDownloaded, totalBytes)
    }

    private fun closeConnection(conn: HttpURLConnection) {
        conn.disconnect()
        if (activeConnection === conn) activeConnection = null
    }

    private fun open(urlStr: String, existing: Long): HttpURLConnection {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 20000
        conn.readTimeout = 20000
        if (existing > 0) conn.setRequestProperty("Range", "bytes=$existing-")
        // Publish BEFORE connect() so pause()/cancel() can interrupt a
        // worker blocked in connect or read, not just future reads.
        activeConnection = conn
        try {
            conn.connect()
        } catch (e: Exception) {
            closeConnection(conn)
            throw e
        }
        return conn
    }
}
