package com.tlhf.shared.learn

import com.tlhf.shared.ai.FakeStorage
import com.tlhf.shared.strategies.StrategyType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the unified [LearnProgress] store: levels/marks (the levels
 * system) and resumable deck runs / per-question lesson answers (the
 * persistence work) sharing one blob under one key.
 */
class LearnProgressTest {

    // ---- Levels & progression ----

    @Test
    fun progressionMatchesLessonsOrderAndStrategies() {
        assertEquals(6, LEVEL_PROGRESSION.size)
        assertEquals(LESSONS.map { it.id }, LEVEL_PROGRESSION.map { it.lessonId })
        assertEquals(LESSONS.map { it.title }, LEVEL_PROGRESSION.map { it.title })
        assertEquals((1..6).toList(), LEVEL_PROGRESSION.map { it.level })
        assertEquals(
            listOf(
                StrategyType.COVERED_CALL,
                StrategyType.CASH_SECURED_PUT,
                StrategyType.BULL_CALL_SPREAD,
                StrategyType.BEAR_PUT_SPREAD,
                StrategyType.IRON_CONDOR,
                StrategyType.LEAPS_CALL
            ),
            LEVEL_PROGRESSION.map { it.strategy }
        )
        for (info in LEVEL_PROGRESSION) {
            assertTrue(info.canTradeMessage.isNotBlank())
            assertTrue(info.canTradeMessage.length <= 200, "message too long: ${info.canTradeMessage}")
        }
    }

    @Test
    fun freshProgressIsLevelOneWithOnlyFirstLessonUnlocked() {
        val progress = LearnProgress(FakeStorage())
        assertEquals(1, progress.currentLevel())
        assertTrue(progress.completedLessons().isEmpty())
        assertTrue(progress.isUnlocked("coveredCall"))
        assertFalse(progress.isUnlocked("cashPut"))
        assertFalse(progress.isUnlocked("unknownLesson"))
        assertEquals(LEVEL_PROGRESSION[0], progress.levelInfo(1))
        assertNull(progress.levelInfo(99))
        assertEquals(LEVEL_PROGRESSION[1], progress.nextLevelInfo())
    }

    @Test
    fun levelAdvancesByCompletedPrefixOnly() {
        val progress = LearnProgress(FakeStorage())
        // Completing lesson 3 alone does not advance the prefix.
        progress.markLessonComplete("bullCall")
        assertEquals(1, progress.currentLevel())
        assertFalse(progress.isUnlocked("cashPut"))
        // Completing lesson 1 still leaves a gap at lesson 2.
        progress.markLessonComplete("coveredCall")
        assertEquals(2, progress.currentLevel())
        assertTrue(progress.isUnlocked("cashPut"))
        assertFalse(progress.isUnlocked("bullCall"))
        // Filling the gap unlocks through lesson 3 and advances to level 4.
        progress.markLessonComplete("cashPut")
        assertEquals(4, progress.currentLevel())
        assertTrue(progress.isUnlocked("bullCall"))
        assertTrue(progress.isUnlocked("bearPut"))
        assertFalse(progress.isUnlocked("ironCondor"))
    }

    @Test
    fun completingEverythingCapsAtMaxLevelAndNextIsNull() {
        val progress = LearnProgress(FakeStorage())
        for (info in LEVEL_PROGRESSION) progress.markLessonComplete(info.lessonId)
        assertEquals(LEVEL_PROGRESSION.size, progress.currentLevel())
        assertNull(progress.nextLevelInfo())
        for (info in LEVEL_PROGRESSION) assertTrue(progress.isUnlocked(info.lessonId))
    }

    @Test
    fun unknownLessonIdsAreIgnored() {
        val progress = LearnProgress(FakeStorage())
        progress.markLessonComplete("nope")
        assertTrue(progress.completedLessons().isEmpty())
        assertEquals(1, progress.currentLevel())
    }

    @Test
    fun marksRoundTripAndPersistAcrossInstances() {
        val storage = FakeStorage()
        val first = LearnProgress(storage)
        first.markLessonComplete("coveredCall")
        val key = LearnProgress.cardKey("coveredCall", "Some front?")
        first.recordCardMark(key, correct = true)
        first.recordCardMark("other|abc", correct = false)

        val second = LearnProgress(storage)
        assertEquals(setOf("coveredCall"), second.completedLessons())
        assertEquals(2, second.currentLevel())
        assertEquals(true, second.cardMark(key))
        assertEquals(false, second.cardMark("other|abc"))
        assertNull(second.cardMark("missing"))
        assertEquals(mapOf(key to true, "other|abc" to false), second.marks())
        // Overwriting a mark sticks.
        second.recordCardMark(key, correct = false)
        assertEquals(false, LearnProgress(storage).cardMark(key))
    }

    @Test
    fun cardKeyIsDeterministicAndLessonScoped() {
        assertEquals(
            LearnProgress.cardKey("coveredCall", "front"),
            LearnProgress.cardKey("coveredCall", "front")
        )
        assertTrue(LearnProgress.cardKey("coveredCall", "front").startsWith("coveredCall|"))
        assertFalse(
            LearnProgress.cardKey("coveredCall", "front") ==
                LearnProgress.cardKey("cashPut", "front")
        )
    }

    @Test
    fun corruptStoredJsonDegradesToEmptyProgress() {
        val storage = FakeStorage()
        storage.put(LearnProgress.KEY, "not json {{{")
        val progress = LearnProgress(storage)
        assertTrue(progress.completedLessons().isEmpty())
        assertEquals(1, progress.currentLevel())
        assertTrue(progress.marks().isEmpty())
    }

    // ---- Resumable deck runs & per-question lesson answers ----

    @Test
    fun deckProgressSaveLoadRoundTrip() {
        val store = LearnProgress(FakeStorage())
        assertNull(store.deckProgress("mixed"))
        val progress = DeckProgress(
            deckId = "mixed",
            seed = 42L,
            totalCards = 54,
            index = 7,
            correct = 5,
            answered = 6,
            missedFronts = listOf("What is a covered call?")
        )
        store.saveDeckProgress(progress)
        assertEquals(progress, store.deckProgress("mixed"))
    }

    @Test
    fun saveDeckProgressOverwritesSameDeckOnly() {
        val store = LearnProgress(FakeStorage())
        store.saveDeckProgress(DeckProgress("mixed", seed = 1L, totalCards = 54, index = 1, correct = 1, answered = 1))
        store.saveDeckProgress(DeckProgress("lesson_coveredCall", seed = 2L, totalCards = 9, index = 3, correct = 2, answered = 3))
        store.saveDeckProgress(DeckProgress("mixed", seed = 1L, totalCards = 54, index = 2, correct = 2, answered = 2))
        assertEquals(2, store.deckProgress("mixed")!!.index)
        assertEquals(3, store.deckProgress("lesson_coveredCall")!!.index)
    }

    @Test
    fun clearDeckProgressRemovesOnlyThatDeck() {
        val store = LearnProgress(FakeStorage())
        store.saveDeckProgress(DeckProgress("mixed", seed = 1L, totalCards = 54, index = 1, correct = 0, answered = 1))
        store.saveDeckProgress(DeckProgress("lesson_cashPut", seed = 3L, totalCards = 9, index = 4, correct = 3, answered = 4))
        store.recordLessonAnswer("cashPut", 0, 1, totalQuestions = 5)
        store.clearDeckProgress("mixed")
        assertNull(store.deckProgress("mixed"))
        assertEquals(4, store.deckProgress("lesson_cashPut")!!.index)
        // Lesson progress is untouched by a deck clear.
        assertEquals(mapOf(0 to 1), store.lessonProgress("cashPut")!!.answers)
    }

    @Test
    fun lessonAnswersAccumulateAndFinishExactlyOnLastQuestion() {
        val store = LearnProgress(FakeStorage())
        // Lesson quizzes run over quiz + extraQuiz combined (here 3 + 2 = 5).
        var p = store.recordLessonAnswer("coveredCall", 0, 1, totalQuestions = 5)
        assertEquals(mapOf(0 to 1), p.answers)
        assertFalse(p.finished)

        p = store.recordLessonAnswer("coveredCall", 1, 1, totalQuestions = 5)
        p = store.recordLessonAnswer("coveredCall", 2, 1, totalQuestions = 5)
        p = store.recordLessonAnswer("coveredCall", 4, 0, totalQuestions = 5)
        assertEquals(4, p.answers.size)
        assertFalse(p.finished) // index 3 still unanswered

        p = store.recordLessonAnswer("coveredCall", 3, 1, totalQuestions = 5)
        assertTrue(p.finished)
        assertEquals(p, store.lessonProgress("coveredCall"))
    }

    @Test
    fun reAnsweringAQuestionUpdatesPickWithoutFinishingEarly() {
        val store = LearnProgress(FakeStorage())
        store.recordLessonAnswer("cashPut", 0, 0, totalQuestions = 2)
        val p = store.recordLessonAnswer("cashPut", 0, 2, totalQuestions = 2)
        assertEquals(mapOf(0 to 2), p.answers)
        assertFalse(p.finished)
    }

    @Test
    fun zeroTotalQuestionsNeverFinishes() {
        val store = LearnProgress(FakeStorage())
        val p = store.recordLessonAnswer("cashPut", 0, 0, totalQuestions = 0)
        assertFalse(p.finished)
    }

    @Test
    fun clearLessonProgressRemovesOnlyThatLesson() {
        val store = LearnProgress(FakeStorage())
        store.recordLessonAnswer("coveredCall", 0, 1, totalQuestions = 5)
        store.recordLessonAnswer("cashPut", 0, 0, totalQuestions = 5)
        store.saveDeckProgress(DeckProgress("mixed", seed = 9L, totalCards = 54, index = 2, correct = 1, answered = 2))
        store.clearLessonProgress("coveredCall")
        assertNull(store.lessonProgress("coveredCall"))
        assertEquals(mapOf(0 to 0), store.lessonProgress("cashPut")!!.answers)
        // Deck progress is untouched by a lesson clear.
        assertEquals(2, store.deckProgress("mixed")!!.index)
    }

    @Test
    fun finishedIdSetsReflectBothKinds() {
        val store = LearnProgress(FakeStorage())
        assertTrue(store.finishedDeckIds().isEmpty())
        assertTrue(store.finishedLessonIds().isEmpty())

        store.saveDeckProgress(
            DeckProgress("mixed", seed = 1L, totalCards = 54, index = 54, correct = 50, answered = 54, finished = true)
        )
        store.saveDeckProgress(DeckProgress("lesson_bullCall", seed = 2L, totalCards = 9, index = 4, correct = 3, answered = 4))
        store.recordLessonAnswer("bullCall", 0, 1, totalQuestions = 1)
        store.recordLessonAnswer("bearPut", 0, 0, totalQuestions = 5)

        assertEquals(setOf("mixed"), store.finishedDeckIds())
        assertEquals(setOf("bullCall"), store.finishedLessonIds())
    }

    @Test
    fun corruptJsonLoadsEmptyState() {
        val storage = FakeStorage()
        storage.put(LearnProgress.KEY, "not json {{{")
        val store = LearnProgress(storage)
        assertNull(store.deckProgress("mixed"))
        assertNull(store.lessonProgress("coveredCall"))
        assertTrue(store.finishedDeckIds().isEmpty())
        assertTrue(store.finishedLessonIds().isEmpty())
        // And the store recovers: new progress can be saved over the corrupt blob.
        store.saveDeckProgress(DeckProgress("mixed", seed = 5L, totalCards = 54, index = 1, correct = 1, answered = 1))
        assertEquals(1, store.deckProgress("mixed")!!.index)
    }

    @Test
    fun freshStoreOverSameStorageSeesSavedProgress() {
        // The "resume after app restart" property: state lives in the storage
        // blob, not in the store instance.
        val storage = FakeStorage()
        val first = LearnProgress(storage)
        first.saveDeckProgress(
            DeckProgress("lesson_ironCondor", seed = 77L, totalCards = 9, index = 6, correct = 4, answered = 5, missedFronts = listOf("Max loss?"))
        )
        first.recordLessonAnswer("ironCondor", 0, 0, totalQuestions = 5)
        first.recordLessonAnswer("ironCondor", 1, 1, totalQuestions = 5)

        val resumed = LearnProgress(storage)
        val deck = resumed.deckProgress("lesson_ironCondor")!!
        assertEquals(77L, deck.seed)
        assertEquals(6, deck.index)
        assertEquals(listOf("Max loss?"), deck.missedFronts)
        assertEquals(mapOf(0 to 0, 1 to 1), resumed.lessonProgress("ironCondor")!!.answers)
        assertFalse(resumed.lessonProgress("ironCondor")!!.finished)
    }

    @Test
    fun clearAllRemovesEverything() {
        val storage = FakeStorage()
        val store = LearnProgress(storage)
        store.saveDeckProgress(
            DeckProgress("mixed", seed = 1L, totalCards = 54, index = 54, correct = 54, answered = 54, finished = true)
        )
        store.recordLessonAnswer("leapsCall", 0, 1, totalQuestions = 1)
        store.clearAll()
        assertNull(storage.get(LearnProgress.KEY))
        assertNull(store.deckProgress("mixed"))
        assertNull(store.lessonProgress("leapsCall"))
        assertTrue(store.finishedDeckIds().isEmpty())
        assertTrue(store.finishedLessonIds().isEmpty())
    }

    // ---- Unification: both kinds of state share the one blob ----

    @Test
    fun unifiedStoreKeepsLevelsMarksAndRunsInOneBlob() {
        val storage = FakeStorage()
        val first = LearnProgress(storage)
        first.markLessonComplete("coveredCall")
        first.recordCardMark(LearnProgress.cardKey("coveredCall", "Front?"), correct = true)
        first.saveDeckProgress(DeckProgress("mixed", seed = 3L, totalCards = 54, index = 10, correct = 8, answered = 9))
        first.recordLessonAnswer("cashPut", 0, 1, totalQuestions = 5)

        val second = LearnProgress(storage)
        assertEquals(setOf("coveredCall"), second.completedLessons())
        assertEquals(2, second.currentLevel())
        assertEquals(true, second.cardMark(LearnProgress.cardKey("coveredCall", "Front?")))
        assertEquals(10, second.deckProgress("mixed")!!.index)
        assertEquals(mapOf(0 to 1), second.lessonProgress("cashPut")!!.answers)
    }
}
