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

/** History window for [YahooClient.history]: Yahoo `range` + `interval` params. */
enum class HistoryRange(val rangeParam: String, val intervalParam: String) {
    ONE_DAY("1d", "5m"), ONE_WEEK("5d", "30m"), ONE_MONTH("1mo", "1d"),
    THREE_MONTHS("3mo", "1d"), YEAR_TO_DATE("ytd", "1d")
}

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

    /**
     * Historical closes for [symbol] over [range]. Never invents points:
     * only closes actually present in Yahoo's chart payload are returned,
     * and a payload with no usable closes is treated as a failure.
     */
    suspend fun history(symbol: String, range: HistoryRange): DataResult<List<PricePoint>> {
        val t0 = currentTimeMs()
        var lastErr = "no host tried"
        for (host in hosts) {
            try {
                val resp = client.get(
                    "https://$host/v8/finance/chart/$symbol?range=${range.rangeParam}&interval=${range.intervalParam}&includePrePost=false"
                ) {
                    header("User-Agent", "Mozilla/5.0 (compatible; TLHF/5.0)")
                }
                if (!resp.status.isSuccess()) {
                    lastErr = "HTTP ${resp.status.value} on $host"
                    continue
                }
                val points = parseChartHistory(resp.body<String>())
                if (points.isEmpty()) {
                    lastErr = "no history for $symbol on $host"
                    continue
                }
                return DataResult.Ok(points, currentTimeMs() - t0)
            } catch (e: Exception) {
                lastErr = e.message ?: "request failed"
            }
        }
        return DataResult.Err("yahoo", lastErr)
    }
}

/**
 * Parses a Yahoo chart payload into sorted [PricePoint]s. Timestamps in the
 * payload are SECONDS and are converted to milliseconds. Null closes and
 * non-positive closes are skipped. Malformed payloads yield an empty list.
 */
internal fun parseChartHistory(jsonText: String): List<PricePoint> {
    return try {
        val json = Json { ignoreUnknownKeys = true }
        val root = json.parseToJsonElement(jsonText).jsonObject
        val result = root["chart"]?.jsonObject?.get("result")?.jsonArray
            ?.firstOrNull()?.jsonObject ?: return emptyList()
        val timestamps = result["timestamp"]?.jsonArray ?: return emptyList()
        val closes = result["indicators"]?.jsonObject?.get("quote")?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("close")?.jsonArray ?: return emptyList()
        val points = mutableListOf<PricePoint>()
        val n = minOf(timestamps.size, closes.size)
        for (i in 0 until n) {
            val tsSec = timestamps[i].jsonPrimitive.longOrNull ?: continue
            val close = closes[i].jsonPrimitive.doubleOrNull ?: continue
            if (close <= 0.0) continue
            points += PricePoint(epochMs = tsSec * 1000L, close = close)
        }
        points.sortedBy { it.epochMs }
    } catch (e: Exception) {
        emptyList()
    }
}
