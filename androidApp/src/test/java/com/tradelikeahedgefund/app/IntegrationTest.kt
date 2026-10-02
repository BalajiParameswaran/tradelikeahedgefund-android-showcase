package com.tradelikeahedgefund.app

import com.tlhf.shared.data.Quote
import com.tlhf.shared.data.TickerDirectory
import com.tlhf.shared.portfolio.PortfolioState
import com.tlhf.shared.portfolio.Position
import com.tlhf.shared.portfolio.WatchEntry
import com.tlhf.shared.portfolio.decodePortfolio
import com.tlhf.shared.portfolio.encodePortfolio
import com.tlhf.shared.portfolio.positionsDayPnl
import com.tlhf.shared.portfolio.positionsValue
import com.tlhf.shared.pricing.bsGreeks
import com.tlhf.shared.strategies.Leg
import com.tlhf.shared.strategies.StrategyType
import com.tlhf.shared.strategies.analyze
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrationTest {

    @Test
    fun testPortfolioAndTickerIntegration() {
        val ticker = TickerDirectory.search("tsla").firstOrNull()
        assertNotNull(ticker)
        assertEquals("TSLA", ticker?.symbol)

        val initial = PortfolioState(
            positions = listOf(Position("TSLA", 10.0, 200.0)),
            watchlist = listOf(WatchEntry("AAPL", 1700000000000L))
        )

        val json = encodePortfolio(initial)
        val decoded = decodePortfolio(json)
        assertEquals(1, decoded.positions.size)
        assertEquals("TSLA", decoded.positions.first().symbol)

        val quotes = mapOf("TSLA" to Quote(symbol = "TSLA", price = 220.0, change = 5.0, changePct = 2.33))
        val totalVal = positionsValue(decoded.positions, quotes)
        assertEquals(2200.0, totalVal, 0.01)

        val dayPnl = positionsDayPnl(decoded.positions, quotes)
        assertEquals(50.0, dayPnl, 0.01)
    }

    @Test
    fun testStrategyPricingIntegration() {
        val legs = listOf(Leg(strike = 100.0, premium = 5.0, isCall = true, isLong = true))
        val res = analyze(StrategyType.COVERED_CALL, legs, stockPrice = 100.0)
        assertNotNull(res)
        assertTrue(res.maxProfit > 0)

        val greeks = bsGreeks(s = 100.0, k = 100.0, t = 0.1, r = 0.05, sig = 0.25, isCall = true)
        assertTrue(greeks.delta in 0.4..0.6)
    }
}
