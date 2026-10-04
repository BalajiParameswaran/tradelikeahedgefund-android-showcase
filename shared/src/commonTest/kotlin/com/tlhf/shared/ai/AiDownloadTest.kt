package com.tlhf.shared.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiDownloadTest {

    @Test
    fun progressFractionAndLabel() {
        val p = DownloadProgress(500L, 1000L)
        assertEquals(0.5f, p.fraction)
        assertEquals(50, p.percent)
        assertTrue(p.label.contains("/"))
    }

    @Test
    fun progressZeroTotalIsSafe() {
        val p = DownloadProgress(0L, 0L)
        assertEquals(0f, p.fraction)
        assertEquals(0, p.percent)
    }

    @Test
    fun autoUnloadAtThirtyMinutes() {
        assertFalse(shouldAutoUnload(0L, AI_IDLE_UNLOAD_MS - 1))
        assertTrue(shouldAutoUnload(0L, AI_IDLE_UNLOAD_MS))
        assertTrue(shouldAutoUnload(0L, AI_IDLE_UNLOAD_MS + 60_000L))
    }

    @Test
    fun backoffCapsAtFifteenSeconds() {
        assertEquals(4_000L, downloadBackoffMs(1))
        assertEquals(8_000L, downloadBackoffMs(2))
        assertEquals(15_000L, downloadBackoffMs(4))
        assertEquals(15_000L, downloadBackoffMs(10))
    }

    @Test
    fun formatBytesUnits() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1 MB", formatBytes(1_048_576))
        assertTrue(formatBytes(1_597_913_616L).endsWith("GB"))
    }

    @Test
    fun completeOnlyOnExactByteMatch() {
        assertTrue(isCompleteDownload(1000L, 1000L))
        // Truncated (the CDN clean-close bug) and oversized are both incomplete.
        assertFalse(isCompleteDownload(999L, 1000L))
        assertFalse(isCompleteDownload(0L, 1000L))
        assertFalse(isCompleteDownload(1001L, 1000L))
        assertFalse(isCompleteDownload(-1L, 1000L))
    }

    @Test
    fun completeWithUnknownExpectedIsSafe() {
        // No expectation to violate: must not fail downloads whose size
        // neither the catalog nor the response could state.
        assertTrue(isCompleteDownload(1234L, 0L))
        assertTrue(isCompleteDownload(1234L, -1L))
        assertFalse(isCompleteDownload(-1L, 0L))
    }

    @Test
    fun fileStateNoneWhenNothingOnDisk() {
        assertEquals(DownloadFileState.NONE, downloadFileState(destExists = false, partBytes = 0L, expectedBytes = 1000L))
        assertEquals(DownloadFileState.NONE, downloadFileState(destExists = false, partBytes = -5L, expectedBytes = 1000L))
        assertEquals(DownloadFileState.NONE, downloadFileState(destExists = false, partBytes = 0L, expectedBytes = 0L))
    }

    @Test
    fun fileStatePartialForLeftoverPart() {
        assertEquals(DownloadFileState.PARTIAL, downloadFileState(destExists = false, partBytes = 500L, expectedBytes = 1000L))
        // Unknown expected size: any bytes on disk are a resumable partial.
        assertEquals(DownloadFileState.PARTIAL, downloadFileState(destExists = false, partBytes = 500L, expectedBytes = 0L))
        assertEquals(DownloadFileState.PARTIAL, downloadFileState(destExists = false, partBytes = 500L, expectedBytes = -1L))
    }

    @Test
    fun fileStateCompleteForDestAndForFullPart() {
        assertEquals(DownloadFileState.COMPLETE, downloadFileState(destExists = true, partBytes = 0L, expectedBytes = 1000L))
        assertEquals(DownloadFileState.COMPLETE, downloadFileState(destExists = true, partBytes = 0L, expectedBytes = 0L))
        // Died between the last byte and the rename: all bytes are on disk.
        assertEquals(DownloadFileState.COMPLETE, downloadFileState(destExists = false, partBytes = 1000L, expectedBytes = 1000L))
        assertEquals(DownloadFileState.COMPLETE, downloadFileState(destExists = false, partBytes = 1500L, expectedBytes = 1000L))
    }

    @Test
    fun partialProgressFromLeftoverPart() {
        val p = partialProgress(500L, 1000L)
        assertEquals(500L, p.bytesDownloaded)
        assertEquals(1000L, p.totalBytes)
        assertEquals(0.5f, p.fraction)
        assertEquals(50, p.percent)
        assertTrue(p.label.contains("/"))
    }

    @Test
    fun partialProgressUnknownExpectedIsSafe() {
        val p = partialProgress(500L, 0L)
        assertEquals(500L, p.bytesDownloaded)
        assertEquals(0f, p.fraction)
        assertEquals(0, p.percent)
        // Negative leftover sizes are clamped instead of producing nonsense.
        assertEquals(0L, partialProgress(-10L, 1000L).bytesDownloaded)
        assertEquals(0f, partialProgress(0L, -1L).fraction)
    }
}
