package com.tradelikeahedgefund.app.ui

import com.tradelikeahedgefund.app.ai.AiPlatform
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tlhf.shared.data.DataResult
import com.tlhf.shared.data.Quote
import com.tlhf.shared.data.TickerDirectory
import com.tlhf.shared.data.YahooClient
import com.tlhf.shared.portfolio.PortfolioState
import com.tlhf.shared.portfolio.Position
import com.tlhf.shared.portfolio.WatchEntry
import com.tlhf.shared.portfolio.addWatch
import com.tlhf.shared.portfolio.decodePortfolio
import com.tlhf.shared.portfolio.encodePortfolio
import com.tlhf.shared.portfolio.missingQuotes
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

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                        Text(
                            (if (dayPnl >= 0) "+" else "") + money2(dayPnl) + " today",
                            color = dayColor, style = MaterialTheme.typography.titleMedium
                        )
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
                        onEdit = { editPos = p }
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
                        onEdit = null
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeableRow(
    onDelete: () -> Unit,
    onTrade: () -> Unit,
    onEdit: (() -> Unit)?,
    content: @Composable () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val dismiss = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
        }
    )
    // Reset after a programmatic delete so a reused row doesn't stick dismissed.
    LaunchedEffect(dismiss.currentValue) {
        if (dismiss.currentValue != SwipeToDismissBoxValue.Settled) dismiss.reset()
    }
    Box {
        SwipeToDismissBox(
            state = dismiss,
            enableDismissFromStartToEnd = false,
            enableDismissFromEndToStart = true,
            backgroundContent = {
                Box(
                    Modifier.fillMaxSize().padding(vertical = 4.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(Icons.Filled.Delete, "Delete", tint = Color.White,
                        modifier = Modifier.padding(end = 16.dp))
                }
            }
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = NavySurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onTrade,
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
