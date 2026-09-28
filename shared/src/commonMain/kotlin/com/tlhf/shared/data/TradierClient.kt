package com.tlhf.shared.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock

/**
 * Tradier market-data client (quotes + option chains).
 * A production token against the sandbox host (or vice versa) was the classic
 * failure mode — the host toggle is explicit here, never inferred.
 */
class TradierClient(
    private val token: String,
    sandbox: Boolean
) {
    private val base = if (sandbox) "https://sandbox.tradier.com" else "https://api.tradier.com"
    private val host = if (sandbox) "sandbox" else "production"

    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
    }

    suspend fun quote(symbol: String): DataResult<Quote> {
        val t0 = currentTimeMs()
        return try {
            val resp = client.get("$base/v1/markets/quotes") {
                header("Authorization", "Bearer $token")
                header("Accept", "application/json")
                parameter("symbols", symbol)
                parameter("greeks", "false")
            }
            if (!resp.status.isSuccess()) {
                return DataResult.Err("tradier-$host", "HTTP ${resp.status.value}")
            }
            val root = json.parseToJsonElement(resp.body<String>()).jsonObject
            val q = root["quotes"]?.jsonObject?.get("quote")?.jsonObject
                ?: return DataResult.Err("tradier-$host", "no quote for $symbol")
            fun num(key: String) = q[key]?.jsonPrimitive?.doubleOrNull ?: 0.0
            DataResult.Ok(
                Quote(
                    symbol = q["symbol"]?.jsonPrimitive?.content ?: symbol,
                    price = num("last"),
                    change = num("change"),
                    changePct = num("change_percentage"),
                    dayHigh = num("high"),
                    dayLow = num("low"),
                    volume = q["volume"]?.jsonPrimitive?.longOrNull ?: 0L,
                    source = "tradier-$host"
                ),
                currentTimeMs() - t0
            )
        } catch (e: Exception) {
            DataResult.Err("tradier-$host", e.message ?: "request failed")
        }
    }

    /** [expiration] as YYYY-MM-DD. */
    suspend fun optionChain(symbol: String, expiration: String): DataResult<OptionChain> {
        val t0 = currentTimeMs()
        return try {
            val resp = client.get("$base/v1/markets/options/chains") {
                header("Authorization", "Bearer $token")
                header("Accept", "application/json")
                parameter("symbol", symbol)
                parameter("expiration", expiration)
                parameter("greeks", "true")
            }
            if (!resp.status.isSuccess()) {
                return DataResult.Err("tradier-$host", "HTTP ${resp.status.value}")
            }
            val root = json.parseToJsonElement(resp.body<String>()).jsonObject
            val arr = root["options"]?.jsonObject?.get("option")?.jsonArray
                ?: return DataResult.Ok(OptionChain(symbol), currentTimeMs() - t0)
            val contracts = arr.mapNotNull { el ->
                val o = el.jsonObject
                fun num(key: String) = o[key]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val g = o["greeks"]?.jsonObject
                OptionContract(
                    symbol = o["symbol"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    strike = num("strike"),
                    expiry = expiration,
                    isCall = (o["option_type"]?.jsonPrimitive?.content ?: "") == "call",
                    bid = num("bid"), ask = num("ask"), last = num("last"),
                    volume = o["volume"]?.jsonPrimitive?.longOrNull ?: 0L,
                    openInterest = o["open_interest"]?.jsonPrimitive?.longOrNull ?: 0L,
                    greeks = Greeks(
                        delta = g?.get("delta")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        gamma = g?.get("gamma")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        theta = g?.get("theta")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        vega = g?.get("vega")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        rho = g?.get("rho")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        iv = g?.get("mid_iv")?.jsonPrimitive?.doubleOrNull ?: 0.0
                    )
                )
            }
            DataResult.Ok(OptionChain(symbol, listOf(expiration), contracts), currentTimeMs() - t0)
        } catch (e: Exception) {
            DataResult.Err("tradier-$host", e.message ?: "request failed")
        }
    }
}

/** Milliseconds clock (multiplatform via kotlin.time). */
internal fun currentTimeMs(): Long = Clock.System.now().toEpochMilliseconds()
