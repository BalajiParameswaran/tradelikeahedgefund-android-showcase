package com.tlhf.shared.ai

import com.tlhf.shared.learn.FlashcardKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue


class FlashDecksTest {

    @Test
    fun mixedDeckHas54Cards() {
        val decks = allFlashDecks()
        val mixed = decks.first { it.id == "mixed" }
        assertEquals(54, mixed.cards.size)
        assertEquals(30, mixed.cards.count { it.kind == FlashcardKind.GUESS })
        assertEquals(24, mixed.cards.count { it.kind == FlashcardKind.INFO })
    }

    @Test
    fun oneDeckPerLessonPlusMixed() {
        // 6 lessons ported so far
        assertEquals(7, allFlashDecks().size)
        val per = allFlashDecks().first { it.id == "lesson_ironCondor" }
        assertEquals(9, per.cards.size) // 3 bullets + 1 tip + 5 quiz
    }

    @Test
    fun shuffleIsDeterministicPerSeed() {
        val deck = allFlashDecks().first { it.id == "mixed" }
        val a = shuffledDeck(deck, 42L).map { it.front }
        val b = shuffledDeck(deck, 42L).map { it.front }
        val c = shuffledDeck(deck, 7L).map { it.front }
        assertEquals(a, b)
        assertTrue(a != c)
    }

    @Test
    fun bestScoreRecordedOnlyWhenBetter() {
        val scores = FlashScores(FakeStorage())
        assertNull(scores.best("mixed"))
        scores.record("mixed", 3, 5)
        assertEquals("3/5", scores.bestLabel("mixed"))
        scores.record("mixed", 2, 5) // worse — ignored
        assertEquals("3/5", scores.bestLabel("mixed"))
        scores.record("mixed", 4, 5) // better — stored
        assertEquals("4/5", scores.bestLabel("mixed"))
    }

    @Test
    fun corruptBestScoreReadsNull() {
        val storage = FakeStorage()
        storage.put("tlhf_deck_best_mixed", "junk")
        assertNull(FlashScores(storage).best("mixed"))
    }
}
