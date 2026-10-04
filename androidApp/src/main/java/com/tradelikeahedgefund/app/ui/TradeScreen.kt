package com.tradelikeahedgefund.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tlhf.shared.learn.LESSONS
import com.tlhf.shared.learn.lessonIdForStrategy
import com.tlhf.shared.learn.riskQuizFor
import com.tlhf.shared.learn.riskVerdict
import com.tlhf.shared.portfolio.decodePortfolio
import com.tlhf.shared.pricing.bsGreeks
import com.tlhf.shared.strategies.Leg
import com.tlhf.shared.strategies.Outlook
import com.tlhf.shared.strategies.StrategyResult
import com.tlhf.shared.strategies.StrategyType
import com.tlhf.shared.strategies.analyze
import com.tlhf.shared.strategies.filterDeck
import com.tlhf.shared.strategies.legRiskPlainEnglish
import com.tradelikeahedgefund.app.ai.AiPlatform
import com.tradelikeahedgefund.app.ui.theme.BearRed
import com.tradelikeahedgefund.app.ui.theme.BronzeGold
import com.tradelikeahedgefund.app.ui.theme.BullGreen
import com.tradelikeahedgefund.app.ui.theme.Ink
import com.tradelikeahedgefund.app.ui.theme.Muted
import com.tradelikeahedgefund.app.ui.theme.NavySurface
import kotlin.math.abs

private data class UiLeg(
    var strike: String,
    var premium: String,
    val isCall: Boolean,
    val isLong: Boolean
)

private fun defaultLegs(type: StrategyType): List<UiLeg> = when (type) {
    StrategyType.COVERED_CALL -> listOf(UiLeg("105", "2.0", true, false))
    StrategyType.CASH_SECURED_PUT -> listOf(UiLeg("95", "2.0", false, false))
    StrategyType.BULL_CALL_SPREAD -> listOf(UiLeg("100", "5.0", true, true), UiLeg("110", "2.0", true, false))
    StrategyType.BEAR_PUT_SPREAD -> listOf(UiLeg("100", "5.0", false, true), UiLeg("90", "2.0", false, false))
    StrategyType.IRON_CONDOR -> listOf(
        UiLeg("105", "1.0", true, false), UiLeg("110", "0.5", true, true),
        UiLeg("95", "1.0", false, false), UiLeg("90", "0.5", false, true)
    )
    StrategyType.IRON_BUTTERFLY -> listOf(
        UiLeg("100", "3.0", true, false), UiLeg("100", "3.0", false, false),
        UiLeg("110", "1.0", true, true), UiLeg("90", "1.0", false, true)
    )
    StrategyType.LONG_STRADDLE -> listOf(UiLeg("100", "4.0", true, true), UiLeg("100", "4.0", false, true))
    StrategyType.LONG_STRANGLE -> listOf(UiLeg("105", "2.5", true, true), UiLeg("95", "2.5", false, true))
    StrategyType.LEAPS_CALL -> listOf(UiLeg("100", "8.0", true, true))
}

private fun money(v: Double): String {
    if (v.isInfinite()) return if (v > 0) "Unlimited" else "-Unlimited"
    val r = kotlin.math.round(v).toLong()
    val a = abs(r).toString().reversed().chunked(3).joinToString(",").reversed()
    return (if (r < 0) "-" else "") + "$$a"
}

// Duplicated from PortfolioScreen's trimNum (file-private there): whole
// numbers render without a trailing ".0".
private fun trimNum(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

private fun outlookLabel(outlook: Outlook): String = when (outlook) {
    Outlook.UP -> "Up"
    Outlook.DOWN -> "Down"
    Outlook.FLAT -> "Flat"
    Outlook.SWING -> "Swing"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeScreen(
    onAskTutor: (String) -> Unit = {},
    externalSymbol: String? = null,
    onExternalConsumed: () -> Unit = {}
) {
    var type by remember { mutableStateOf(StrategyType.COVERED_CALL) }
    var expanded by remember { mutableStateOf(false) }
    var symbol by remember { mutableStateOf("AAPL") }

    LaunchedEffect(externalSymbol) {
        if (externalSymbol != null) {
            symbol = externalSymbol
            onExternalConsumed()
        }
    }
    var stockPrice by remember { mutableStateOf("100") }
    var dte by remember { mutableStateOf("45") }
    var iv by remember { mutableStateOf("30") }
    var rate by remember { mutableStateOf("5") }
    val legs = remember(type) { mutableStateListOf(*defaultLegs(type).toTypedArray()) }
    var result by remember { mutableStateOf<StrategyResult?>(null) }
    val scroll = rememberScrollState()

    // --- Workstream 2F: outlook filter + portfolio position context ---
    var outlook by remember { mutableStateOf<Outlook?>(null) }
    var quizOpen by remember { mutableStateOf(false) }
    var quizKey by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val positions = remember(symbol) {
        decodePortfolio(AiPlatform.storage(context).get("state_json") ?: "").positions
    }
    val held = positions.firstOrNull { it.symbol.equals(symbol.trim(), ignoreCase = true) }
    val hasPosition = held != null
    val deck = remember(outlook, hasPosition) { filterDeck(outlook, hasPosition) }

    LaunchedEffect(deck) {
        if (type !in deck) {
            type = deck.first()
            result = null
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        E2eNotice()
        Text("Trade", style = MaterialTheme.typography.headlineSmall, color = Ink)

        // --- Workstream 2F: "What do you think?" outlook selector ---
        Text("What do you think?", color = Muted, style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlookChip(label = "Any", selected = outlook == null) { outlook = null }
            Outlook.entries.forEach { o ->
                OutlookChip(label = outlookLabel(o), selected = outlook == o) { outlook = o }
            }
        }
        when {
            hasPosition -> {
                val heldShares = held?.let { trimNum(it.shares) } ?: "0"
                Text(
                    "You hold $heldShares shares of $symbol — the deck is filtered to strategies that fit a holder" +
                        (if (outlook != null) " and your outlook." else ""),
                    color = Muted, style = MaterialTheme.typography.bodySmall
                )
            }
            outlook != null -> {
                Text(
                    "Showing strategies for a '${outlookLabel(outlook!!)}' outlook.",
                    color = Muted, style = MaterialTheme.typography.bodySmall
                )
            }
        }

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = type.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                onValueChange = {},
                readOnly = true,
                label = { Text("Strategy") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                deck.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(t.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }) },
                        onClick = { type = t; expanded = false; result = null }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(symbol, { symbol = it; result = null }, label = { Text("Symbol") }, modifier = Modifier.weight(1f))
            OutlinedTextField(
                stockPrice, { stockPrice = it; result = null }, label = { Text("Stock price") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(dte, { dte = it; result = null }, label = { Text("DTE (days)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            OutlinedTextField(iv, { iv = it; result = null }, label = { Text("IV %") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            OutlinedTextField(rate, { rate = it; result = null }, label = { Text("Rate %") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
        }

        Text("Legs (tap a leg for its plain-English risk)", color = Muted, style = MaterialTheme.typography.labelLarge)
        legs.forEachIndexed { i, leg ->
            var showRisk by remember { mutableStateOf(false) }
            Card(colors = CardDefaults.cardColors(containerColor = NavySurface)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            (if (leg.isLong) "Long " else "Short ") + (if (leg.isCall) "Call" else "Put"),
                            color = BronzeGold, modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { showRisk = !showRisk }) { Text(if (showRisk) "Hide risk" else "Risk") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            leg.strike, { legs[i] = leg.copy(strike = it); result = null },
                            label = { Text("Strike") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            leg.premium, { legs[i] = leg.copy(premium = it); result = null },
                            label = { Text("Premium") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (showRisk) {
                        val l = Leg(leg.strike.toDoubleOrNull() ?: 0.0, leg.isCall, leg.isLong, leg.premium.toDoubleOrNull() ?: 0.0)
                        Text(legRiskPlainEnglish(l), color = Ink, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Button(
            onClick = {
                val s = stockPrice.toDoubleOrNull() ?: return@Button
                val parsed = legs.mapNotNull {
                    val k = it.strike.toDoubleOrNull() ?: return@mapNotNull null
                    val p = it.premium.toDoubleOrNull() ?: return@mapNotNull null
                    Leg(k, it.isCall, it.isLong, p)
                }
                if (parsed.isEmpty()) return@Button
                val (qty, basis) = if (type == StrategyType.COVERED_CALL) 100 to s else 0 to 0.0
                result = analyze(type, parsed, s, qty, basis)
                quizOpen = false
                quizKey++
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Analyze") }

        result?.let { r ->
            Card(colors = CardDefaults.cardColors(containerColor = NavySurface)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatRow("Max profit", money(r.maxProfit), BullGreen)
                    StatRow("Max loss", money(r.maxLoss), BearRed)
                    StatRow("Breakevens", r.breakevens.joinToString(", ") { "$${"%.2f".format(it)}" }, Ink)
                    if (r.marginRequired > 0) StatRow("Margin required", money(r.marginRequired), Ink)
                    Spacer(Modifier.height(4.dp))
                    PayoffChart(r, stockPrice.toDoubleOrNull() ?: 100.0)
                    Spacer(Modifier.height(4.dp))
                    Text("Plain-English risks", color = BronzeGold, style = MaterialTheme.typography.labelLarge)
                    r.plainEnglishRisks.forEach { Text("• $it", color = Ink, style = MaterialTheme.typography.bodyMedium) }
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            val typeName = type.name.replace('_', ' ').lowercase()
                                .replaceFirstChar { it.uppercase() }
                            val s0 = stockPrice.toDoubleOrNull() ?: 0.0
                            val be = r.breakevens.joinToString(", ") { "$${"%.2f".format(it)}" }
                            onAskTutor(
                                "I'm looking at a $typeName on $symbol with the stock at ${money(s0)}. " +
                                    "Max profit ${money(r.maxProfit)}, max loss ${money(r.maxLoss)}, " +
                                    "breakeven(s) at $be. Explain the key risks of this trade in plain English."
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Ask tutor about this trade") }
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { quizOpen = !quizOpen },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Check your risk understanding") }
                    if (quizOpen) {
                        RiskQuiz(type = type, quizKey = quizKey)
                    }
                }
            }
            // Greeks for the first leg (priced with the shared Black-Scholes core).
            val first = legs.firstOrNull()
            val s = stockPrice.toDoubleOrNull()
            val k = first?.strike?.toDoubleOrNull()
            if (first != null && s != null && k != null) {
                val g = bsGreeks(s, k, (dte.toDoubleOrNull() ?: 45.0) / 365.0, (rate.toDoubleOrNull() ?: 5.0) / 100.0, (iv.toDoubleOrNull() ?: 30.0) / 100.0, first.isCall)
                Card(colors = CardDefaults.cardColors(containerColor = NavySurface)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Greeks — first leg", color = BronzeGold, style = MaterialTheme.typography.labelLarge)
                        Text("Δ ${"%.3f".format(g.delta)}   Γ ${"%.4f".format(g.gamma)}", color = Ink)
                        Text("θ ${"%.2f".format(g.theta / 365)}/day   Vega ${"%.2f".format(g.vega / 100)}/pt", color = Ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun OutlookChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) BronzeGold.copy(alpha = 0.25f) else NavySurface
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            label, color = Ink, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/**
 * Post-analysis risk quiz (workstream 2F): re-answers the mapped lesson's
 * questions for the analyzed strategy. Lives inside the result card, so it
 * disappears whenever a field edit clears the result; a fresh Analyze resets
 * it via [quizKey]. Rendering mirrors the Learn tab's QuizCard.
 */
@Composable
private fun RiskQuiz(type: StrategyType, quizKey: Int) {
    var attempt by remember(type, quizKey) { mutableIntStateOf(0) }
    val questions = remember(type, quizKey) { riskQuizFor(type) }
    var picked by remember(type, quizKey, attempt) { mutableStateOf<Map<Int, Int>>(emptyMap()) }

    if (questions.isEmpty()) {
        Text(
            "No risk questions are available for this strategy.",
            color = Muted, style = MaterialTheme.typography.bodyMedium
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        questions.forEachIndexed { qi, q ->
            Text(
                "Q${qi + 1}. ${q.question}",
                color = Ink, style = MaterialTheme.typography.bodyLarge
            )
            q.choices.forEachIndexed { ci, choice ->
                val answered = picked.containsKey(qi)
                val bg = when {
                    !answered -> NavySurface
                    ci == q.answerIndex -> BullGreen.copy(alpha = 0.25f)
                    ci == picked[qi] -> BearRed.copy(alpha = 0.25f)
                    else -> NavySurface
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = bg),
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (!answered) picked = picked + (qi to ci)
                    }
                ) {
                    Text(choice, color = Ink, modifier = Modifier.padding(10.dp))
                }
            }
            picked[qi]?.let { p ->
                Text(
                    if (p == q.answerIndex) "Correct. " else "Not quite. ",
                    color = if (p == q.answerIndex) BullGreen else BearRed
                )
                Text(q.explanation, color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (picked.size == questions.size) {
            val correct = questions.indices.count { picked[it] == questions[it].answerIndex }
            val passed = riskVerdict(correct, questions.size)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (passed) BullGreen.copy(alpha = 0.25f) else BearRed.copy(alpha = 0.25f)
                )
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (passed) {
                            "You scored $correct/${questions.size} — you understand the risk on this trade."
                        } else {
                            "You scored $correct/${questions.size} — you don't yet understand the risk on this trade."
                        },
                        color = if (passed) BullGreen else BearRed,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!passed) {
                        val lessonTitle = LESSONS.firstOrNull { it.id == lessonIdForStrategy(type) }?.title ?: "the lesson"
                        Text(
                            "Review '$lessonTitle' in the Learn tab and re-read the plain-English risks above before trading this.",
                            color = Muted, style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            TextButton(onClick = { picked = emptyMap(); attempt++ }) { Text("Retake quiz") }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted)
        Text(value, color = color)
    }
}

@Composable
private fun PayoffChart(r: StrategyResult, center: Double) {
    val lo = center * 0.6
    val hi = center * 1.4
    // Scrub position resets whenever a fresh analysis is produced.
    var scrub by remember(r) { mutableStateOf(center) }
    var beInfo by remember { mutableStateOf<Double?>(null) }
    val density = LocalDensity.current
    val pl = r.payoffAt(scrub)

    fun xToPrice(xPx: Float, widthPx: Float): Double =
        (xPx / widthPx * (hi - lo) + lo).coerceIn(lo, hi)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Drag the chart or the slider",
                color = Muted,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                "${money(scrub)} → ${money(pl)}",
                color = if (pl >= 0) BullGreen else BearRed,
                style = MaterialTheme.typography.labelLarge
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .pointerInput(r) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat()
                        val x = { p: Double -> (w * (p - lo) / (hi - lo)).toFloat() }
                        val tappedBe = r.breakevens.minByOrNull { be ->
                            kotlin.math.abs(x(be) - offset.x)
                        }
                        if (tappedBe != null &&
                            kotlin.math.abs(x(tappedBe) - offset.x) < 28.dp.toPx()
                        ) {
                            beInfo = tappedBe
                        } else {
                            scrub = xToPrice(offset.x, w)
                        }
                    }
                }
                .pointerInput(r) {
                    detectDragGestures(
                        onDragStart = { offset -> scrub = xToPrice(offset.x, size.width.toFloat()) },
                        onDrag = { change, _ -> scrub = xToPrice(change.position.x, size.width.toFloat()) }
                    )
                }
        ) {
            val w = size.width
            fun x(p: Double) = (w * (p - lo) / (hi - lo)).toFloat()
            val n = 120
            val vals = (0..n).map { i -> val p = lo + (hi - lo) * i / n; p to r.payoffAt(p) }
            val finite = vals.map { it.second }.filter { it.isFinite() }
            val maxA = (finite.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
            val minA = (finite.minOrNull() ?: -1.0).coerceAtMost(-1.0)
            fun y(v: Double) = (size.height * (1 - (v - minA) / (maxA - minA))).toFloat()

            // Zero P&L line.
            drawLine(BronzeGold.copy(alpha = 0.5f), Offset(0f, y(0.0)), Offset(w, y(0.0)), strokeWidth = 2.dp.toPx())
            // Spot price line.
            drawLine(Muted.copy(alpha = 0.4f), Offset(x(center), 0f), Offset(x(center), size.height), strokeWidth = 1.dp.toPx())
            // Payoff curve.
            val path = Path()
            vals.forEachIndexed { i, (p, v) ->
                val vv = v.coerceIn(minA, maxA)
                if (i == 0) path.moveTo(x(p), y(vv)) else path.lineTo(x(p), y(vv))
            }
            drawPath(path, BullGreen, style = Stroke(width = 3.dp.toPx()))
            // Breakeven markers on the zero line — tap one for an explanation.
            r.breakevens.forEach { be ->
                drawCircle(BronzeGold, radius = 7.dp.toPx(), center = Offset(x(be), y(0.0)))
                drawCircle(Color.White, radius = 3.dp.toPx(), center = Offset(x(be), y(0.0)))
            }
            // Scrubber crosshair + dot on the curve.
            val sx = x(scrub)
            drawLine(
                Color.White.copy(alpha = 0.7f),
                Offset(sx, 0f), Offset(sx, size.height),
                strokeWidth = 1.dp.toPx()
            )
            drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(sx, y(pl)))
            drawCircle(BronzeGold, radius = 6.dp.toPx(), center = Offset(sx, y(pl)), style = Stroke(width = 2.dp.toPx()))

            // Tooltip label near the scrub point.
            val label = "${money(scrub)} · P&L ${money(pl)}"
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = with(density) { 13.sp.toPx() }
                isAntiAlias = true
            }
            val tw = paint.measureText(label)
            val pad = with(density) { 8.dp.toPx() }
            val boxW = tw + pad * 2
            val boxH = with(density) { 28.dp.toPx() }
            val boxX = (sx + with(density) { 10.dp.toPx() }).coerceIn(0f, (w - boxW).coerceAtLeast(0f))
            val boxY = with(density) { 6.dp.toPx() }
            drawRoundRect(
                Color(0xFF101826),
                topLeft = Offset(boxX, boxY),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(with(density) { 6.dp.toPx() })
            )
            drawRoundRect(
                BronzeGold.copy(alpha = 0.6f),
                topLeft = Offset(boxX, boxY),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(with(density) { 6.dp.toPx() }),
                style = Stroke(width = 1.dp.toPx())
            )
            drawContext.canvas.nativeCanvas.drawText(
                label, boxX + pad, boxY + boxH / 2 + paint.textSize * 0.35f, paint
            )
        }
        Slider(
            value = scrub.toFloat(),
            onValueChange = { scrub = it.toDouble().coerceIn(lo, hi) },
            valueRange = lo.toFloat()..hi.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
    }

    beInfo?.let { be ->
        AlertDialog(
            onDismissRequest = { beInfo = null },
            title = { Text("Breakeven ${money(be)}") },
            text = {
                Text(
                    "The stock price at expiry where this trade breaks even — profit is exactly $0. " +
                        "Drag the scrubber across this point on the chart and watch the P&L flip sign."
                )
            },
            confirmButton = {
                TextButton(onClick = { beInfo = null }) { Text("Got it") }
            }
        )
    }
}
