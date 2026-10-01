package com.tlhf.shared.portfolio

import com.tlhf.shared.data.Quote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortfolioTest {
    private fun q(symbol: String, price: Double, change: Double = 0.0) =
        Quote(symbol = symbol, price = price, change = change)

    @Test
    fun normalizeSymbolAcceptsTickers() {
        assertEquals("AAPL", normalizeSymbol("aapl"))
        assertEquals("AAPL", normalizeSymbol("  AAPL "))
        assertEquals("BRK.B", normalizeSymbol("brk.b"))
        assertNull(normalizeSymbol(""))
        assertNull(normalizeSymbol("TOOLONGNAME"))
        assertNull(normalizeSymbol("AAPL!"))
    }

    @Test
    fun addWatchDedupesAndSorts() {
        var s = PortfolioState()
        var err: String?
        s = addWatch(s, "msft").also { err = it.second }.first
        assertNull(err)
        val (s2, dupErr) = addWatch(s, "MSFT")
        assertEquals(s, s2)
        assertTrue(dupErr!!.contains("already"))
        val (s3, badErr) = addWatch(s, "!!!")
        assertEquals(s, s3)
        assertTrue(badErr!!.contains("valid ticker"))
        val (s4, _) = addWatch(s, "aapl")
        assertEquals(listOf("AAPL", "MSFT"), s4.watchlist.map { it.symbol })
    }

    @Test
    fun removeWatchDropsEntry() {
        val (s, _) = addWatch(PortfolioState(), "AAPL")
        assertEquals(0, removeWatch(s, "AAPL").watchlist.size)
    }

    @Test
    fun upsertPositionValidates() {
        val (s1, e1) = upsertPosition(PortfolioState(), "aapl", 10.0, 150.0)
        assertNull(e1)
        assertEquals(Position("AAPL", 10.0, 150.0), s1.positions.single())
        // Replace same symbol.
        val (s2, e2) = upsertPosition(s1, "AAPL", 5.0, 160.0)
        assertNull(e2)
        assertEquals(1, s2.positions.size)
        assertEquals(5.0, s2.positions.single().shares)
        // Bad inputs keep state unchanged.
        assertEquals(s1, upsertPosition(s1, "AAPL", 0.0, 150.0).first)
        assertEquals(s1, upsertPosition(s1, "AAPL", 10.0, -1.0).first)
        assertEquals(s1, upsertPosition(s1, "???", 10.0, 150.0).first)
    }

    @Test
    fun portfolioRoundTripsThroughJson() {
        val (s1, _) = addWatch(PortfolioState(), "AAPL")
        val (s2, _) = upsertPosition(s1, "MSFT", 20.0, 300.0)
        val decoded = decodePortfolio(encodePortfolio(s2))
        assertEquals(s2, decoded)
        // Corrupt payload degrades to empty, never crashes.
        assertEquals(PortfolioState(), decodePortfolio("not json{"))
    }

    @Test
    fun valueSkipsMissingQuotes() {
        val positions = listOf(Position("AAPL", 10.0, 150.0), Position("ZZZ", 5.0, 10.0))
        val quotes = mapOf("AAPL" to q("AAPL", 170.0, 2.0))
        assertEquals(1700.0, positionsValue(positions, quotes), 1e-9)
        assertEquals(20.0, positionsDayPnl(positions, quotes), 1e-9)
        assertEquals(listOf("ZZZ"), missingQuotes(positions, quotes))
    }

    @Test
    fun positionUnrealizedMath() {
        val (pnl, pct) = positionUnrealized(Position("AAPL", 10.0, 150.0), 170.0)
        assertEquals(200.0, pnl, 1e-9)
        assertEquals(13.333, pct, 1e-2)
    }
}
