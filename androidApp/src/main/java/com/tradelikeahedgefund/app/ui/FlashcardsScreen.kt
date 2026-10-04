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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tlhf.shared.ai.FlashDeck
import com.tlhf.shared.ai.FlashScores
import com.tlhf.shared.ai.allFlashDecks
import com.tlhf.shared.ai.reviewMistakesPrompt
import com.tlhf.shared.ai.shuffledDeck
import com.tlhf.shared.data.DataResult
import com.tlhf.shared.data.Quote
import com.tlhf.shared.data.YahooClient
import com.tlhf.shared.learn.DeckProgress
import com.tlhf.shared.learn.Flashcard
import com.tlhf.shared.learn.FlashcardKind
import com.tlhf.shared.learn.LEVEL_PROGRESSION
import com.tlhf.shared.learn.LearnProgress
import com.tlhf.shared.learn.buildPositionCards
import com.tlhf.shared.learn.buildStockCards
import com.tlhf.shared.portfolio.Position
import com.tlhf.shared.portfolio.decodePortfolio
import com.tradelikeahedgefund.app.ai.AiPlatform
import com.tradelikeahedgefund.app.ui.theme.BronzeGold
import com.tradelikeahedgefund.app.ui.theme.BullGreen
import com.tradelikeahedgefund.app.ui.theme.BearRed
import com.tradelikeahedgefund.app.ui.theme.Ink
import com.tradelikeahedgefund.app.ui.theme.Muted
import com.tradelikeahedgefund.app.ui.theme.NavySurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Flashcards: the static 54-card Learn system. One mixed deck (30
 * guess-and-correct + 24 info) plus one deck per lesson, shuffled per run,
 * best score stored on-device. "Review my mistakes" hands the missed
 * questions to the AI Tutor tab.
 */
@Composable
fun FlashcardsScreen(context: Context, progress: LearnProgress, onReviewMistakes: (prompt: String) -> Unit) {
    val storage = remember { AiPlatform.storage(context) }
    val scores = remember { FlashScores(storage) }
    var deck by remember { mutableStateOf<FlashDeck?>(null) }

    if (deck == null) {
        DeckPicker(scores = scores, progress = progress, context = context, onPick = { deck = it })
    } else {
        val d = deck!!
        DeckRunner(
            deck = d,
            progress = progress,
            onExit = { deck = null },
            onFinish = { correctCount, total ->
                scores.record(d.id, correctCount, total)
                if (d.id.startsWith("lesson_") && total > 0 && correctCount * 5 >= total * 4) {
                    progress.markLessonComplete(d.id.removePrefix("lesson_"))
                }
            },
            onCardAnswered = { card, correct ->
                progress.recordCardMark(LearnProgress.cardKey(card.lessonId, card.front), correct)
            },
            onReviewMistakes = { missed ->
                onReviewMistakes(reviewMistakesPrompt(d.title, missed))
            }
        )
    }
}

@Composable
private fun DeckPicker(
    scores: FlashScores,
    progress: LearnProgress,
    context: Context,
    onPick: (FlashDeck) -> Unit
) {
    val storage = remember { AiPlatform.storage(context) }
    val portfolio = remember { decodePortfolio(storage.get("state_json") ?: "") }
    val positions: List<Position> = portfolio.positions
    val yahoo = remember { YahooClient() }
    var posQuotes by remember { mutableStateOf<Map<String, Quote>>(emptyMap()) }
    var posLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (positions.isNotEmpty()) {
            posLoading = true
            try {
                val results = withContext(Dispatchers.IO) {
                    positions.map { pos -> async { pos.symbol to yahoo.quote(pos.symbol) } }.awaitAll()
                }
                posQuotes = results.mapNotNull { (sym, r) ->
                    (r as? DataResult.Ok)?.let { sym to it.value }
                }.toMap()
            } catch (_: Exception) {
                posQuotes = emptyMap()
            } finally {
                posLoading = false
            }
        }
    }

    var pickSymbol by remember { mutableStateOf("") }
    var pickLoading by remember { mutableStateOf(false) }
    var pickError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Flashcards", style = MaterialTheme.typography.headlineSmall, color = Ink)
        Text("54 cards · shuffled every run · progress and best score saved on this phone", color = Muted, style = MaterialTheme.typography.bodySmall)

        Text("From your portfolio", color = BronzeGold, style = MaterialTheme.typography.titleMedium)
        if (posLoading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator()
                Text("Loading quotes…", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (positions.isEmpty()) {
            Text(
                "No positions yet. Add positions in the Portfolio tab and this section builds cards from them.",
                color = Muted, style = MaterialTheme.typography.bodySmall
            )
        } else {
            positions.forEach { position ->
                val quote = posQuotes[position.symbol]
                if (quote != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NavySurface),
                        modifier = Modifier.fillMaxWidth().clickable {
                            onPick(
                                FlashDeck(
                                    "position_${position.symbol}",
                                    "${position.symbol} — your position",
                                    buildPositionCards(position, quote)
                                )
                            )
                        }
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "${position.symbol} — your position",
                                color = BronzeGold, style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "${formatShares(position.shares)} shares · cards from your real numbers",
                                color = Muted, style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else if (!posLoading) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NavySurface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "${position.symbol} — quote unavailable; cards need a live price.",
                            color = BearRed, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    Text(
                        "${position.symbol} — loading quote…",
                        color = Muted, style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Pick a stock
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = pickSymbol,
                onValueChange = { pickSymbol = it.uppercase(); pickError = null },
                label = { Text("Ticker") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val sym = pickSymbol.trim().uppercase()
                    if (sym.isEmpty() || pickLoading) return@Button
                    pickLoading = true
                    pickError = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { yahoo.quote(sym) }
                        pickLoading = false
                        when (result) {
                            is DataResult.Ok -> onPick(
                                FlashDeck("stock_$sym", "$sym cards", buildStockCards(sym, result.value))
                            )
                            is DataResult.Err -> pickError =
                                "Couldn't get a price for $sym — check the ticker and your connection."
                        }
                    }
                },
                enabled = !pickLoading
            ) { Text("Build cards") }
            if (pickLoading) CircularProgressIndicator()
        }
        if (pickError != null) {
            Text(pickError!!, color = BearRed, style = MaterialTheme.typography.bodySmall)
        }

        Text("Lesson decks", color = BronzeGold, style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(allFlashDecks()) { deck ->
                val isLessonDeck = deck.id.startsWith("lesson_")
                val lessonId = if (isLessonDeck) deck.id.removePrefix("lesson_") else null
                val unlocked = lessonId == null || progress.isUnlocked(lessonId)
                val level = lessonId?.let { id -> LEVEL_PROGRESSION.firstOrNull { it.lessonId == id }?.level }
                val deckMarks = deck.cards.filter { it.kind == FlashcardKind.GUESS }
                    .mapNotNull { progress.cardMark(LearnProgress.cardKey(it.lessonId, it.front)) }
                Card(
                    colors = CardDefaults.cardColors(containerColor = NavySurface),
                    modifier = if (unlocked) {
                        Modifier.fillMaxWidth().clickable { onPick(deck) }
                    } else {
                        Modifier.fillMaxWidth()
                    }
                ) {
                    Row(
                        Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                            Text(deck.title, color = BronzeGold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${deck.cards.size} cards · ${deck.guessCount} quiz",
                                color = Muted, style = MaterialTheme.typography.bodySmall
                            )
                            val saved = progress.deckProgress(deck.id)
                            when {
                                saved != null && !saved.finished -> Text(
                                    "In progress — card ${saved.index + 1} of ${saved.totalCards} · tap to continue",
                                    color = BronzeGold, style = MaterialTheme.typography.bodySmall
                                )
                                deck.id in progress.finishedDeckIds() -> Text(
                                    "Finished ✓",
                                    color = BullGreen, style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (!unlocked) {
                                Text(
                                    if (level != null) "Locked — Level $level" else "Locked",
                                    color = Muted, style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            val best = scores.bestLabel(deck.id)
                            Text(
                                best?.let { "Best $it" } ?: "Not tried",
                                color = if (best != null) BullGreen else Muted,
                                style = MaterialTheme.typography.labelMedium
                            )
                            if (deckMarks.isNotEmpty()) {
                                Text(
                                    "Marks: ${deckMarks.count { it }}/${deckMarks.size} correct",
                                    color = Muted, style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatShares(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

@Composable
private fun DeckRunner(
    deck: FlashDeck,
    progress: LearnProgress,
    onExit: () -> Unit,
    onFinish: (correct: Int, total: Int) -> Unit,
    onReviewMistakes: (missed: List<String>) -> Unit,
    onCardAnswered: (card: Flashcard, correct: Boolean) -> Unit = { _, _ -> }
) {
    // Built-in decks persist their run so it can resume; position/stock
    // decks are rebuilt from live quotes on every visit, so a saved shuffle
    // would resume against different cards — those always start fresh.
    val persistable = !deck.id.startsWith("position_") && !deck.id.startsWith("stock_")
    // Resume an unfinished run exactly where it stopped: the saved seed
    // reproduces the same shuffle, and the saved position/score continue it.
    // A saved entry whose card count no longer matches the deck (app update
    // changed the content) is ignored rather than resumed into a bad state.
    val saved = remember(deck) {
        if (persistable) {
            progress.deckProgress(deck.id)?.takeIf { !it.finished && it.totalCards == deck.cards.size }
        } else {
            null
        }
    }
    var seed by remember(deck) { mutableStateOf(saved?.seed ?: System.currentTimeMillis()) }
    var order by remember(deck) { mutableStateOf(shuffledDeck(deck, seed)) }
    var index by remember(deck) { mutableIntStateOf(saved?.index ?: 0) }
    var flipped by remember(deck) { mutableStateOf(false) }
    var picked by remember(deck) { mutableStateOf<Int?>(null) }
    var correct by remember(deck) { mutableIntStateOf(saved?.correct ?: 0) }
    var answered by remember(deck) { mutableIntStateOf(saved?.answered ?: 0) }
    var missed by remember(deck) { mutableStateOf(saved?.missedFronts ?: emptyList()) }
    var done by remember(deck) { mutableStateOf(false) }

    fun next() {
        flipped = false
        picked = null
        val isLast = index + 1 >= order.size
        // Persist at every card boundary, so leaving mid-run loses nothing
        // already answered; the current card restarts unanswered on resume.
        if (persistable) {
            progress.saveDeckProgress(
                DeckProgress(
                    deckId = deck.id,
                    seed = seed,
                    totalCards = order.size,
                    index = if (isLast) index else index + 1,
                    correct = correct,
                    answered = answered,
                    missedFronts = missed,
                    finished = isLast
                )
            )
        }
        if (isLast) {
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
                            if (persistable) progress.clearDeckProgress(deck.id)
                            val newSeed = System.currentTimeMillis()
                            seed = newSeed
                            order = shuffledDeck(deck, newSeed)
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
                                    onCardAnswered(card, ci == card.answerIndex)
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
