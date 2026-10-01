package com.tradelikeahedgefund.app.ai

import com.tlhf.shared.ai.MAX_DOWNLOAD_ATTEMPTS
import com.tlhf.shared.ai.downloadBackoffMs
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Resumable model-file downloader — Kotlin port of the web app's
 * OnDeviceAIPlugin download loop (byte-for-byte same behavior):
 *
 * - Range-resume from a `.part` file; 416 restarts from scratch
 * - up to 5 attempts, backoff min(4s * attempt, 15s)
 * - pause / cancel / resume
 * - progress callbacks throttled to ~300ms
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

    @Volatile private var thread: Thread? = null
    @Volatile private var pauseRequested = false
    @Volatile private var cancelRequested = false
    @Volatile private var lastUrl: String? = null

    @Volatile var bytesDownloaded: Long = 0L
        private set
    @Volatile var totalBytes: Long = -1L
        private set

    val isRunning: Boolean get() = thread?.isAlive == true

    /**
     * @return null when the download started, or a human-readable reason why not
     * ("wifi_required", "download already running").
     */
    fun start(
        url: String,
        destFile: File,
        wifiOnly: Boolean,
        isWifiNow: () -> Boolean,
        listener: Listener
    ): String? {
        if (thread?.isAlive == true) return "download already running"
        if (wifiOnly && !isWifiNow()) return "wifi_required"
        pauseRequested = false
        cancelRequested = false
        thread = Thread { downloadLoop(url, destFile, isWifiNow, listener) }.also { it.start() }
        return null
    }

    fun pause() { pauseRequested = true }

    fun resume(
        url: String,
        destFile: File,
        wifiOnly: Boolean,
        isWifiNow: () -> Boolean,
        listener: Listener
    ) {
        pauseRequested = false
        if (thread?.isAlive == true) return
        if (!cancelRequested && destFile.exists()) return // already complete
        val part = File(destFile.absolutePath + ".part")
        if (!part.exists()) return
        cancelRequested = false
        lastUrl?.let { thread = Thread { downloadLoop(it, destFile, isWifiNow, listener) }.also { t -> t.start() } }
            ?: start(url, destFile, wifiOnly, isWifiNow, listener)
    }

    fun cancel() { cancelRequested = true; pauseRequested = false }

    fun delete(destFile: File) {
        cancelRequested = true
        destFile.delete()
        File(destFile.absolutePath + ".part").delete()
        bytesDownloaded = 0
        totalBytes = -1
    }

    private fun downloadLoop(
        url: String,
        destFile: File,
        isWifiNow: () -> Boolean,
        listener: Listener
    ) {
        lastUrl = url
        var attempt = 0
        while (true) {
            attempt++
            if (cancelRequested) { listener.onDone("cancelled"); break }
            try {
                downloadOnce(url, destFile, listener)
                listener.onDone(if (cancelRequested) "cancelled" else null)
                break
            } catch (e: Exception) {
                if (cancelRequested) { listener.onDone("cancelled"); break }
                if (attempt >= MAX_DOWNLOAD_ATTEMPTS) { listener.onDone(e.message ?: "download failed"); break }
                try { Thread.sleep(downloadBackoffMs(attempt)) } catch (_: InterruptedException) {}
            }
        }
        thread = null
    }

    private fun downloadOnce(urlStr: String, destFile: File, listener: Listener) {
        val part = File(destFile.absolutePath + ".part")
        var existing = if (part.exists()) part.length() else 0L
        bytesDownloaded = existing

        var conn = open(urlStr, existing)
        var code = conn.responseCode
        var contentLen = conn.getHeaderFieldLong("Content-Length", -1)
        if (code == 416) {
            conn.disconnect()
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
            conn.disconnect()
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
                        while (pauseRequested && !cancelRequested) Thread.sleep(200)
                        if (cancelRequested) return
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
            conn.disconnect()
        }
        if (destFile.exists()) destFile.delete()
        if (!part.renameTo(destFile)) throw Exception("could not save model file")
        bytesDownloaded = destFile.length()
        totalBytes = bytesDownloaded
        listener.onProgress(bytesDownloaded, totalBytes)
    }

    private fun open(urlStr: String, existing: Long): HttpURLConnection {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 20000
        conn.readTimeout = 20000
        if (existing > 0) conn.setRequestProperty("Range", "bytes=$existing-")
        conn.connect()
        return conn
    }
}
