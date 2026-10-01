package com.tlhf.shared.strategies

import kotlin.math.abs

/**
 * Pure helpers behind the interactive payoff charts on Android and iOS.
 * Keeping the scrub geometry here guarantees both platforms sample the
 * same prices, clamp to the same range, and snap to the same breakevens.
 */

/** Horizontal price window shown around the spot price. */
fun payoffRange(center: Double): Pair<Double, Double> = (center * 0.6) to (center * 1.4)

/** Evenly sample [n]+1 (price, payoff) points across the visible range. */
fun samplePayoff(
    payoffAt: (Double) -> Double,
    lo: Double,
    hi: Double,
    n: Int = 120
): List<Pair<Double, Double>> =
    (0..n).map { i ->
        val p = lo + (hi - lo) * i / n
        p to payoffAt(p)
    }

/** Vertical bounds for the chart, ignoring non-finite payoffs. Never collapses to zero height. */
fun payoffBounds(samples: List<Pair<Double, Double>>): Pair<Double, Double> {
    val finite = samples.map { it.second }.filter { it.isFinite() }
    val maxA = (finite.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    val minA = (finite.minOrNull() ?: -1.0).coerceAtMost(-1.0)
    return minA to maxA
}

/** Clamp a scrubbed price into the visible range. */
fun clampScrub(price: Double, lo: Double, hi: Double): Double = price.coerceIn(lo, hi)

/**
 * Nearest breakeven to [price], or null when none is within [tolerance]
 * (expressed in price units, matching the platform's tap radius).
 */
fun nearestBreakeven(breakevens: List<Double>, price: Double, tolerance: Double): Double? =
    breakevens.minByOrNull { abs(it - price) }
        ?.takeIf { abs(it - price) <= tolerance }
