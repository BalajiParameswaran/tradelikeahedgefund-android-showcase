package com.tlhf.shared.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Yahoo Finance quote client.
 *
 * Known quirk (carried over from the web app): Yahoo sometimes answers 401
 * unless the cookie+crumb handshake is done first. This client tries the
 * chart endpoint directly on query1, then query2; if Yahoo starts 401ing,
 * do the handshake (GET https://fc-query1.finance.yahoo.com/v1/test/getcrumb
 * with cookies) and retry with the crumb param — see the TODO below.
 */
class YahooClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
    }
    private val hosts = listOf("query1.finance.yahoo.com", "query2.finance.yahoo.com")

    suspend fun quote(symbol: String): DataResult<Quote> {
        val t0 = currentTimeMs()
        var lastErr = "no host tried"
        for (host in hosts) {
            try {
                val resp = client.get("https://$host/v8/finance/chart/$symbol") {
                    header("User-Agent", "Mozilla/5.0 (compatible; TLHF/5.0)")
                }
                if (!resp.status.isSuccess()) {
                    lastErr = "HTTP ${resp.status.value} on $host"
                    // TODO: on 401, perform the cookie+crumb handshake and retry
                    // with ?crumb=... before giving up on this host.
                    continue
                }
                val root = json.parseToJsonElement(resp.body<String>()).jsonObject
                val result = root["chart"]?.jsonObject?.get("result")?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?: run { lastErr = "no result for $symbol"; continue }
                val meta = result["meta"]?.jsonObject ?: run { lastErr = "no meta"; continue }
                fun num(key: String) = meta[key]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val price = num("regularMarketPrice")
                if (price <= 0) { lastErr = "bad price"; continue }
                return DataResult.Ok(
                    Quote(
                        symbol = meta["symbol"]?.jsonPrimitive?.content ?: symbol,
                        price = price,
                        change = num("regularMarketChange"),
                        changePct = num("regularMarketChangePercent"),
                        dayHigh = num("regularMarketDayHigh"),
                        dayLow = num("regularMarketDayLow"),
                        volume = meta["regularMarketVolume"]?.jsonPrimitive?.longOrNull ?: 0L,
                        source = "yahoo"
                    ),
                    currentTimeMs() - t0
                )
            } catch (e: Exception) {
                lastErr = e.message ?: "request failed"
            }
        }
        return DataResult.Err("yahoo", lastErr)
    }
}
