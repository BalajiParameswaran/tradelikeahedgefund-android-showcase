package com.tlhf.shared.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TickerDirectoryTest {

    @Test
    fun testExactSymbolMatch() {
        val results = TickerDirectory.search("AAPL")
        assertTrue(results.isNotEmpty())
        assertEquals("AAPL", results.first().symbol)
        assertEquals("Apple Inc.", results.first().name)
    }

    @Test
    fun testCaseInsensitiveSearch() {
        val results = TickerDirectory.search("nvda")
        assertTrue(results.isNotEmpty())
        assertEquals("NVDA", results.first().symbol)
    }

    @Test
    fun testCompanyNameSearch() {
        val results = TickerDirectory.search("microsoft")
        assertTrue(results.isNotEmpty())
        assertEquals("MSFT", results.first().symbol)
    }

    @Test
    fun testSearchLimit() {
        val results = TickerDirectory.search("a", limit = 3)
        assertTrue(results.size <= 3)
    }

    @Test
    fun testEmptyQueryReturnsEmpty() {
        val results = TickerDirectory.search("   ")
        assertTrue(results.isEmpty())
    }
}
