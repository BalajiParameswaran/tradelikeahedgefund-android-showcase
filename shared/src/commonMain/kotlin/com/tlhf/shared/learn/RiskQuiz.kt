package com.tlhf.shared.learn

import com.tlhf.shared.strategies.StrategyType

/**
 * "Explain the risk first" quiz gate: before trading a strategy, the user
 * re-answers that strategy's lesson questions. Questions come straight
 * from the lesson content — nothing new is invented here.
 */

/** Lesson that teaches [type]. Related strategies share their closest lesson. */
fun lessonIdForStrategy(type: StrategyType): String = when (type) {
    StrategyType.COVERED_CALL -> "coveredCall"
    StrategyType.CASH_SECURED_PUT -> "cashPut"
    StrategyType.BULL_CALL_SPREAD -> "bullCall"
    StrategyType.BEAR_PUT_SPREAD -> "bearPut"
    StrategyType.IRON_CONDOR -> "ironCondor"
    StrategyType.IRON_BUTTERFLY -> "ironCondor"
    StrategyType.LEAPS_CALL -> "leapsCall"
    StrategyType.LONG_STRADDLE -> "leapsCall"
    StrategyType.LONG_STRANGLE -> "leapsCall"
}

/** First [count] questions of the mapped lesson (quiz + extraQuiz), deterministic — no shuffle. */
fun riskQuizFor(type: StrategyType, count: Int = 5): List<QuizQuestion> {
    val lessonId = lessonIdForStrategy(type)
    val lesson = LESSONS.firstOrNull { it.id == lessonId } ?: return emptyList()
    return (lesson.quiz + lesson.extraQuiz).take(count.coerceAtLeast(0))
}

/** Pass bar for the risk gate: at least 80% correct, with at least one question. */
fun riskVerdict(correct: Int, total: Int): Boolean =
    total > 0 && correct.toDouble() / total >= 0.8
