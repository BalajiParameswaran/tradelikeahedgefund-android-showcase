package com.tlhf.shared.pricing

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Black-Scholes option pricing and Greeks. Pure Kotlin, no platform
 * dependencies — shared verbatim by the Android and iOS apps.
 *
 * Conventions:
 * - [t] = time to expiry in YEARS (days / 365).
 * - [r] = continuously-compounded risk-free rate as a decimal (0.05 = 5%).
 * - [sig] = annualized volatility as a decimal (0.20 = 20%).
 * - [theta] is returned ANNUALIZED; divide by 365 for per-day decay.
 */
data class OptionPrice(
    val price: Double,
    val delta: Double,
    val gamma: Double,
    val theta: Double, // annualized; /365 for per-day
    val vega: Double,  // per 1.0 (100%) vol move; /100 for per-1pt move
    val rho: Double    // per 1.0 (100%) rate move; /100 for per-1pt move
)

/** Standard normal CDF (Abramowitz & Stegun 7.1.26, ~1e-7 accuracy). */
fun normCdf(x: Double): Double {
    val t = 1.0 / (1.0 + 0.2316419 * abs(x))
    val d = 0.3989422804014327 * exp(-x * x / 2.0)
    val p = d * t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))))
    return if (x > 0) 1.0 - p else p
}

/** Standard normal PDF. */
fun normPdf(x: Double): Double = exp(-0.5 * x * x) / sqrt(2.0 * PI)

private fun d1(s: Double, k: Double, t: Double, r: Double, sig: Double): Double =
    (ln(s / k) + (r + sig * sig / 2.0) * t) / (sig * sqrt(t))

private fun d2(s: Double, k: Double, t: Double, r: Double, sig: Double): Double =
    d1(s, k, t, r, sig) - sig * sqrt(t)

private fun guard(s: Double, k: Double, t: Double, sig: Double): Boolean =
    s > 0 && k > 0 && t > 0 && sig > 0

/** Black-Scholes theoretical price. */
fun bsPrice(s: Double, k: Double, t: Double, r: Double, sig: Double, isCall: Boolean): Double {
    if (!guard(s, k, t, sig)) return 0.0
    val d1v = d1(s, k, t, r, sig)
    val d2v = d2(s, k, t, r, sig)
    val disc = exp(-r * t)
    return if (isCall) s * normCdf(d1v) - k * disc * normCdf(d2v)
    else k * disc * normCdf(-d2v) - s * normCdf(-d1v)
}

/** Black-Scholes price plus all Greeks. */
fun bsGreeks(s: Double, k: Double, t: Double, r: Double, sig: Double, isCall: Boolean): OptionPrice {
    if (!guard(s, k, t, sig)) return OptionPrice(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    val d1v = d1(s, k, t, r, sig)
    val d2v = d2(s, k, t, r, sig)
    val disc = exp(-r * t)
    val nd1 = normPdf(d1v)
    val price = if (isCall) s * normCdf(d1v) - k * disc * normCdf(d2v)
    else k * disc * normCdf(-d2v) - s * normCdf(-d1v)
    val delta = if (isCall) normCdf(d1v) else normCdf(d1v) - 1.0
    val gamma = nd1 / (s * sig * sqrt(t))
    val theta = -(s * nd1 * sig) / (2.0 * sqrt(t)) -
        (if (isCall) r * k * disc * normCdf(d2v) else -r * k * disc * normCdf(-d2v))
    val vega = s * nd1 * sqrt(t)
    val rho = (if (isCall) k * t * disc * normCdf(d2v) else -k * t * disc * normCdf(-d2v))
    return OptionPrice(price, delta, gamma, theta, vega, rho)
}

/**
 * Implied volatility via bisection. Returns 0.0 when the target price is not
 * bracketed (e.g. below intrinsic value) instead of inventing a number.
 */
fun impliedVol(
    targetPrice: Double,
    s: Double, k: Double, t: Double, r: Double,
    isCall: Boolean,
    lo: Double = 0.001,
    hi: Double = 5.0,
    tol: Double = 1e-6
): Double {
    if (targetPrice <= 0 || !guard(s, k, t, 0.2)) return 0.0
    var low = lo
    var high = hi
    if (bsPrice(s, k, t, r, high, isCall) < targetPrice) return 0.0
    repeat(100) {
        val mid = (low + high) / 2.0
        val p = bsPrice(s, k, t, r, mid, isCall)
        if (abs(p - targetPrice) < tol) return mid
        if (p < targetPrice) low = mid else high = mid
    }
    return (low + high) / 2.0
}
