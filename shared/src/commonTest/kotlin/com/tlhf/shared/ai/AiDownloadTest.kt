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
}
