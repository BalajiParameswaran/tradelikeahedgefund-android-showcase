package com.tlhf.shared.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiPromptsTest {

    @Test
    fun topicPromptHasTickerStrategyAndHardRule() {
        val p = aiTopicPrompt("AAPL", "Iron Condor")
        assertTrue(p.contains("AAPL"))
        assertTrue(p.contains("Iron Condor"))
        assertTrue(p.contains("exactly 5 flashcards"))
        assertTrue(p.contains("HARD RULE"))
        assertTrue(p.contains("example only"))
    }

    @Test
    fun studyPromptHasSixCardsAndQuestion() {
        val p = aiStudyPrompt("Bull Call Spread", "NVDA", 182.5, "How does the short call cap my profit?")
        assertTrue(p.contains("exactly 6 flashcards"))
        assertTrue(p.contains("NVDA"))
        assertTrue(p.contains("How does the short call cap my profit?"))
        assertTrue(p.contains("HARD RULE"))
    }

    @Test
    fun studyPromptWithoutPriceSaysNoLivePrice() {
        val p = aiStudyPrompt("Covered Call", "XYZ", null, "q")
        assertTrue(p.contains("no live price available"))
    }

    @Test
    fun tutorPromptAssemblesAllContexts() {
        val hist = listOf(ChatMessage("user", "hi", 1), ChatMessage("bot", "hello", 2))
        val p = buildTutorPrompt(
            market = marketContext("AAPL", 227.4, "Yahoo", "3m old"),
            portfolioJson = "{\"positions\":[]}",
            history = hist,
            userText = "What is delta?"
        )
        assertTrue(p.contains("plain-English options tutor"))
        assertTrue(p.contains("AAPL last \$227.4"))
        assertTrue(p.contains("PORTFOLIO CONTEXT"))
        assertTrue(p.contains("User: hi"))
        assertTrue(p.contains("User: What is delta?\nTutor:"))
    }

    @Test
    fun marketContextWithoutPrice() {
        assertEquals(
            "MARKET CONTEXT: no live price available for TSLA.",
            marketContext("tsla", null, null, null)
        )
    }

    @Test
    fun parseAiCardsParsesJsonArray() {
        val out = "Here you go:\n[{\"front\":\"What is delta?\",\"back\":\"How much the option price moves per $1 of stock.\"}," +
            "{\"front\":\"Bad\",\"back\":\"\"}]\nDone."
        val cards = parseAiCards(out, max = 5)
        assertEquals(1, cards.size)
        assertEquals("What is delta?", cards[0].front)
        assertTrue(cards[0].back.contains("option price moves"))
    }

    @Test
    fun parseAiCardsFallsBackToParagraphs() {
        val out = "Delta measures sensitivity. It ranges 0 to 1 for calls.\n\nTheta is time decay. It hurts buyers daily."
        val cards = parseAiCards(out, max = 5)
        assertEquals(2, cards.size)
        assertEquals("Delta measures sensitivity.", cards[0].front)
        assertTrue(cards[1].back.contains("time decay"))
    }

    @Test
    fun parseAiCardsRespectsMax() {
        val out = (1..10).joinToString("\n\n") { "Card $it. Some longer explanation text here for card number $it." }
        assertEquals(3, parseAiCards(out, max = 3).size)
    }

    @Test
    fun reviewMistakesPromptCapsAtFive() {
        val p = reviewMistakesPrompt("Mixed Deck", listOf("q1", "q2", "q3", "q4", "q5", "q6"))
        assertTrue(p.contains("Mixed Deck"))
        assertTrue(p.contains("5. q5"))
        assertTrue(!p.contains("q6"))
    }
}
