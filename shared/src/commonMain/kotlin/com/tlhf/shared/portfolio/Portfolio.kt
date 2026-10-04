package com.tlhf.shared.portfolio

import com.tlhf.shared.data.PricePoint
import com.tlhf.shared.data.Quote
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Portfolio home: positions + watchlist, persisted as JSON by the platforms
 * (SharedPreferences on Android, UserDefaults on iOS).
 *
 * Quotes are NEVER invented: value/P&L math skips symbols with no known
 * quote, and the UI must say so instead of showing a fabricated number.
 */

@Serializable
data class WatchEntry(val symbol: String, val addedAtEpochMs: Long)

@Serializable
data class Position(val symbol: String, val shares: Double, val avgCost: Double)

@Serializable
data class PortfolioState(
    val watchlist: List<WatchEntry> = emptyList(),
    val positions: List<Position> = emptyList()
)

private val portfolioJson = Json { ignoreUnknownKeys = true }

fun encodePortfolio(state: PortfolioState): String = portfolioJson.encodeToString(state)

fun decodePortfolio(raw: String): PortfolioState =
    runCatching { portfolioJson.decodeFromString<PortfolioState>(raw) }
        .getOrElse { PortfolioState() }

fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

private val SYMBOL_RE = Regex("^[A-Z]{1,5}(\\.[A-Z]{1,2})?$")

/** Uppercase-trimmed symbol, or null when it is not a plausible ticker. */
fun normalizeSymbol(raw: String): String? {
    val s = raw.trim().uppercase()
    return if (SYMBOL_RE.matches(s)) s else null
}

/** Returns (newState, errorMessage?) — errorMessage is null on success. */
fun addWatch(state: PortfolioState, rawSymbol: String, atMs: Long = nowMs()): Pair<PortfolioState, String?> {
    val sym = normalizeSymbol(rawSymbol) ?: return state to "Enter a valid ticker, e.g. AAPL"
    if (state.watchlist.any { it.symbol == sym }) return state to "$sym is already on your watchlist"
    return state.copy(watchlist = (state.watchlist + WatchEntry(sym, atMs)).sortedBy { it.symbol }) to null
}

fun removeWatch(state: PortfolioState, symbol: String): PortfolioState =
    state.copy(watchlist = state.watchlist.filterNot { it.symbol == symbol })

/** Insert or replace a position. Returns (newState, errorMessage?) — null on success. */
fun upsertPosition(
    state: PortfolioState,
    rawSymbol: String,
    shares: Double,
    avgCost: Double
): Pair<PortfolioState, String?> {
    val sym = normalizeSymbol(rawSymbol) ?: return state to "Enter a valid ticker, e.g. AAPL"
    if (!(shares > 0) || !shares.isFinite()) return state to "Shares must be greater than zero"
    if (!(avgCost >= 0) || !avgCost.isFinite()) return state to "Average cost can't be negative"
    val rest = state.positions.filterNot { it.symbol == sym }
    return state.copy(positions = (rest + Position(sym, shares, avgCost)).sortedBy { it.symbol }) to null
}

fun removePosition(state: PortfolioState, symbol: String): PortfolioState =
    state.copy(positions = state.positions.filterNot { it.symbol == symbol })

/**
 * Market value of positions. Symbols with no known quote contribute 0 —
 * callers must surface how many symbols are missing quotes.
 */
fun positionsValue(positions: List<Position>, quotes: Map<String, Quote>): Double =
    positions.sumOf { p -> (quotes[p.symbol]?.price ?: 0.0) * p.shares }

/** Aggregate day P&L across positions with known quotes. */
fun positionsDayPnl(positions: List<Position>, quotes: Map<String, Quote>): Double =
    positions.sumOf { p -> (quotes[p.symbol]?.change ?: 0.0) * p.shares }

/** Symbols in [positions] that have no quote yet. */
fun missingQuotes(positions: List<Position>, quotes: Map<String, Quote>): List<String> =
    positions.map { it.symbol }.filter { it !in quotes }

/** (unrealized $, unrealized %) for one position at [price]. */
fun positionUnrealized(p: Position, price: Double): Pair<Double, Double> {
    val pnl = (price - p.avgCost) * p.shares
    val pct = if (p.avgCost > 0 && price.isFinite()) (price / p.avgCost - 1.0) * 100.0 else 0.0
    return pnl to pct
}

/**
 * Total portfolio value over time, forward-filled: at each timestamp in the
 * union of all position symbols' histories, every position contributes
 * shares x its latest close at or before that timestamp. Positions with no
 * history points at all contribute nothing — values are never invented for
 * a symbol Yahoo gave us no history for.
 */
fun portfolioValueSeries(
    positions: List<Position>,
    histories: Map<String, List<PricePoint>>
): List<PricePoint> {
    if (positions.isEmpty()) return emptyList()
    val sortedHistories = positions.map { p ->
        p to (histories[p.symbol]?.sortedBy { it.epochMs } ?: emptyList())
    }
    if (sortedHistories.all { it.second.isEmpty() }) return emptyList()
    val timestamps = sortedHistories
        .flatMap { (_, hist) -> hist.map { it.epochMs } }
        .toSortedSet()
    val series = mutableListOf<PricePoint>()
    for (t in timestamps) {
        var total = 0.0
        var any = false
        for ((pos, hist) in sortedHistories) {
            if (hist.isEmpty()) continue
            val latest = hist.lastOrNull { it.epochMs <= t } ?: continue
            total += pos.shares * latest.close
            any = true
        }
        if (any) series += PricePoint(t, total)
    }
    return series
}

/** Symbols of [positions] whose history is missing or empty, in position order, distinct. */
fun missingHistory(
    positions: List<Position>,
    histories: Map<String, List<PricePoint>>
): List<String> =
    positions.map { it.symbol }
        .distinct()
        .filter { histories[it].isNullOrEmpty() }
