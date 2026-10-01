package com.tradelikeahedgefund.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tlhf.shared.ai.DynamicCard
import com.tlhf.shared.ai.LlmEngine
import com.tlhf.shared.ai.aiTopicPrompt
import com.tlhf.shared.ai.parseAiCards
import com.tlhf.shared.learn.LESSONS
import com.tradelikeahedgefund.app.ui.theme.BronzeGold
import com.tradelikeahedgefund.app.ui.theme.BearRed
import com.tradelikeahedgefund.app.ui.theme.Ink
import com.tradelikeahedgefund.app.ui.theme.Muted
import com.tradelikeahedgefund.app.ui.theme.NavySurface

/**
 * AI Topics: dynamic flashcards generated on-device from a ticker + strategy.
 * The model is asked for exactly 5 cards; the reply is parsed with the same
 * JSON-first/paragraph-fallback parser as the web app.
 */
@Composable
fun AiTopicsScreen(
    engine: LlmEngine,
    engineLoaded: Boolean,
    onGoToTutor: () -> Unit
) {
    var ticker by remember { mutableStateOf("AAPL") }
    var strategy by remember { mutableStateOf(LESSONS.first().title) }
    var cards by remember { mutableStateOf<List<DynamicCard>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cacheKey by remember { mutableStateOf("") }

    fun generate() {
        if (busy || !engineLoaded) return
        busy = true
        error = null
        val t = ticker.trim().uppercase().ifEmpty { "AAPL" }
        val prompt = aiTopicPrompt(t, strategy)
        engine.generate(
            prompt,
            onToken = {},
            onDone = { full, err ->
                busy = false
                if (err != null) {
                    error = "Couldn't build cards: $err"
                } else {
                    val parsed = parseAiCards(full.orEmpty(), 5)
                    if (parsed.isEmpty()) error = "The model returned no usable cards — try again."
                    else {
                        cards = parsed
                        cacheKey = "$t::$strategy"
                    }
                }
            }
        )
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("AI Topics", style = MaterialTheme.typography.headlineSmall, color = Ink)
        Text(
            "Dynamic flashcards generated on your phone for any ticker + strategy.",
            color = Muted, style = MaterialTheme.typography.bodySmall
        )
        if (!engineLoaded) {
            Card(colors = CardDefaults.cardColors(containerColor = NavySurface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Load the on-device model in the AI Tutor tab to unlock dynamic topics.", color = Ink)
                    Button(onClick = onGoToTutor) { Text("Go to AI Tutor") }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = ticker,
                    onValueChange = { ticker = it.uppercase().filter { ch -> ch.isLetter() }.take(6) },
                    label = { Text("Ticker") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { generate() }, enabled = !busy) {
                    Text(if (busy) "Building…" else "Generate")
                }
            }
            Text("Strategy", color = BronzeGold, style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LESSONS) { lesson ->
                    val selected = lesson.title == strategy
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) BronzeGold.copy(alpha = 0.25f) else NavySurface
                        ),
                        modifier = Modifier.clickable { strategy = lesson.title }
                    ) {
                        Text(lesson.title, color = Ink, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                    }
                }
            }
            if (error != null) Text(error!!, color = BearRed, style = MaterialTheme.typography.bodySmall)
            if (cards.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(cacheKey, color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { generate() }, enabled = !busy) { Text("Regenerate") }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(cards) { card ->
                        var flipped by remember(card) { mutableStateOf(false) }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = NavySurface),
                            modifier = Modifier.fillMaxWidth().clickable { flipped = !flipped }
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (flipped) "Back — tap to flip" else "Front — tap to flip", color = Muted, style = MaterialTheme.typography.labelSmall)
                                Text(if (flipped) card.back else card.front, color = Ink, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
