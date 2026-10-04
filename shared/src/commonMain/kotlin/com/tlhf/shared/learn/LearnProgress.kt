package com.tlhf.shared.learn

import com.tlhf.shared.ai.KeyValueStorage
import com.tlhf.shared.strategies.StrategyType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Learn progression: lessons unlock in order, one level per lesson.
 * Completing lessons out of order does not skip ahead — the level is
 * 1 + the length of the completed prefix, so the path stays sequential
 * and every strategy is earned in the order it builds on the last.
 */

data class LevelInfo(
    val level: Int,
    val lessonId: String,
    val title: String,
    val strategy: StrategyType,
    val canTradeMessage: String
)

val LEVEL_PROGRESSION: List<LevelInfo> = listOf(
    LevelInfo(
        level = 1,
        lessonId = "coveredCall",
        title = "Covered Call",
        strategy = StrategyType.COVERED_CALL,
        canTradeMessage = "You've earned Level 1 and can now trade covered calls - you own stock, so selling calls against it is the gentlest way to earn premium."
    ),
    LevelInfo(
        level = 2,
        lessonId = "cashPut",
        title = "Cash-Secured Put",
        strategy = StrategyType.CASH_SECURED_PUT,
        canTradeMessage = "You've earned Level 2 and can now trade cash-secured puts - after covered calls, getting paid to name your buy price is the natural next income trade."
    ),
    LevelInfo(
        level = 3,
        lessonId = "bullCall",
        title = "Bull Call Spread",
        strategy = StrategyType.BULL_CALL_SPREAD,
        canTradeMessage = "You've earned Level 3 and can now trade bull call spreads - defined-risk debit spreads let you bet on upside without paying for a naked call."
    ),
    LevelInfo(
        level = 4,
        lessonId = "bearPut",
        title = "Bear Put Spread",
        strategy = StrategyType.BEAR_PUT_SPREAD,
        canTradeMessage = "You've earned Level 4 and can now trade bear put spreads - the bearish mirror of bull call spreads, with the same defined-risk structure."
    ),
    LevelInfo(
        level = 5,
        lessonId = "ironCondor",
        title = "Iron Condor",
        strategy = StrategyType.IRON_CONDOR,
        canTradeMessage = "You've earned Level 5 and can now trade iron condors - combining both spreads lets you profit when the stock stays calm and range-bound."
    ),
    LevelInfo(
        level = 6,
        lessonId = "leapsCall",
        title = "LEAPS Call",
        strategy = StrategyType.LEAPS_CALL,
        canTradeMessage = "You've earned Level 6 and can now trade LEAPS calls - long-dated calls give you a year or more of upside with risk capped at the premium."
    )
)

/**
 * One in-progress (or finished) flashcard deck run. A run's card order is
 * fully determined by its [seed] (see `shuffledDeck(deck, seed)` in
 * FlashDecks.kt), so persisting the seed plus the position reproduces the
 * run exactly on resume.
 */
@Serializable
data class DeckProgress(
    val deckId: String,
    val seed: Long,
    val totalCards: Int,
    val index: Int,
    val correct: Int,
    val answered: Int,
    val missedFronts: List<String> = emptyList(),
    val finished: Boolean = false
)

/**
 * Quiz answers for one lesson. [answers] maps a question index — over the
 * lesson's `quiz + extraQuiz` lists combined, in that order — to the picked
 * choice index. [finished] flips when every question has a recorded answer;
 * lesson *completion* for level purposes is tracked separately (see
 * [LearnProgress.markLessonComplete]) and requires the answers to be right.
 */
@Serializable
data class LessonProgress(
    val lessonId: String,
    val answers: Map<Int, Int> = emptyMap(),
    val finished: Boolean = false
)

@Serializable
private data class ProgressFile(
    val completed: List<String> = emptyList(),
    val marks: Map<String, Boolean> = emptyMap(),
    val decks: Map<String, DeckProgress> = emptyMap(),
    val lessons: Map<String, LessonProgress> = emptyMap()
)

/**
 * THE single Learn progress store (unified in the whiteboard-revamp merge —
 * two same-key stores were written independently and are one class now).
 *
 * Everything Learn persists lives in one JSON blob under [KEY] on the
 * platform's encrypted [KeyValueStorage] (Android EncryptedSharedPreferences,
 * iOS Keychain — never plaintext on disk), mirroring
 * [com.tlhf.shared.ai.AiChatStore]'s semantics: corrupt payloads degrade to
 * empty progress, never a crash.
 *
 * Responsibilities inside the one store:
 * - Levels: completed lessons, [currentLevel], unlock checks, level info.
 * - Per-card marks: the latest correct/incorrect result per flashcard.
 * - Resumable deck runs: [DeckProgress] per deck (seed + position + score),
 *   so a half-finished deck continues exactly where it stopped.
 * - Per-question lesson answers: [LessonProgress], so a half-answered
 *   lesson quiz restores its picks.
 */
class LearnProgress(private val storage: KeyValueStorage) {

    companion object {
        const val KEY = "tlhf_learn_progress_v1"

        fun cardKey(lessonId: String, front: String): String =
            "$lessonId|${front.hashCode().toString(16)}"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun load(): ProgressFile {
        return try {
            val raw = storage.get(KEY) ?: return ProgressFile()
            json.decodeFromString(ProgressFile.serializer(), raw)
        } catch (_: Exception) {
            ProgressFile()
        }
    }

    private fun save(file: ProgressFile) {
        storage.put(KEY, json.encodeToString(ProgressFile.serializer(), file))
    }

    // ---- Levels & lesson completion ----

    fun completedLessons(): Set<String> = load().completed.toSet()

    /** Only progression lesson ids stick; unknown ids are ignored. */
    fun markLessonComplete(lessonId: String) {
        if (LEVEL_PROGRESSION.none { it.lessonId == lessonId }) return
        val file = load()
        if (lessonId in file.completed) return
        save(file.copy(completed = file.completed + lessonId))
    }

    /** 1 + length of the completed prefix of [LEVEL_PROGRESSION], capped at its size. */
    fun currentLevel(): Int {
        val completed = completedLessons()
        var prefix = 0
        for (info in LEVEL_PROGRESSION) {
            if (info.lessonId in completed) prefix++ else break
        }
        return (1 + prefix).coerceAtMost(LEVEL_PROGRESSION.size)
    }

    fun isUnlocked(lessonId: String): Boolean {
        val index = LEVEL_PROGRESSION.indexOfFirst { it.lessonId == lessonId }
        if (index < 0) return false
        return index < currentLevel()
    }

    fun levelInfo(level: Int): LevelInfo? =
        LEVEL_PROGRESSION.firstOrNull { it.level == level }

    /** Info for the next level to earn, or null when maxed. */
    fun nextLevelInfo(): LevelInfo? = levelInfo(currentLevel() + 1)

    // ---- Per-card marks ----

    fun recordCardMark(cardKey: String, correct: Boolean) {
        val file = load()
        save(file.copy(marks = file.marks + (cardKey to correct)))
    }

    fun cardMark(cardKey: String): Boolean? = load().marks[cardKey]

    fun marks(): Map<String, Boolean> = load().marks

    // ---- Resumable deck runs ----

    fun deckProgress(deckId: String): DeckProgress? = load().decks[deckId]

    fun saveDeckProgress(progress: DeckProgress) {
        val file = load()
        save(file.copy(decks = file.decks + (progress.deckId to progress)))
    }

    fun clearDeckProgress(deckId: String) {
        val file = load()
        save(file.copy(decks = file.decks - deckId))
    }

    fun finishedDeckIds(): Set<String> =
        load().decks.values.filter { it.finished }.map { it.deckId }.toSet()

    // ---- Per-question lesson answers ----

    fun lessonProgress(lessonId: String): LessonProgress? = load().lessons[lessonId]

    /**
     * Records one picked answer and returns the updated progress.
     * [LessonProgress.finished] flips to true exactly when [totalQuestions]
     * is positive and every question index `0 until totalQuestions` has a
     * recorded answer.
     */
    fun recordLessonAnswer(lessonId: String, questionIndex: Int, picked: Int, totalQuestions: Int): LessonProgress {
        val file = load()
        val answers = (file.lessons[lessonId]?.answers ?: emptyMap()) + (questionIndex to picked)
        val finished = totalQuestions > 0 && (0 until totalQuestions).all { it in answers }
        val updated = LessonProgress(lessonId = lessonId, answers = answers, finished = finished)
        save(file.copy(lessons = file.lessons + (lessonId to updated)))
        return updated
    }

    fun clearLessonProgress(lessonId: String) {
        val file = load()
        save(file.copy(lessons = file.lessons - lessonId))
    }

    fun finishedLessonIds(): Set<String> =
        load().lessons.values.filter { it.finished }.map { it.lessonId }.toSet()

    fun clearAll() {
        storage.remove(KEY)
    }
}
