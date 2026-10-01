package com.tlhf.shared.ai

import com.tlhf.shared.learn.Flashcard
import com.tlhf.shared.learn.FlashcardKind
import com.tlhf.shared.learn.LESSONS
import com.tlhf.shared.learn.buildFlashcards
import kotlin.random.Random

/**
 * The static 54-card Learn system: one mixed deck (30 guess-and-correct +
 * 24 info cards) plus one deck per lesson, with on-device best scores.
 * Card data comes from :shared learn content; decks are just views over it.
 */
data class FlashDeck(val id: String, val title: String, val cards: List<Flashcard>) {
    val guessCount: Int get() = cards.count { it.kind == FlashcardKind.GUESS }
}

fun allFlashDecks(): List<FlashDeck> {
    val all = buildFlashcards()
    val mixed = FlashDeck("mixed", "Mixed Deck", all)
    val perLesson = LESSONS.map { lesson ->
        FlashDeck("lesson_" + lesson.id, lesson.title, all.filter { it.lessonId == lesson.id })
    }
    return listOf(mixed) + perLesson
}

fun shuffledDeck(deck: FlashDeck, seed: Long): List<Flashcard> =
    deck.cards.shuffled(Random(seed))

/**
 * Best score per deck, stored on-device. Key mirrors the web app:
 * `tlhf_deck_best_<deckId>` holding "correct/total". Only a strictly better
 * ratio overwrites the stored best.
 */
class FlashScores(private val storage: KeyValueStorage) {

    fun best(deckId: String): Pair<Int, Int>? {
        val raw = storage.get("tlhf_deck_best_$deckId") ?: return null
        val parts = raw.split("/")
        val correct = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val total = parts.getOrNull(1)?.toIntOrNull() ?: return null
        if (total <= 0 || correct < 0) return null
        return correct to total
    }

    fun bestLabel(deckId: String): String? {
        val (c, t) = best(deckId) ?: return null
        return "$c/$t"
    }

    fun record(deckId: String, correct: Int, total: Int) {
        if (total <= 0) return
        val prev = best(deckId)
        val better = prev == null || correct.toDouble() / total > prev.first.toDouble() / prev.second
        if (better) storage.put("tlhf_deck_best_$deckId", "$correct/$total")
    }
}
