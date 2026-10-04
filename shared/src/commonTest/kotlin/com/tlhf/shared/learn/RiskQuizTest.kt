package com.tlhf.shared.learn

import com.tlhf.shared.strategies.StrategyType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RiskQuizTest {

    @Test
    fun strategyToLessonMappingIsExact() {
        assertEquals("coveredCall", lessonIdForStrategy(StrategyType.COVERED_CALL))
        assertEquals("cashPut", lessonIdForStrategy(StrategyType.CASH_SECURED_PUT))
        assertEquals("bullCall", lessonIdForStrategy(StrategyType.BULL_CALL_SPREAD))
        assertEquals("bearPut", lessonIdForStrategy(StrategyType.BEAR_PUT_SPREAD))
        assertEquals("ironCondor", lessonIdForStrategy(StrategyType.IRON_CONDOR))
        assertEquals("ironCondor", lessonIdForStrategy(StrategyType.IRON_BUTTERFLY))
        assertEquals("leapsCall", lessonIdForStrategy(StrategyType.LEAPS_CALL))
        assertEquals("leapsCall", lessonIdForStrategy(StrategyType.LONG_STRADDLE))
        assertEquals("leapsCall", lessonIdForStrategy(StrategyType.LONG_STRANGLE))
    }

    @Test
    fun everyStrategyYieldsFiveQuestionsFromItsMappedLesson() {
        for (type in StrategyType.entries) {
            val quiz = riskQuizFor(type)
            assertEquals(5, quiz.size, "expected 5 questions for $type")
            val lesson = LESSONS.first { it.id == lessonIdForStrategy(type) }
            assertEquals((lesson.quiz + lesson.extraQuiz).take(5), quiz)
        }
    }

    @Test
    fun countIsRespected() {
        assertEquals(3, riskQuizFor(StrategyType.COVERED_CALL, count = 3).size)
        val lesson = LESSONS.first { it.id == "coveredCall" }
        assertEquals(lesson.quiz, riskQuizFor(StrategyType.COVERED_CALL, count = 3))
        assertEquals(0, riskQuizFor(StrategyType.COVERED_CALL, count = 0).size)
    }

    @Test
    fun verdictThresholds() {
        assertTrue(riskVerdict(4, 5))
        assertTrue(riskVerdict(5, 5))
        assertFalse(riskVerdict(3, 5))
        assertFalse(riskVerdict(0, 0))
        assertFalse(riskVerdict(0, 5))
        assertTrue(riskVerdict(8, 10))
        assertFalse(riskVerdict(7, 10))
    }
}
