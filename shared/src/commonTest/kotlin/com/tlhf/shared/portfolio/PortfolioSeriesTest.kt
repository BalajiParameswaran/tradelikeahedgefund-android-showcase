package com.tlhf.shared.portfolio

import com.tlhf.shared.data.PricePoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortfolioSeriesTest {

    @Test
    fun forwardFillsAcrossUnionOfTimestamps() {
        val positions = listOf(
            Position("AAPL", 10.0, 100.0),
            Position("MSFT", 2.0, 200.0)
        )
        val histories = mapOf(
            "AAPL" to listOf(PricePoint(1_000L, 10.0), PricePoint(3_000L, 30.0)),
            "MSFT" to listOf(PricePoint(2_000L, 100.0), PricePoint(3_000L, 110.0))
        )
        val series = portfolioValueSeries(positions, histories)
        assertEquals(
            listOf(
                // t=1000: only AAPL has a point so far: 10 x 10.
                PricePoint(1_000L, 100.0),
                // t=2000: AAPL forward-filled at 10, MSFT at 100: 100 + 200.
                PricePoint(2_000L, 300.0),
                // t=3000: 10 x 30 + 2 x 110.
                PricePoint(3_000L, 520.0)
            ),
            series
        )
    }

    @Test
    fun positionWithNoHistoryContributesNothing() {
        val positions = listOf(Position("AAPL", 10.0, 100.0), Position("ZZZ", 5.0, 10.0))
        val histories = mapOf("AAPL" to listOf(PricePoint(1_000L, 20.0)))
        assertEquals(listOf(PricePoint(1_000L, 200.0)), portfolioValueSeries(positions, histories))
    }

    @Test
    fun unsortedHistoryIsSortedBeforeFilling() {
        val positions = listOf(Position("AAPL", 1.0, 100.0))
        val histories = mapOf(
            "AAPL" to listOf(PricePoint(3_000L, 30.0), PricePoint(1_000L, 10.0))
        )
        assertEquals(
            listOf(PricePoint(1_000L, 10.0), PricePoint(3_000L, 30.0)),
            portfolioValueSeries(positions, histories)
        )
    }

    @Test
    fun emptyCasesReturnEmpty() {
        assertTrue(portfolioValueSeries(emptyList(), emptyMap()).isEmpty())
        val positions = listOf(Position("AAPL", 10.0, 100.0))
        assertTrue(portfolioValueSeries(positions, emptyMap()).isEmpty())
        assertTrue(portfolioValueSeries(positions, mapOf("AAPL" to emptyList())).isEmpty())
    }

    @Test
    fun missingHistoryReportsNullOrEmptyInPositionOrderDistinct() {
        val positions = listOf(
            Position("AAPL", 1.0, 100.0),
            Position("MSFT", 1.0, 100.0),
            Position("ZZZ", 1.0, 100.0),
            Position("MSFT", 2.0, 100.0)
        )
        val histories = mapOf(
            "AAPL" to listOf(PricePoint(1_000L, 10.0)),
            "MSFT" to emptyList()
            // ZZZ absent entirely.
        )
        assertEquals(listOf("MSFT", "ZZZ"), missingHistory(positions, histories))
        assertEquals(emptyList<String>(), missingHistory(listOf(Position("AAPL", 1.0, 1.0)), histories))
        assertEquals(emptyList<String>(), missingHistory(emptyList(), histories))
    }
}
