package com.tlhf.shared.strategies

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutlookTest {

    @Test
    fun outlookMappingsAreExact() {
        assertEquals(
            setOf(
                StrategyType.BULL_CALL_SPREAD,
                StrategyType.LEAPS_CALL,
                StrategyType.COVERED_CALL,
                StrategyType.CASH_SECURED_PUT
            ),
            strategiesForOutlook(Outlook.UP)
        )
        assertEquals(setOf(StrategyType.BEAR_PUT_SPREAD), strategiesForOutlook(Outlook.DOWN))
        assertEquals(
            setOf(
                StrategyType.IRON_CONDOR,
                StrategyType.IRON_BUTTERFLY,
                StrategyType.COVERED_CALL,
                StrategyType.CASH_SECURED_PUT
            ),
            strategiesForOutlook(Outlook.FLAT)
        )
        assertEquals(
            setOf(StrategyType.LONG_STRADDLE, StrategyType.LONG_STRANGLE),
            strategiesForOutlook(Outlook.SWING)
        )
    }

    @Test
    fun holderStrategiesAreExact() {
        assertEquals(
            setOf(
                StrategyType.COVERED_CALL,
                StrategyType.CASH_SECURED_PUT,
                StrategyType.BULL_CALL_SPREAD,
                StrategyType.BEAR_PUT_SPREAD,
                StrategyType.LEAPS_CALL
            ),
            holderStrategies()
        )
    }

    @Test
    fun outlooksForStrategyIsConsistentInverse() {
        for (type in StrategyType.entries) {
            for (outlook in Outlook.entries) {
                assertEquals(
                    type in strategiesForOutlook(outlook),
                    outlook in outlooksForStrategy(type),
                    "inverse mismatch for $type / $outlook"
                )
            }
        }
        // Spot checks.
        assertEquals(setOf(Outlook.UP, Outlook.FLAT), outlooksForStrategy(StrategyType.COVERED_CALL))
        assertEquals(setOf(Outlook.DOWN), outlooksForStrategy(StrategyType.BEAR_PUT_SPREAD))
        assertEquals(setOf(Outlook.SWING), outlooksForStrategy(StrategyType.LONG_STRADDLE))
        assertEquals(setOf(Outlook.FLAT), outlooksForStrategy(StrategyType.IRON_BUTTERFLY))
    }

    @Test
    fun filterDeckWithNoFiltersReturnsDeclarationOrder() {
        assertEquals(StrategyType.entries.toList(), filterDeck(null, hasPosition = false))
    }

    @Test
    fun filterDeckByOutlookKeepsDeclarationOrder() {
        assertEquals(
            listOf(
                StrategyType.COVERED_CALL,
                StrategyType.CASH_SECURED_PUT,
                StrategyType.BULL_CALL_SPREAD,
                StrategyType.LEAPS_CALL
            ),
            filterDeck(Outlook.UP, hasPosition = false)
        )
        assertEquals(
            listOf(StrategyType.BEAR_PUT_SPREAD),
            filterDeck(Outlook.DOWN, hasPosition = false)
        )
    }

    @Test
    fun filterDeckWithPositionIntersectsHolders() {
        // DOWN's only strategy is a holder strategy, so the deck is unchanged.
        assertEquals(
            listOf(StrategyType.BEAR_PUT_SPREAD),
            filterDeck(Outlook.DOWN, hasPosition = true)
        )
        // FLAT intersect holders drops the iron strategies.
        assertEquals(
            listOf(StrategyType.COVERED_CALL, StrategyType.CASH_SECURED_PUT),
            filterDeck(Outlook.FLAT, hasPosition = true)
        )
        // No outlook + position = holders in declaration order.
        assertEquals(
            listOf(
                StrategyType.COVERED_CALL,
                StrategyType.CASH_SECURED_PUT,
                StrategyType.BULL_CALL_SPREAD,
                StrategyType.BEAR_PUT_SPREAD,
                StrategyType.LEAPS_CALL
            ),
            filterDeck(null, hasPosition = true)
        )
    }

    @Test
    fun filterDeckFallsBackWhenIntersectionWouldBeEmpty() {
        // SWING strategies are not holder strategies: intersection is empty,
        // so the deck falls back to the outlook-only list rather than emptying.
        val deck = filterDeck(Outlook.SWING, hasPosition = true)
        assertEquals(
            listOf(StrategyType.LONG_STRADDLE, StrategyType.LONG_STRANGLE),
            deck
        )
        assertTrue(deck.isNotEmpty())
    }
}
