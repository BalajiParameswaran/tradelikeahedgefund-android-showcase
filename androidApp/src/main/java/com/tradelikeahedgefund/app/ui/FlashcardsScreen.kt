package com.tradelikeahedgefund.app.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tlhf.shared.ai.FlashDeck
import com.tlhf.shared.ai.FlashScores
import com.tlhf.shared.ai.allFlashDecks
import com.tlhf.shared.ai.reviewMistakesPrompt
import com.tlhf.shared.ai.shuffledDeck
import com.tlhf.shared.learn.Flashcard
import com.tlhf.shared.learn.FlashcardKind
import com.tradelikeahedgefund.app.ai.AiPlatform
import com.tradelikeahedgefund.app.ui.theme.BronzeGold
import com.tradelikeahedgefund.app.ui.theme.BullGreen
import com.tradelikeahedgefund.app.ui.theme.BearRed
import com.tradelikeahedgefund.app.ui.theme.Ink
import com.tradelikeahedgefund.app.ui.theme.Muted
import com.tradelikeahedgefund.app.ui.theme.NavySurface

/**
 * Flashcards: the static 54-card Learn system. One mixed deck (30
 * guess-and-correct + 24 info) plus one deck per lesson, shuffled per run,
 * best score stored on-device. "Review my mistakes" hands the missed
 * questions to the AI Tutor tab.
 */
@Composable
fun FlashcardsScreen(context: Context, onReviewMistakes: (prompt: String) -> Unit) {
    val scores = remember { FlashScores(AiPlatform.storage(context)) }
    var deck by remember { mutableStateOf<FlashDeck?>(null) }

    if (deck == null) {
        DeckPicker(scores = scores, onPick = { deck = it })
    } else {
        val d = deck!!
        DeckRunner(
            deck = d,
            onExit = { deck = null },
            onFinish = { correct, total -> scores.record(d.id, correct, total) },
            onReviewMistakes = { missed ->
                onReviewMistakes(reviewMistakesPrompt(d.title, missed))
            }
        )
    }
}

@Composable
private fun DeckPicker(scores: FlashScores, onPick: (FlashDeck) -> Unit) {
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Flashcards", style = MaterialTheme.typography.headlineSmall, color = Ink)
        Text("54 cards · shuffled every run · best score saved on this phone", color = Muted, style = MaterialTheme.typography.bodySmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(allFlashDecks()) { deck ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = NavySurface),
                    modifier = Modifier.fillMaxWidth().clickable { onPick(deck) }
                ) {
                    Row(
                        Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(deck.title, color = BronzeGold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${deck.cards.size} cards · ${deck.guessCount} quiz",
                                color = Muted, style = MaterialTheme.typography.bodySmall
                            )
                        }
                        val best = scores.bestLabel(deck.id)
                        Text(
                            best?.let { "Best $it" } ?: "Not tried",
                            color = if (best != null) BullGreen else Muted,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckRunner(
    deck: FlashDeck,
    onExit: () -> Unit,
    onFinish: (correct: Int, total: Int) -> Unit,
    onReviewMistakes: (missed: List<String>) -> Unit
) {
    var order by remember(deck) { mutableStateOf(shuffledDeck(deck, System.currentTimeMillis())) }
    var index by remember(deck) { mutableIntStateOf(0) }
    var flipped by remember(deck) { mutableStateOf(false) }
    var picked by remember(deck) { mutableStateOf<Int?>(null) }
    var correct by remember(deck) { mutableIntStateOf(0) }
    var answered by remember(deck) { mutableIntStateOf(0) }
    var missed by remember(deck) { mutableStateOf<List<String>>(emptyList()) }
    var done by remember(deck) { mutableStateOf(false) }

    fun next() {
        flipped = false
        picked = null
        if (index + 1 >= order.size) {
            done = true
            onFinish(correct, answered)
        } else index++
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onExit) { Text("← Decks") }
            Text(deck.title, color = Ink, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        if (done) {
            val pct = if (answered > 0) (correct * 100 / answered) else 0
            Card(colors = CardDefaults.cardColors(containerColor = NavySurface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$pct%", color = BronzeGold, style = MaterialTheme.typography.displaySmall)
                    Text("You got $correct of $answered quiz cards right.", color = Ink)
                    Text("Best score saved on this phone.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            order = shuffledDeck(deck, System.currentTimeMillis())
                            index = 0; correct = 0; answered = 0
                            missed = emptyList(); done = false
                        }) { Text("Again") }
                        OutlinedButton(onClick = onExit) { Text("All decks") }
                    }
                    if (missed.isNotEmpty()) {
                        OutlinedButton(onClick = { onReviewMistakes(missed) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Review my mistakes with the tutor →")
                        }
                    }
                }
            }
        } else {
            val card: Flashcard = order[index]
            Text("Card ${index + 1} of ${order.size}", color = Muted, style = MaterialTheme.typography.labelMedium)
            LinearProgressIndicator(
                progress = { (index + 1).toFloat() / order.size },
                modifier = Modifier.fillMaxWidth()
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = NavySurface),
                modifier = Modifier.fillMaxWidth().weight(1f).clickable {
                    if (card.kind == FlashcardKind.INFO) flipped = !flipped
                }
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (card.kind == FlashcardKind.GUESS) "Quiz — pick the answer" else "Info — tap to flip",
                        color = BronzeGold, style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        if (card.kind == FlashcardKind.INFO && flipped) card.back else card.front,
                        color = Ink, style = MaterialTheme.typography.bodyLarge
                    )
                    if (card.kind == FlashcardKind.GUESS && picked == null) {
                        card.choices.forEachIndexed { ci, choice ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = NavySurface),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    picked = ci
                                    answered++
                                    if (ci == card.answerIndex) correct++
                                    else missed = missed + card.front
                                }
                            ) {
                                Text(choice, color = Ink, modifier = Modifier.padding(10.dp))
                            }
                        }
                    }
                    if (card.kind == FlashcardKind.GUESS && picked != null) {
                        card.choices.forEachIndexed { ci, choice ->
                            val bg = when (ci) {
                                card.answerIndex -> BullGreen.copy(alpha = 0.25f)
                                picked -> BearRed.copy(alpha = 0.25f)
                                else -> NavySurface
                            }
                            Card(colors = CardDefaults.cardColors(containerColor = bg), modifier = Modifier.fillMaxWidth()) {
                                Text(choice, color = Ink, modifier = Modifier.padding(10.dp))
                            }
                        }
                        Text(
                            if (picked == card.answerIndex) "Correct." else "Not quite.",
                            color = if (picked == card.answerIndex) BullGreen else BearRed
                        )
                        Text(card.back, color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            val canNext = (card.kind == FlashcardKind.INFO) || picked != null
            Button(onClick = { next() }, enabled = canNext, modifier = Modifier.fillMaxWidth()) {
                Text(if (index + 1 >= order.size) "See score" else "Next card")
            }
        }
    }
}
