package com.tlhf.shared.data

import kotlinx.serialization.Serializable

/** Never invent these: clients only ever populate Quote from a real API response. */
@Serializable
data class Quote(
    val symbol: String,
    val price: Double,
    val change: Double = 0.0,
    val changePct: Double = 0.0,
    val dayHigh: Double = 0.0,
    val dayLow: Double = 0.0,
    val volume: Long = 0L,
    val source: String = ""
)

@Serializable
data class Greeks(
    val delta: Double = 0.0,
    val gamma: Double = 0.0,
    val theta: Double = 0.0,
    val vega: Double = 0.0,
    val rho: Double = 0.0,
    val iv: Double = 0.0
)

@Serializable
data class OptionContract(
    val symbol: String,
    val strike: Double,
    val expiry: String, // YYYY-MM-DD
    val isCall: Boolean,
    val bid: Double = 0.0,
    val ask: Double = 0.0,
    val last: Double = 0.0,
    val volume: Long = 0L,
    val openInterest: Long = 0L,
    val greeks: Greeks = Greeks()
)

@Serializable
data class OptionChain(
    val underlying: String,
    val expirations: List<String> = emptyList(),
    val contracts: List<OptionContract> = emptyList()
)

/** Per-source errors stay isolated: a failing source never poisons the others. */
sealed class DataResult<out T> {
    data class Ok<T>(val value: T, val latencyMs: Long) : DataResult<T>()
    data class Err(val source: String, val message: String) : DataResult<Nothing>()
}
