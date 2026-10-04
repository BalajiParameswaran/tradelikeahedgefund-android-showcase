package com.tlhf.shared.strategies

/**
 * Market-outlook routing for the Trade deck: a plain-English view of the
 * market ("going up", "going down", "staying flat", "about to swing")
 * maps to the strategies that fit that view. This is a fixed product
 * mapping, not market data — it never claims a stock will do anything.
 */

enum class Outlook { UP, DOWN, FLAT, SWING }

fun strategiesForOutlook(outlook: Outlook): Set<StrategyType> = when (outlook) {
    Outlook.UP -> setOf(
        StrategyType.BULL_CALL_SPREAD,
        StrategyType.LEAPS_CALL,
        StrategyType.COVERED_CALL,
        StrategyType.CASH_SECURED_PUT
    )
    Outlook.DOWN -> setOf(
        StrategyType.BEAR_PUT_SPREAD
    )
    Outlook.FLAT -> setOf(
        StrategyType.IRON_CONDOR,
        StrategyType.IRON_BUTTERFLY,
        StrategyType.COVERED_CALL,
        StrategyType.CASH_SECURED_PUT
    )
    Outlook.SWING -> setOf(
        StrategyType.LONG_STRADDLE,
        StrategyType.LONG_STRANGLE
    )
}

/** Inverse of [strategiesForOutlook], computed from it so the two never drift. */
fun outlooksForStrategy(type: StrategyType): Set<Outlook> =
    Outlook.entries.filter { type in strategiesForOutlook(it) }.toSet()

/** Strategies that make sense for someone who already holds the stock (or cash to secure a put). */
fun holderStrategies(): Set<StrategyType> = setOf(
    StrategyType.COVERED_CALL,
    StrategyType.CASH_SECURED_PUT,
    StrategyType.BULL_CALL_SPREAD,
    StrategyType.BEAR_PUT_SPREAD,
    StrategyType.LEAPS_CALL
)

/**
 * The Trade deck for a given outlook / holding state, in [StrategyType]
 * declaration order. When filtering by holdings would empty the deck, the
 * outlook-only (or full) list is used instead — the deck is never empty.
 */
fun filterDeck(outlook: Outlook?, hasPosition: Boolean): List<StrategyType> {
    val byOutlook = if (outlook == null) {
        StrategyType.entries.toList()
    } else {
        val wanted = strategiesForOutlook(outlook)
        StrategyType.entries.filter { it in wanted }
    }
    if (!hasPosition) return byOutlook
    val holders = holderStrategies()
    val intersected = byOutlook.filter { it in holders }
    return if (intersected.isEmpty()) byOutlook else intersected
}
