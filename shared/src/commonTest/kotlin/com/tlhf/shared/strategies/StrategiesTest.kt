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
