package com.tradelikeahedgefund.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tlhf.shared.pricing.bsGreeks
import com.tlhf.shared.strategies.Leg
import com.tlhf.shared.strategies.StrategyResult
import com.tlhf.shared.strategies.StrategyType
import com.tlhf.shared.strategies.analyze
import com.tlhf.shared.strategies.legRiskPlainEnglish
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeScreen() {
    var type by remember { mutableStateOf(StrategyType.COVERED_CALL) }
    var expanded by remember { mutableStateOf(false) }
    var symbol by remember { mutableStateOf("AAPL") }
    var stockPrice by remember { mutableStateOf("100") }
    var dte by remember { mutableStateOf("45") }
    var iv by remember { mutableStateOf("30") }
    var rate by remember { mutableStateOf("5") }
    val legs = remember(type) { mutableStateListOf(*defaultLegs(type).toTypedArray()) }
    var result by remember { mutableStateOf<StrategyResult?>(null) }
    val scroll = rememberScrollState()

    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Trade", style = MaterialTheme.typography.headlineSmall, color = Ink)

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
                StrategyType.entries.forEach { t ->
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
                    PayoffChart(r.payoffAt, stockPrice.toDoubleOrNull() ?: 100.0)
                    Spacer(Modifier.height(4.dp))
                    Text("Plain-English risks", color = BronzeGold, style = MaterialTheme.typography.labelLarge)
                    r.plainEnglishRisks.forEach { Text("• $it", color = Ink, style = MaterialTheme.typography.bodyMedium) }
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
private fun StatRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted)
        Text(value, color = color)
    }
}

@Composable
private fun PayoffChart(payoffAt: (Double) -> Double, center: Double) {
    val lo = center * 0.6
    val hi = center * 1.4
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        val n = 120
        val vals = (0..n).map { i -> val p = lo + (hi - lo) * i / n; p to payoffAt(p) }
        val finite = vals.map { it.second }.filter { it.isFinite() }
        val maxA = (finite.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
        val minA = (finite.minOrNull() ?: -1.0).coerceAtMost(-1.0)
        fun X(p: Double) = (size.width * (p - lo) / (hi - lo)).toFloat()
        fun Y(v: Double) = (size.height * (1 - (v - minA) / (maxA - minA))).toFloat()
        // zero line
        drawLine(BronzeGold.copy(alpha = 0.5f), Offset(0f, Y(0.0)), Offset(size.width, Y(0.0)), strokeWidth = 2f)
        // center line
        drawLine(Muted.copy(alpha = 0.4f), Offset(X(center), 0f), Offset(X(center), size.height), strokeWidth = 1f)
        val path = Path()
        vals.forEachIndexed { i, (p, v) ->
            val vv = v.coerceIn(minA, maxA)
            if (i == 0) path.moveTo(X(p), Y(vv)) else path.lineTo(X(p), Y(vv))
        }
        drawPath(path, BullGreen, style = Stroke(width = 4f))
    }
}
