package com.tlhf.shared.strategies

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrategiesTest {

    @Test
    fun ironCondorMathIsPerShareTimes100Not100xOff() {
        // Short 105 call @1.00 / long 110 call @0.50, short 95 put @1.00 / long 90 put @0.50.
        // Net credit = $1.00/share = $100. Max loss = $5 width - $1 credit = $4/share = $400.
        val legs = listOf(
            Leg(105.0, isCall = true, isLong = false, premium = 1.00),
            Leg(110.0, isCall = true, isLong = true, premium = 0.50),
            Leg(95.0, isCall = false, isLong = false, premium = 1.00),
            Leg(90.0, isCall = false, isLong = true, premium = 0.50)
        )
        val r = analyze(StrategyType.IRON_CONDOR, legs, stockPrice = 100.0)
        assertTrue(abs(r.maxProfit - 100.0) < 1.0, "maxProfit=${r.maxProfit}")
        assertTrue(abs(r.maxLoss - (-400.0)) < 1.0, "maxLoss=${r.maxLoss}")
        assertTrue(abs(r.marginRequired - 400.0) < 1.0, "margin=${r.marginRequired}")
    }

    @Test
    fun coveredCallCapsProfit() {
        // Own 100 @100, short 105 call @2. Max profit = (5+2)*100 = 700.
        val legs = listOf(Leg(105.0, isCall = true, isLong = false, premium = 2.0))
        val r = analyze(StrategyType.COVERED_CALL, legs, 100.0, stockLegQty = 100, stockCostBasis = 100.0)
        assertTrue(abs(r.maxProfit - 700.0) < 1.0, "maxProfit=${r.maxProfit}")
        assertTrue(r.breakevens.any { abs(it - 98.0) < 0.5 }, "breakevens=${r.breakevens}")
        assertTrue(r.plainEnglishRisks.isNotEmpty())
    }

    @Test
    fun longCallHasUnlimitedProfitBoundedLoss() {
        val legs = listOf(Leg(100.0, isCall = true, isLong = true, premium = 5.0))
        val r = analyze(StrategyType.LEAPS_CALL, legs, 100.0)
        assertEquals(Double.POSITIVE_INFINITY, r.maxProfit)
        assertTrue(abs(r.maxLoss - (-500.0)) < 1.0, "maxLoss=${r.maxLoss}")
    }

    @Test
    fun cashSecuredPutMargin() {
        // Short 95 put @2, cash-secured: margin = 95*100 - 200 = 9300.
        val legs = listOf(Leg(95.0, isCall = false, isLong = false, premium = 2.0))
        val r = analyze(StrategyType.CASH_SECURED_PUT, legs, 100.0)
        assertTrue(abs(r.maxProfit - 200.0) < 1.0, "maxProfit=${r.maxProfit}")
        assertTrue(abs(r.marginRequired - 9300.0) < 1.0, "margin=${r.marginRequired}")
    }

    @Test
    fun bullCallSpreadBounded() {
        // Long 100c @5, short 110c @2. Debit 300. Max profit = (10-3)*100 = 700.
        val legs = listOf(
            Leg(100.0, isCall = true, isLong = true, premium = 5.0),
            Leg(110.0, isCall = true, isLong = false, premium = 2.0)
        )
        val r = analyze(StrategyType.BULL_CALL_SPREAD, legs, 100.0)
        assertTrue(abs(r.maxProfit - 700.0) < 1.0, "maxProfit=${r.maxProfit}")
        assertTrue(abs(r.maxLoss - (-300.0)) < 1.0, "maxLoss=${r.maxLoss}")
        assertTrue(r.breakevens.any { abs(it - 103.0) < 0.5 }, "breakevens=${r.breakevens}")
    }
}

class ChartMathTest {
    private val legs = listOf(Leg(100.0, isCall = true, isLong = true, premium = 5.0))
    private val r = analyze(StrategyType.LEAPS_CALL, legs, stockPrice = 100.0)

    @Test
    fun payoffRangeIsSixtyToOneFortyOfCenter() {
        val (lo, hi) = payoffRange(100.0)
        assertEquals(60.0, lo, 1e-9)
        assertEquals(140.0, hi, 1e-9)
    }

    @Test
    fun samplePayoffCoversRangeAndMatchesAnalyze() {
        val (lo, hi) = payoffRange(100.0)
        val samples = samplePayoff(r.payoffAt, lo, hi)
        assertEquals(121, samples.size)
        assertEquals(lo, samples.first().first, 1e-9)
        assertEquals(hi, samples.last().first, 1e-9)
        // Every sample agrees with the strategy payoff function.
        samples.forEach { (p, v) -> assertEquals(r.payoffAt(p), v, 1e-9) }
    }

    @Test
    fun payoffBoundsNeverCollapse() {
        val (minA, maxA) = payoffBounds(listOf(0.0 to 3.0, 100.0 to 3.0))
        assertTrue(maxA >= 1.0 && minA <= -1.0, "bounds must keep zero visible")
        assertTrue(maxA > minA)
    }

    @Test
    fun payoffBoundsIgnoreNonFinite() {
        val (minA, maxA) = payoffBounds(
            listOf(0.0 to Double.POSITIVE_INFINITY, 1.0 to 5.0, 2.0 to Double.NEGATIVE_INFINITY)
        )
        assertEquals(5.0, maxA, 1e-9)
        assertEquals(-1.0, minA, 1e-9)
    }

    @Test
    fun clampScrubKeepsPriceInRange() {
        val (lo, hi) = payoffRange(100.0)
        assertEquals(lo, clampScrub(-1000.0, lo, hi), 1e-9)
        assertEquals(hi, clampScrub(9999.0, lo, hi), 1e-9)
        assertEquals(100.0, clampScrub(100.0, lo, hi), 1e-9)
    }

    @Test
    fun nearestBreakevenSnapsWithinTolerance() {
        val bes = listOf(95.0, 105.0)
        assertEquals(105.0, nearestBreakeven(bes, 104.0, tolerance = 2.0))
        assertEquals(null, nearestBreakeven(bes, 100.0, tolerance = 2.0))
        assertEquals(null, nearestBreakeven(emptyList(), 100.0, tolerance = 5.0))
    }

    @Test
    fun longCallPayoffIsZeroAtBreakeven() {
        // Long 100 call @ 5 premium breaks even at 105.
        val be = r.breakevens.single()
        assertEquals(105.0, be, 1e-6)
        assertEquals(0.0, r.payoffAt(be), 1e-6)
        assertTrue(r.payoffAt(106.0) > 0)
        assertTrue(r.payoffAt(104.0) < 0)
    }
}
