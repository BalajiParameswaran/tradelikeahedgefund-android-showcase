package com.tlhf.shared.learn

import com.tlhf.shared.data.Quote
import com.tlhf.shared.portfolio.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PositionCardsTest {

    private fun guess(cards: List<Flashcard>, frontPart: String): Flashcard =
        cards.first { it.kind == FlashcardKind.GUESS && it.front.contains(frontPart) }

    @Test
    fun positionCardsUseOnlyPositionAndQuoteNumbers() {
        val cards = buildPositionCards(
            Position("AAPL", 250.0, 150.0),
            Quote(symbol = "AAPL", price = 200.0, change = 5.0, changePct = 2.56)
        )
        assertEquals(5, cards.size)
        assertTrue(cards.all { it.lessonId == "position_AAPL" })

        // INFO card states the position.
        val info = cards.first { it.kind == FlashcardKind.INFO }
        assertTrue(info.front.contains("250 shares"))
        assertTrue(info.front.contains("$150.00"))
        assertTrue(info.front.contains("$200.00"))
        assertEquals(info.front, info.back)

        // Unrealized $: (200 - 150) x 250 = $12,500.00.
        val pnl = guess(cards, "Total unrealized P&L?")
        assertEquals("$12,500.00", pnl.choices[pnl.answerIndex])
        assertEquals(4, pnl.choices.distinct().size)

        // Unrealized %: (200 / 150 - 1) x 100 = +33.33%.
        val pct = guess(cards, "in percent?")
        assertEquals("+33.33%", pct.choices[pct.answerIndex])
        assertEquals(4, pct.choices.distinct().size)

        // Covered-call capacity: floor(250 / 100) = 2.
        val contracts = guess(cards, "covered-call contracts")
        assertEquals("2", contracts.choices[contracts.answerIndex])
        assertEquals(4, contracts.choices.distinct().size)

        // +10% move: 0.10 x 250 x 200 = $5,000.00.
        val move = guess(cards, "moves +10%")
        assertEquals("$5,000.00", move.choices[move.answerIndex])
        assertEquals(4, move.choices.distinct().size)

        // Every GUESS card explains its arithmetic on the back.
        for (card in cards.filter { it.kind == FlashcardKind.GUESS }) {
            assertTrue(card.back.isNotBlank())
            assertTrue(card.answerIndex in card.choices.indices)
        }
    }

    @Test
    fun negativePnlFormatsWithMinusBeforeDollar() {
        val cards = buildPositionCards(
            Position("AAPL", 100.0, 200.0),
            Quote(symbol = "AAPL", price = 150.0)
        )
        // (150 - 200) x 100 = -$5,000.00; percent = -25.00%.
        val pnl = guess(cards, "Total unrealized P&L?")
        assertEquals("-$5,000.00", pnl.choices[pnl.answerIndex])
        val pct = guess(cards, "in percent?")
        assertEquals("-25.00%", pct.choices[pct.answerIndex])
    }

    @Test
    fun fewerThanOneHundredSharesYieldsInfoCardInsteadOfContractsGuess() {
        val cards = buildPositionCards(
            Position("MSFT", 50.0, 100.0),
            Quote(symbol = "MSFT", price = 120.0)
        )
        assertEquals(5, cards.size)
        assertTrue(cards.none { it.kind == FlashcardKind.GUESS && it.front.contains("covered-call contracts") })
        val capacity = cards.first { it.front.contains("Covered calls need 100 shares") }
        assertEquals(FlashcardKind.INFO, capacity.kind)
        assertTrue(capacity.front.contains("50 shares of MSFT"))
        // The other three guesses are still there.
        assertEquals(3, cards.count { it.kind == FlashcardKind.GUESS })
    }

    @Test
    fun zeroPnlChoicesStayDistinct() {
        val cards = buildPositionCards(
            Position("XYZ", 100.0, 100.0),
            Quote(symbol = "XYZ", price = 100.0)
        )
        val pnl = guess(cards, "Total unrealized P&L?")
        assertEquals("$0.00", pnl.choices[pnl.answerIndex])
        assertEquals(4, pnl.choices.distinct().size)
        val pct = guess(cards, "in percent?")
        assertEquals("+0.00%", pct.choices[pct.answerIndex])
        assertEquals(4, pct.choices.distinct().size)
    }

    @Test
    fun stockCardsUseOnlyQuoteNumbers() {
        val cards = buildStockCards(
            "AAPL",
            Quote(symbol = "AAPL", price = 200.0, change = 5.0, changePct = 2.56)
        )
        assertEquals(4, cards.size)
        assertTrue(cards.all { it.lessonId == "stock_AAPL" })

        val info = cards.first { it.kind == FlashcardKind.INFO }
        assertTrue(info.front.contains("$200.00"))
        assertTrue(info.front.contains("up $5.00"))
        assertTrue(info.front.contains("$20,000.00"))

        // 100 shares at $200 = $20,000.00.
        val cost = guess(cards, "100 shares of AAPL cost")
        assertEquals("$20,000.00", cost.choices[cost.answerIndex])
        assertEquals(4, cost.choices.distinct().size)

        // +5% from $200 = $210.00.
        val rise = guess(cards, "rises 5%")
        assertEquals("$210.00", rise.choices[rise.answerIndex])
        assertEquals(4, rise.choices.distinct().size)

        val qualitative = cards.last()
        assertEquals(FlashcardKind.INFO, qualitative.kind)
        assertTrue(qualitative.front.contains("Trade tab"))
    }

    @Test
    fun stockCardsShowDownMove() {
        val cards = buildStockCards(
            "TSLA",
            Quote(symbol = "TSLA", price = 250.0, change = -10.0, changePct = -3.85)
        )
        val info = cards.first { it.kind == FlashcardKind.INFO }
        assertTrue(info.front.contains("down $10.00"))
        assertTrue(info.front.contains("-3.85%"))
    }
}
