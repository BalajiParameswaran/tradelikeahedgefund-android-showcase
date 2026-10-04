package com.tradelikeahedgefund.app.ui

import com.tradelikeahedgefund.app.ai.AiPlatform
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tlhf.shared.data.DataResult
import com.tlhf.shared.data.HistoryRange
import com.tlhf.shared.data.PricePoint
import com.tlhf.shared.data.Quote
import com.tlhf.shared.data.TickerDirectory
import com.tlhf.shared.data.YahooClient
import com.tlhf.shared.learn.LESSONS
import com.tlhf.shared.portfolio.PortfolioState
import com.tlhf.shared.portfolio.Position
import com.tlhf.shared.portfolio.WatchEntry
import com.tlhf.shared.portfolio.addWatch
import com.tlhf.shared.portfolio.decodePortfolio
import com.tlhf.shared.portfolio.encodePortfolio
import com.tlhf.shared.portfolio.missingHistory
import com.tlhf.shared.portfolio.missingQuotes
import com.tlhf.shared.portfolio.portfolioValueSeries
import com.tlhf.shared.portfolio.positionUnrealized
import com.tlhf.shared.portfolio.positionsDayPnl
import com.tlhf.shared.portfolio.positionsValue
import com.tlhf.shared.portfolio.removePosition
import com.tlhf.shared.portfolio.removeWatch
import com.tlhf.shared.portfolio.upsertPosition
import com.tradelikeahedgefund.app.ui.theme.BearRed
import com.tradelikeahedgefund.app.ui.theme.BronzeGold
import com.tradelikeahedgefund.app.ui.theme.BullGreen
import com.tradelikeahedgefund.app.ui.theme.Ink
import com.tradelikeahedgefund.app.ui.theme.Muted
import com.tradelikeahedgefund.app.ui.theme.NavySurface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private const val KEY_STATE = "state_json"

private fun money2(v: Double): String {
    val neg = v < 0
    val a = abs(v)
    val int = a.toLong().toString().reversed().chunked(3).joinToString(",").reversed()
    val dec = ((a - a.toLong()) * 100).toInt().toString().padStart(2, '0')
    return (if (neg) "-$" else "$") + int + "." + dec
}

private sealed interface RemovedItem {
    data class Watch(val entry: WatchEntry) : RemovedItem
    data class Pos(val position: Position) : RemovedItem
}

/**
 * Portfolio home: hero value/P&L, positions, watchlist.
 * Quotes come from Yahoo via the shared client — never invented. Symbols with
 * no quote are excluded from totals and the UI says so explicitly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(onTradeSymbol: (String) -> Unit = {}) {
    val context = LocalContext.current
    // Positions carry share counts + cost basis: encrypted at rest (AES-256).
    val storage = remember { AiPlatform.storage(context) }
    var state by remember { mutableStateOf(decodePortfolio(storage.get(KEY_STATE) ?: "")) }
    var quotes by remember { mutableStateOf<Map<String, Quote>>(emptyMap()) }
    var quotesAt by remember { mutableStateOf<Long?>(null) }
    var loading by remember { mutableStateOf(false) }
    var quoteError by remember { mutableStateOf<String?>(null) }
    var removed by remember { mutableStateOf<RemovedItem?>(null) }
    var showAddWatch by remember { mutableStateOf(false) }
    var showAddPos by remember { mutableStateOf(false) }
    var editPos by remember { mutableStateOf<Position?>(null) }
    var sheetPos by remember { mutableStateOf<Position?>(null) }
    var range by remember { mutableStateOf(HistoryRange.ONE_DAY) }
    var histories by remember { mutableStateOf<Map<String, List<PricePoint>>>(emptyMap()) }
    var historyLoading by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val yahoo = remember { YahooClient() }

    fun save(s: PortfolioState) {
        state = s
        storage.put(KEY_STATE, encodePortfolio(s))
    }

    suspend fun refreshQuotes() {
        val symbols = (state.positions.map { it.symbol } + state.watchlist.map { it.symbol }).distinct()
        if (symbols.isEmpty()) return
        loading = true
        quoteError = null
        try {
            val results = withContext(Dispatchers.IO) {
                symbols.map { sym -> async { sym to yahoo.quote(sym) } }.awaitAll()
            }
            val ok = results.mapNotNull { (sym, r) -> (r as? DataResult.Ok)?.let { sym to it.value } }.toMap()
            quotes = quotes + ok
            val failed = results.filter { it.second is DataResult.Err }.map { it.first }
            quoteError = when {
                ok.isEmpty() -> "Couldn't reach Yahoo Finance — check your connection and retry."
                failed.isNotEmpty() -> "No quote for ${failed.joinToString(", ")} — not counted in totals."
                else -> null
            }
            if (ok.isNotEmpty()) quotesAt = System.currentTimeMillis()
        } catch (e: Exception) {
            quoteError = "Couldn't reach Yahoo Finance — check your connection and retry."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refreshQuotes() }
    // Refetch when the symbol set changes.
    LaunchedEffect(state.positions.map { it.symbol } + state.watchlist.map { it.symbol }) {
        refreshQuotes()
    }

    // Price history for the hero chart — position symbols only (watchlist
    // symbols never count toward portfolio value). Only DataResult.Ok
    // histories are kept; a symbol Yahoo gives no history for contributes
    // nothing to the series and is named under the chart.
    LaunchedEffect(range, state.positions.map { it.symbol }) {
        val symbols = state.positions.map { it.symbol }.distinct()
        if (symbols.isEmpty()) {
            histories = emptyMap()
            historyLoading = false
            return@LaunchedEffect
        }
        historyLoading = true
        try {
            val results = withContext(Dispatchers.IO) {
                symbols.map { sym -> async { sym to yahoo.history(sym, range) } }.awaitAll()
            }
            histories = results.mapNotNull { (sym, r) ->
                (r as? DataResult.Ok)?.let { sym to it.value }
            }.toMap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            histories = emptyMap()
        }
        historyLoading = false
    }

    removed?.let { r ->
        LaunchedEffect(r) {
            val label = when (r) {
                is RemovedItem.Watch -> "${r.entry.symbol} removed from watchlist"
                is RemovedItem.Pos -> "${r.position.symbol} position removed"
            }
            when (snackbar.showSnackbar(label, "Undo", duration = SnackbarDuration.Short)) {
                SnackbarResult.ActionPerformed -> {
                    when (r) {
                        is RemovedItem.Watch ->
                            save(state.copy(watchlist = (state.watchlist + r.entry).sortedBy { it.symbol }))
                        is RemovedItem.Pos ->
                            save(state.copy(positions = (state.positions + r.position).sortedBy { it.symbol }))
                    }
                }
                SnackbarResult.Dismissed -> Unit
            }
            removed = null
        }
    }

    val totalValue = positionsValue(state.positions, quotes)
    val dayPnl = positionsDayPnl(state.positions, quotes)
    val missing = missingQuotes(state.positions, quotes)
    val series = remember(state.positions, histories) { portfolioValueSeries(state.positions, histories) }
    val missingHist = remember(state.positions, histories) { missingHistory(state.positions, histories) }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { E2eNotice() }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Portfolio", style = MaterialTheme.typography.headlineSmall, color = Ink, modifier = Modifier.weight(1f))
                    if (loading) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                    IconButton(onClick = { scope.launch { refreshQuotes() } }) {
                        Icon(Icons.Filled.Refresh, "Refresh quotes", tint = BronzeGold)
                    }
                }
            }

            // Hero: total value + day P&L.
            item {
                Card(colors = CardDefaults.cardColors(containerColor = NavySurface), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Total value", color = Muted, style = MaterialTheme.typography.labelLarge)
                        Text(money2(totalValue), color = Ink, style = MaterialTheme.typography.headlineMedium)
                        val dayColor = if (dayPnl >= 0) BullGreen else BearRed
                        val dayPct = if (totalValue - dayPnl > 0) dayPnl / (totalValue - dayPnl) * 100 else 0.0
                        Text(
                            "${signedMoney(dayPnl)} (${signedPct(dayPct)}) today",
                            color = dayColor, style = MaterialTheme.typography.titleMedium
                        )
                        // Robinhood-style value chart over the selected range.
                        // Accent follows the DAY's direction, per spec. Points
                        // come only from Yahoo history — never fabricated.
                        if (series.size >= 2) {
                            Canvas(
                                Modifier.fillMaxWidth().height(120.dp).padding(top = 8.dp, bottom = 4.dp)
                            ) {
                                val closes = series.map { it.close }
                                var min = closes.minOrNull() ?: 0.0
                                var max = closes.maxOrNull() ?: 0.0
                                if (min == max) {
                                    val padAmt = if (min == 0.0) 1.0 else abs(min) * 0.01
                                    min -= padAmt
                                    max += padAmt
                                }
                                val w = size.width
                                val h = size.height
                                fun x(i: Int) = w * i / (series.size - 1)
                                fun y(v: Double) = (h * (1 - (v - min) / (max - min))).toFloat()
                                val fillPath = Path()
                                fillPath.moveTo(0f, h)
                                series.forEachIndexed { i, pt -> fillPath.lineTo(x(i), y(pt.close)) }
                                fillPath.lineTo(w, h)
                                fillPath.close()
                                drawPath(fillPath, dayColor.copy(alpha = 0.15f))
                                val linePath = Path()
                                series.forEachIndexed { i, pt ->
                                    if (i == 0) linePath.moveTo(x(i), y(pt.close))
                                    else linePath.lineTo(x(i), y(pt.close))
                                }
                                drawPath(linePath, dayColor, style = Stroke(width = 2.5.dp.toPx()))
                            }
                            if (missingHist.isNotEmpty()) {
                                Text(
                                    "Chart excludes ${missingHist.joinToString(", ")} — no price history.",
                                    color = Muted, style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else if (state.positions.isNotEmpty()) {
                            Text(
                                "Price history isn't available for this range right now.",
                                color = Muted, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        // Range selector.
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val rangeOptions = listOf(
                                "1D" to HistoryRange.ONE_DAY,
                                "1W" to HistoryRange.ONE_WEEK,
                                "1M" to HistoryRange.ONE_MONTH,
                                "3M" to HistoryRange.THREE_MONTHS,
                                "YTD" to HistoryRange.YEAR_TO_DATE
                            )
                            rangeOptions.forEach { (label, r) ->
                                val selected = r == range
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selected) BronzeGold.copy(alpha = 0.25f) else NavySurface
                                    ),
                                    modifier = Modifier.clickable { range = r }
                                ) {
                                    Text(
                                        label,
                                        color = if (selected) BronzeGold else Muted,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            if (historyLoading) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
                            }
                        }
                        val stamp = quotesAt?.let {
                            SimpleDateFormat("h:mm a", Locale.US).format(Date(it))
                        }
                        Text(
                            when {
                                state.positions.isEmpty() -> "Add positions below to track value here."
                                missing.isNotEmpty() -> "Excludes ${missing.joinToString(", ")} — no quote yet."
                                stamp != null -> "Quotes as of $stamp · Yahoo Finance"
                                else -> "Waiting for quotes…"
                            },
                            color = Muted, style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (quoteError != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = NavySurface)) {
                        Text(
                            quoteError!!, color = BearRed,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // Positions.
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Positions", color = BronzeGold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showAddPos = true }) { Icon(Icons.Filled.Add, "Add position", tint = BronzeGold) }
                }
            }
            if (state.positions.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = NavySurface), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "No positions yet. Add the stock positions behind your options trades to track value and P&L.",
                            color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(state.positions, key = { "pos-${it.symbol}" }) { p ->
                    SwipeableRow(
                        onDelete = { save(removePosition(state, p.symbol)); removed = RemovedItem.Pos(p) },
                        onTrade = { onTradeSymbol(p.symbol) },
                        onEdit = { editPos = p },
                        onTap = { sheetPos = p },
                        onSwipeRight = { editPos = p }
                    ) {
                        val q = quotes[p.symbol]
                        val (uPnl, uPct) = if (q != null) positionUnrealized(p, q.price) else 0.0 to 0.0
                        val uColor = if (uPnl >= 0) BullGreen else BearRed
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.symbol, color = Ink, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${trimNum(p.shares)} sh @ ${money2(p.avgCost)}",
                                    color = Muted, style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    if (q != null) "${money2(q.price)} (${signedPct(q.changePct)})"
                                    else "Quote unavailable",
                                    color = if (q != null) Muted else BearRed,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    if (q != null) money2((q.price) * p.shares) else "—",
                                    color = Ink, style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    if (q != null) "${signedMoney(uPnl)} (${signedPct(uPct)})" else "—",
                                    color = if (q != null) uColor else Muted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // Watchlist.
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Watchlist", color = BronzeGold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showAddWatch = true }) { Icon(Icons.Filled.Add, "Add to watchlist", tint = BronzeGold) }
                }
            }
            if (state.watchlist.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = NavySurface), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Nothing watched yet. Add tickers you want to keep an eye on.",
                            color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(state.watchlist, key = { "watch-${it.symbol}" }) { w ->
                    SwipeableRow(
                        onDelete = { save(removeWatch(state, w.symbol)); removed = RemovedItem.Watch(w) },
                        onTrade = { onTradeSymbol(w.symbol) },
                        onEdit = null,
                        onTap = { onTradeSymbol(w.symbol) },
                        onSwipeRight = { onTradeSymbol(w.symbol) },
                        swipeRightIsAnalyze = true
                    ) {
                        val q = quotes[w.symbol]
                        val cColor = if ((q?.change ?: 0.0) >= 0) BullGreen else BearRed
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(w.symbol, color = Ink, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    if (q != null) money2(q.price) else "—",
                                    color = Ink, style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    if (q != null) "${signedMoney(q.change)} (${signedPct(q.changePct)})"
                                    else "Quote unavailable",
                                    color = if (q != null) cColor else BearRed,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
            item {
                Text(
                    "Quotes by Yahoo Finance, for education only — not investment advice. " +
                        "Your list is stored encrypted on this device only.",
                    color = Muted, style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    // Strategy teaching sheet for a tapped position.
    sheetPos?.let { p ->
        ModalBottomSheet(
            onDismissRequest = { sheetPos = null },
            containerColor = NavySurface
        ) {
            PositionStrategySheet(
                position = p,
                quote = quotes[p.symbol],
                onAnalyze = { sheetPos = null; onTradeSymbol(p.symbol) }
            )
        }
    }

    if (showAddWatch) {
        var sym by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showAddWatch = false },
            title = { Text("Add to watchlist", color = Ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TickerInputField(sym, { sym = it; err = null }, label = "Ticker")
                    if (err != null) Text(err!!, color = BearRed, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val (s, e) = addWatch(state, sym)
                    if (e == null) { save(s); showAddWatch = false } else err = e
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddWatch = false }) { Text("Cancel") } }
        )
    }

    val posDialogFor = editPos
    if (showAddPos || posDialogFor != null) {
        var sym by remember(posDialogFor) { mutableStateOf(posDialogFor?.symbol ?: "") }
        var shares by remember(posDialogFor) { mutableStateOf(posDialogFor?.shares?.let { trimNum(it) } ?: "") }
        var avg by remember(posDialogFor) { mutableStateOf(posDialogFor?.avgCost?.let { trimNum(it) } ?: "") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showAddPos = false; editPos = null },
            title = { Text(if (posDialogFor != null) "Edit position" else "Add position", color = Ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TickerInputField(sym, { sym = it; err = null }, label = "Ticker", enabled = posDialogFor == null)
                    OutlinedTextField(shares, { shares = it; err = null }, label = { Text("Shares") }, singleLine = true)
                    OutlinedTextField(avg, { avg = it; err = null }, label = { Text("Average cost per share") }, singleLine = true)
                    if (err != null) Text(err!!, color = BearRed, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val sh = shares.toDoubleOrNull()
                    val ac = avg.toDoubleOrNull()
                    if (sh == null || ac == null) { err = "Enter numbers for shares and average cost"; return@TextButton }
                    val (s, e) = upsertPosition(state, sym, sh, ac)
                    if (e == null) { save(s); showAddPos = false; editPos = null } else err = e
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddPos = false; editPos = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TickerInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "Ticker",
    enabled: Boolean = true
) {
    val suggestions = remember(value) { TickerDirectory.search(value) }

    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
        if (enabled && suggestions.isNotEmpty() && value.trim().uppercase() != suggestions.firstOrNull()?.symbol) {
            Card(
                colors = CardDefaults.cardColors(containerColor = NavySurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    suggestions.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onValueChange(item.symbol) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.symbol,
                                color = BronzeGold,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = item.name,
                                color = Muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun trimNum(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

private fun signedMoney(v: Double): String = (if (v >= 0) "+" else "") + money2(v)

private fun signedPct(v: Double): String = (if (v >= 0) "+" else "") + "%.2f%%".format(v)

/**
 * Plain-English strategy ideas for one holding, taught with the shared Learn
 * lessons (covered call, cash-secured put, LEAPS). Educational only — the
 * only numbers shown are the user's own position and its live quote.
 */
@Composable
private fun PositionStrategySheet(
    position: Position,
    quote: Quote?,
    onAnalyze: () -> Unit
) {
    val p = position
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "${p.symbol} — strategies for your holding",
            color = Ink, style = MaterialTheme.typography.titleLarge
        )
        val holdingLine = buildString {
            append("${trimNum(p.shares)} shares @ ${money2(p.avgCost)}")
            if (quote != null) {
                val (_, uPct) = positionUnrealized(p, quote.price)
                append(" · now ${money2(quote.price)} (${signedPct(uPct)})")
            } else {
                append(" · quote unavailable right now")
            }
        }
        Text(holdingLine, color = Muted, style = MaterialTheme.typography.bodyMedium)

        for (lessonId in listOf("coveredCall", "cashPut", "leapsCall")) {
            val lesson = LESSONS.firstOrNull { it.id == lessonId } ?: continue
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(lesson.title, color = BronzeGold, style = MaterialTheme.typography.titleMedium)
                Text(lesson.tagline, color = Ink, style = MaterialTheme.typography.bodyMedium)
                lesson.learn.forEach { bullet ->
                    Text("• $bullet", color = Ink, style = MaterialTheme.typography.bodyMedium)
                }
                if (lessonId == "coveredCall") {
                    val contracts = (p.shares / 100).toInt()
                    Text(
                        if (contracts >= 1)
                            "Your ${trimNum(p.shares)} shares can support $contracts covered-call contract${if (contracts == 1) "" else "s"}."
                        else
                            "Covered calls need 100 shares per contract — this position isn't there yet.",
                        color = Muted, style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Save from earnings", color = BronzeGold, style = MaterialTheme.typography.titleMedium)
            Text(
                "Before an earnings report, option premiums usually swell because a big move is " +
                    "expected. Some holders sell a covered call into that — collecting a richer " +
                    "premium in exchange for capping their gains if the stock pops on the news. " +
                    "The catch: if earnings crush it, your shares can be called away at the " +
                    "strike right after the run-up.",
                color = Ink, style = MaterialTheme.typography.bodyMedium
            )
        }

        Button(onClick = onAnalyze, modifier = Modifier.fillMaxWidth()) {
            Text("Analyze ${p.symbol} in Trade")
        }
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeableRow(
    onDelete: () -> Unit,
    onTrade: () -> Unit,
    onEdit: (() -> Unit)?,
    onTap: () -> Unit,
    onSwipeRight: (() -> Unit)? = null,
    swipeRightIsAnalyze: Boolean = false,
    content: @Composable () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val dismiss = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            when (it) {
                // Swipe LEFT = delete (dismisses; undo via Snackbar).
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); true }
                // Swipe RIGHT = edit/analyze: run the action, snap back.
                SwipeToDismissBoxValue.StartToEnd -> { onSwipeRight?.invoke(); false }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )
    // Reset after a programmatic delete so a reused row doesn't stick dismissed.
    // StartToEnd returns false above, so it settles on its own and never
    // reaches this effect with a non-Settled value.
    LaunchedEffect(dismiss.currentValue) {
        if (dismiss.currentValue != SwipeToDismissBoxValue.Settled) dismiss.reset()
    }
    Box {
        SwipeToDismissBox(
            state = dismiss,
            enableDismissFromStartToEnd = onSwipeRight != null,
            enableDismissFromEndToStart = true,
            backgroundContent = {
                when (dismiss.dismissDirection) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(BronzeGold.copy(alpha = 0.25f))
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Icon(
                                if (swipeRightIsAnalyze) Icons.Filled.ShowChart else Icons.Filled.Edit,
                                if (swipeRightIsAnalyze) "Analyze" else "Edit",
                                tint = BronzeGold,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                    else -> {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(BearRed.copy(alpha = 0.25f))
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Icon(
                                Icons.Filled.Delete, "Delete", tint = Color.White,
                                modifier = Modifier.padding(end = 16.dp)
                            )
                        }
                    }
                }
            }
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = NavySurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onTap,
                        onLongClick = { menu = true }
                    )
            ) { content() }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Analyze in Trade") }, onClick = { menu = false; onTrade() })
            if (onEdit != null) DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
            DropdownMenuItem(text = { Text("Remove", color = BearRed) }, onClick = { menu = false; onDelete() })
        }
    }
}
