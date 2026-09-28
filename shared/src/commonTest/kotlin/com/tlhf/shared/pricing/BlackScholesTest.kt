package com.tlhf.shared.pricing

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlackScholesTest {

    // Textbook values: S=100, K=100, T=1, r=5%, vol=20%.
    @Test
    fun atmCallPrice() {
        val p = bsPrice(100.0, 100.0, 1.0, 0.05, 0.20, true)
        assertTrue(abs(p - 10.4506) < 0.01, "call=$p")
    }

    @Test
    fun atmPutPrice() {
        val p = bsPrice(100.0, 100.0, 1.0, 0.05, 0.20, false)
        assertTrue(abs(p - 5.5735) < 0.01, "put=$p")
    }

    @Test
    fun putCallParity() {
        val s = 100.0; val k = 105.0; val t = 0.5; val r = 0.03; val v = 0.25
        val c = bsPrice(s, k, t, r, v, true)
        val p = bsPrice(s, k, t, r, v, false)
        val disc = kotlin.math.exp(-r * t)
        assertTrue(abs((c - p) - (s - k * disc)) < 1e-9, "parity violated")
    }

    @Test
    fun greeksSanity() {
        val g = bsGreeks(100.0, 100.0, 1.0, 0.05, 0.20, true)
        assertTrue(g.delta in 0.0..1.0, "delta=${g.delta}")
        assertTrue(g.gamma > 0, "gamma=${g.gamma}")
        assertTrue(g.theta < 0, "theta=${g.theta}")
        assertTrue(g.vega > 0, "vega=${g.vega}")
        assertTrue(g.rho > 0, "rho=${g.rho}")
        val gp = bsGreeks(100.0, 100.0, 1.0, 0.05, 0.20, false)
        assertTrue(gp.delta in -1.0..0.0, "put delta=${gp.delta}")
    }

    @Test
    fun impliedVolRoundTrip() {
        val s = 100.0; val k = 110.0; val t = 0.75; val r = 0.04; val v = 0.32
        val px = bsPrice(s, k, t, r, v, true)
        val iv = impliedVol(px, s, k, t, r, true)
        assertTrue(abs(iv - v) < 1e-4, "iv=$iv")
    }

    @Test
    fun degenerateInputsReturnZero() {
        assertEquals(0.0, bsPrice(100.0, 100.0, 0.0, 0.05, 0.20, true))
        assertEquals(0.0, bsPrice(100.0, 100.0, 1.0, 0.05, 0.0, true))
    }
}
