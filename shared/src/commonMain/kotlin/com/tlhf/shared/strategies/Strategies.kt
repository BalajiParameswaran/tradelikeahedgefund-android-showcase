package com.tlhf.shared.strategies

import kotlin.math.abs
import kotlin.math.max

/**
 * Strategy payoff analytics. Pure Kotlin — shared by both apps.
 *
 * MONEY CONVENTION (the v3.6 bug class this avoids): every leg's P&L is
 * computed PER SHARE first, summed, and multiplied by [CONTRACT_MULTIPLIER]
 * exactly ONCE at the strategy level. Never multiply per-leg premiums by 100
 * and then again at the total.
 */
const val CONTRACT_MULTIPLIER = 100.0

enum class StrategyType {
    COVERED_CALL,
    CASH_SECURED_PUT,
    BULL_CALL_SPREAD,
    BEAR_PUT_SPREAD,
    IRON_CONDOR,
    IRON_BUTTERFLY,
    LONG_STRADDLE,
    LONG_STRANGLE,
    LEAPS_CALL
}

/** One option leg. [premium] is per-share (e.g. 2.0 = $2.00 = $200/contract). */
data class Leg(
    val strike: Double,
    val isCall: Boolean,
    val isLong: Boolean,
    val premium: Double,
    val quantity: Int = 1
)

/** P&L per share for a single leg at expiry price [price]. */
fun legPayoffPerShare(leg: Leg, price: Double): Double {
    val intrinsic = if (leg.isCall) max(0.0, price - leg.strike)
    else max(0.0, leg.strike - price)
    val perShare = if (leg.isLong) intrinsic - leg.premium else leg.premium - intrinsic
    return perShare * leg.quantity
}

/**
 * Total strategy P&L in dollars at expiry price [price].
 * [stockLegQty] = shares of underlying held (covered call = +100 per contract).
 * [stockCostBasis] = per-share cost of those shares.
 */
fun strategyPayoff(
    legs: List<Leg>,
    price: Double,
    stockLegQty: Int = 0,
    stockCostBasis: Double = 0.0
): Double {
    val options = legs.sumOf { legPayoffPerShare(it, price) } * CONTRACT_MULTIPLIER
    val stock = stockLegQty * (price - stockCostBasis)
    return options + stock
}

data class StrategyResult(
    /** +Infinity means "unlimited" (UI should render the word, not the number). */
    val maxProfit: Double,
    /** Always <= 0. */
    val maxLoss: Double,
    val breakevens: List<Double>,
    /** Collateral/cash required beyond premiums paid. 0 when none. */
    val marginRequired: Double,
    val plainEnglishRisks: List<String>,
    val payoffAt: (Double) -> Double
)

private val UNBOUNDED_PROFIT = setOf(
    StrategyType.LONG_STRADDLE, StrategyType.LONG_STRANGLE, StrategyType.LEAPS_CALL
)

/** Plain-English risk for one leg — tappable leg-level explanation in the UI. */
fun legRiskPlainEnglish(leg: Leg): String {
    val kind = if (leg.isCall) "call" else "put"
    val dir = if (leg.isLong) "bought" else "sold"
    val money = "$${leg.strike} $kind ($dir, $$${leg.premium}/share)"
    return when {
        leg.isLong && leg.isCall ->
            "Long $money: the most you can lose is the premium. You need the stock above \$${leg.strike} plus what you paid to profit."
        leg.isLong && !leg.isCall ->
            "Long $money: the most you can lose is the premium. You need the stock below \$${leg.strike} minus what you paid to profit."
        !leg.isLong && leg.isCall ->
            "Short $money: you keep the premium if the stock stays below \$${leg.strike}. Above it you owe the difference — losses can be large unless this call is covered by stock or a long call."
        else ->
            "Short $money: you keep the premium if the stock stays above \$${leg.strike}. Below it you may be forced to buy the stock at \$${leg.strike} — keep cash ready."
    }
}

private fun strategyNotes(type: StrategyType): List<String> = when (type) {
    StrategyType.COVERED_CALL -> listOf(
        "Profit is capped at the strike you sold — a moonshot leaves money on the table.",
        "The premium only softens a crash; you still own the downside on the shares."
    )
    StrategyType.CASH_SECURED_PUT -> listOf(
        "Only sell puts on stocks you would happily own at the strike.",
        "Best outcome: the put expires worthless and you keep the full premium."
    )
    StrategyType.BULL_CALL_SPREAD -> listOf(
        "Cheaper than buying the call outright, but profit is capped at the short strike.",
        "Max loss is the debit you paid — defined up front."
    )
    StrategyType.BEAR_PUT_SPREAD -> listOf(
        "Cheaper than buying the put outright, but profit is capped at the short strike.",
        "Max loss is the debit you paid — defined up front."
    )
    StrategyType.IRON_CONDOR -> listOf(
        "You are short premium on both sides: you want the stock to sit still.",
        "A strong move either way hits max loss — defined, but real.",
        "Falling implied volatility helps you; rising volatility hurts."
    )
    StrategyType.IRON_BUTTERFLY -> listOf(
        "Like an iron condor with the short strikes pinned together: bigger credit, narrower sweet spot.",
        "Max profit needs the stock pinned exactly at the middle strike at expiry."
    )
    StrategyType.LONG_STRADDLE -> listOf(
        "You need a big move — up or down. Small moves lose to time decay.",
        "Implied volatility crush after events (earnings) can sink this even if you called the direction."
    )
    StrategyType.LONG_STRANGLE -> listOf(
        "Cheaper than a straddle but needs an even bigger move to profit.",
        "Time decay works against you every single day."
    )
    StrategyType.LEAPS_CALL -> listOf(
        "Controls 100 shares of upside for a fraction of the stock's cost; max loss is capped at the premium.",
        "If the stock goes nowhere, time decay quietly eats the premium — at expiry an at-the-money call is worth \$0."
    )
}

/** Collateral required beyond premiums, from the legs. */
private fun marginFor(type: StrategyType, legs: List<Leg>): Double {
    fun width(): Double {
        val calls = legs.filter { it.isCall }.map { it.strike }
        val puts = legs.filter { !it.isCall }.map { it.strike }
        val wCall = if (calls.size >= 2) calls.max() - calls.min() else 0.0
        val wPut = if (puts.size >= 2) puts.max() - puts.min() else 0.0
        return max(wCall, wPut)
    }
    fun netCreditPerShare(): Double =
        legs.sumOf { if (it.isLong) -it.premium * it.quantity else it.premium * it.quantity }
    return when (type) {
        StrategyType.CASH_SECURED_PUT -> {
            val shortPut = legs.firstOrNull { !it.isCall && !it.isLong }
            if (shortPut != null) shortPut.strike * CONTRACT_MULTIPLIER * shortPut.quantity -
                shortPut.premium * CONTRACT_MULTIPLIER * shortPut.quantity else 0.0
        }
        StrategyType.IRON_CONDOR, StrategyType.IRON_BUTTERFLY ->
            (width() * CONTRACT_MULTIPLIER - netCreditPerShare() * CONTRACT_MULTIPLIER).coerceAtLeast(0.0)
        else -> 0.0
    }
}

private fun breakevens(payoffAt: (Double) -> Double, lo: Double, hi: Double): List<Double> {
    val found = mutableListOf<Double>()
    val n = 400
    var prevX = lo
    var prevY = payoffAt(lo)
    for (i in 1..n) {
        val x = lo + (hi - lo) * i / n
        val y = payoffAt(x)
        if (prevY == 0.0) found += prevX
        else if (y == 0.0) found += x
        else if ((prevY < 0) != (y < 0)) {
            var a = prevX; var b = x; var fa = prevY
            repeat(40) {
                val m = (a + b) / 2; val fm = payoffAt(m)
                if ((fa < 0) != (fm < 0)) { b = m } else { a = m; fa = fm }
            }
            found += (a + b) / 2
        }
        prevX = x; prevY = y
    }
    return found.distinct().filter { it > 0.01 }.sorted()
        .fold(mutableListOf<Double>()) { acc, v ->
            if (acc.isEmpty() || abs(acc.last() - v) > 0.05) acc += v; acc
        }
}

/**
 * Full analysis for a strategy. [stockLegQty]/[stockCostBasis] describe an
 * optional stock position (covered call: qty=100 per contract).
 */
fun analyze(
    type: StrategyType,
    legs: List<Leg>,
    stockPrice: Double,
    stockLegQty: Int = 0,
    stockCostBasis: Double = 0.0
): StrategyResult {
    val payoffAt: (Double) -> Double = { p -> strategyPayoff(legs, p, stockLegQty, stockCostBasis) }

    val strikes = legs.map { it.strike }
    val lo = 0.01
    val hi = max((strikes.maxOrNull() ?: stockPrice) * 2.5, stockPrice * 2.5) + 50.0

    // Critical points: expiry grid nodes + every strike nudged both ways.
    val points = mutableSetOf(lo, hi)
    for (s in strikes) {
        points += s
        if (s > 1.0) { points += s - 0.01; points += s + 0.01 }
    }
    var i = lo
    while (i < hi) { points += i; i += hi / 200.0 }

    var minP = Double.POSITIVE_INFINITY
    var maxP = Double.NEGATIVE_INFINITY
    for (p in points) {
        val v = payoffAt(p)
        if (v < minP) minP = v
        if (v > maxP) maxP = v
    }
    // Sanity: max loss can never be positive.
    if (minP > 0) minP = 0.0

    val maxProfit = if (type in UNBOUNDED_PROFIT) Double.POSITIVE_INFINITY else maxP
    val risks = legs.map { legRiskPlainEnglish(it) } + strategyNotes(type)

    return StrategyResult(
        maxProfit = maxProfit,
        maxLoss = minP,
        breakevens = breakevens(payoffAt, lo, hi),
        marginRequired = marginFor(type, legs),
        plainEnglishRisks = risks,
        payoffAt = payoffAt
    )
}
