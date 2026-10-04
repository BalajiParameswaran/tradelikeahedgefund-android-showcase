package com.tlhf.shared.learn

import com.tlhf.shared.data.Quote
import com.tlhf.shared.portfolio.Position
import com.tlhf.shared.portfolio.positionUnrealized
import kotlin.math.abs
import kotlin.math.floor

/**
 * Flashcards built from a real position / live quote. NEVER invent numbers:
 * every value on these cards is arithmetic over the [Position] / [Quote]
 * arguments — shares, average cost and the latest price are the only inputs.
 * Money and percent formatting is hand-rolled (no java.text in commonMain).
 */

private fun groupThousands(n: Long): String {
    val s = n.toString()
    val sb = StringBuilder()
    for ((i, c) in s.withIndex()) {
        if (i > 0 && (s.length - i) % 3 == 0) sb.append(',')
        sb.append(c)
    }
    return sb.toString()
}

private fun fmt(v: Double): String {
    if (!v.isFinite()) return "$0.00"
    val negative = v < 0.0
    val centsTotal = (abs(v) * 100.0 + 0.5).toLong()
    val dollars = centsTotal / 100
    val cents = (centsTotal % 100).toString().padStart(2, '0')
    return (if (negative) "-$" else "$") + groupThousands(dollars) + "." + cents
}

private fun fmtPct(v: Double): String {
    if (!v.isFinite()) return "+0.00%"
    val sign = if (v < 0.0) "-" else "+"
    val hundredths = (abs(v) * 100.0 + 0.5).toLong()
    val whole = hundredths / 100
    val frac = (hundredths % 100).toString().padStart(2, '0')
    return "$sign$whole.$frac%"
}

private fun fmtShares(v: Double): String {
    if (!v.isFinite()) return "0"
    return if (v == floor(v)) v.toLong().toString() else v.toString()
}

/** Nudge distractors (by [step]) until none formats the same as [correct] or another distractor. */
private fun distinctByFormat(
    correct: Double,
    distractors: List<Double>,
    step: Double,
    format: (Double) -> String
): List<Double> {
    val out = mutableListOf<Double>()
    for (raw in distractors) {
        var v = raw
        while (format(v) == format(correct) || out.any { format(it) == format(v) }) {
            v += step
        }
        out += v
    }
    return out
}

/** Correct value first, distractors after — all formatted with [fmt]. */
private fun dollarChoicesOrdered(
    correct: Double,
    distractors: List<Double>,
    step: Double = 100.0
): Pair<List<String>, Int> {
    val distinct = distinctByFormat(correct, distractors, step, ::fmt)
    return (listOf(correct) + distinct).map(::fmt) to 0
}

/** Correct value inserted at a symbol-derived position, so it is not always first. */
private fun dollarChoicesHashed(
    correct: Double,
    distractors: List<Double>,
    symbol: String
): Pair<List<String>, Int> {
    val distinct = distinctByFormat(correct, distractors, 100.0, ::fmt)
    val pos = abs(symbol.hashCode() % 4)
    val values = distinct.toMutableList()
    values.add(pos, correct)
    return values.map(::fmt) to pos
}

fun buildPositionCards(position: Position, quote: Quote): List<Flashcard> {
    val symbol = position.symbol
    val lessonId = "position_$symbol"
    val price = quote.price
    val sharesText = fmtShares(position.shares)
    val (pnl, pct) = positionUnrealized(position, price)
    val cards = mutableListOf<Flashcard>()

    // INFO: the position itself, stated plainly.
    val infoText = "Your $symbol position: $sharesText shares at avg ${fmt(position.avgCost)}. " +
        "Latest price ${fmt(price)}."
    cards += Flashcard(lessonId, FlashcardKind.INFO, infoText, back = infoText)

    // GUESS: unrealized $ P&L.
    run {
        val distractors = listOf(-pnl, pnl / 2.0, pnl + position.shares * 1.00)
        val (choices, answerIndex) = dollarChoicesHashed(pnl, distractors, symbol)
        cards += Flashcard(
            lessonId = lessonId,
            kind = FlashcardKind.GUESS,
            front = "Your $symbol: $sharesText shares, avg ${fmt(position.avgCost)}, now ${fmt(price)}. Total unrealized P&L?",
            choices = choices,
            answerIndex = answerIndex,
            back = "(${fmt(price)} - ${fmt(position.avgCost)}) x $sharesText shares = ${fmt(pnl)} unrealized P&L."
        )
    }

    // GUESS: unrealized % P&L.
    run {
        val distractors = distinctByFormat(pct, listOf(pct + 5.0, pct - 5.0, -pct), 2.0, ::fmtPct)
        val choices = (listOf(pct) + distractors).map(::fmtPct)
        val back = if (position.avgCost > 0.0) {
            "(${fmt(price)} / ${fmt(position.avgCost)} - 1) x 100 = ${fmtPct(pct)} unrealized return."
        } else {
            "With a ${fmt(position.avgCost)} average cost there is no cost basis to measure a percent against, so this is shown as ${fmtPct(pct)}."
        }
        cards += Flashcard(
            lessonId = lessonId,
            kind = FlashcardKind.GUESS,
            front = "Your $symbol: $sharesText shares, avg ${fmt(position.avgCost)}, now ${fmt(price)}. Total unrealized P&L in percent?",
            choices = choices,
            answerIndex = 0,
            back = back
        )
    }

    // Covered-call capacity: one contract per 100 shares.
    val contracts = floor(position.shares / 100.0).toInt()
    if (contracts >= 1) {
        val rawChoices = listOf(contracts, contracts + 1, maxOf(0, contracts - 1), contracts * 100)
        val intChoices = mutableListOf<Int>()
        for (raw in rawChoices) {
            var v = raw
            while (v in intChoices) v += 1
            intChoices += v
        }
        cards += Flashcard(
            lessonId = lessonId,
            kind = FlashcardKind.GUESS,
            front = "How many covered-call contracts can your $sharesText $symbol shares support?",
            choices = intChoices.map { it.toString() },
            answerIndex = intChoices.indexOf(contracts),
            back = "One contract covers 100 shares, so $sharesText / 100 = $contracts contract(s), rounded down."
        )
    } else {
        val text = "Covered calls need 100 shares per contract - your $sharesText shares of $symbol aren't enough for one contract yet."
        cards += Flashcard(lessonId, FlashcardKind.INFO, text, back = text)
    }

    // GUESS: what a +10% move does to the position value.
    run {
        val correct = 0.10 * position.shares * price
        val distractors = listOf(correct / 2.0, correct * 2.0, position.shares * price * 0.01)
        val (choices, answerIndex) = dollarChoicesOrdered(correct, distractors)
        cards += Flashcard(
            lessonId = lessonId,
            kind = FlashcardKind.GUESS,
            front = "If $symbol moves +10% from ${fmt(price)}, by how much does your position value change?",
            choices = choices,
            answerIndex = answerIndex,
            back = "10% x $sharesText shares x ${fmt(price)} = ${fmt(correct)} change in position value."
        )
    }

    return cards
}

fun buildStockCards(symbol: String, quote: Quote): List<Flashcard> {
    val lessonId = "stock_$symbol"
    val price = quote.price
    val cards = mutableListOf<Flashcard>()

    // INFO: the last trade, and what one contract controls at this price.
    run {
        val direction = when {
            quote.change > 0.0 -> "up"
            quote.change < 0.0 -> "down"
            else -> "flat"
        }
        val text = "$symbol last traded at ${fmt(price)} ($direction ${fmt(abs(quote.change))}, " +
            "${fmtPct(quote.changePct)} today). One options contract controls 100 shares = " +
            "${fmt(100.0 * price)} of stock at this price."
        cards += Flashcard(lessonId, FlashcardKind.INFO, text, back = text)
    }

    // GUESS: cost of 100 shares.
    run {
        val correct = 100.0 * price
        val (choices, answerIndex) = dollarChoicesOrdered(
            correct,
            listOf(90.0 * price, 110.0 * price, 100.0 * price + 100.0)
        )
        cards += Flashcard(
            lessonId = lessonId,
            kind = FlashcardKind.GUESS,
            front = "How much would 100 shares of $symbol cost at ${fmt(price)} per share?",
            choices = choices,
            answerIndex = answerIndex,
            back = "100 x ${fmt(price)} = ${fmt(correct)}."
        )
    }

    // GUESS: price after a +5% move.
    run {
        val correct = price * 1.05
        val (choices, answerIndex) = dollarChoicesOrdered(
            correct,
            listOf(price * 0.95, price * 1.5, price + 5.00),
            step = 5.0
        )
        cards += Flashcard(
            lessonId = lessonId,
            kind = FlashcardKind.GUESS,
            front = "$symbol rises 5% from ${fmt(price)} - what is the new price?",
            choices = choices,
            answerIndex = answerIndex,
            back = "${fmt(price)} x 1.05 = ${fmt(correct)}."
        )
    }

    // INFO: where the Trade tab's payoff charts start from.
    run {
        val text = "Options on $symbol are priced from this stock price - the payoff charts in the Trade tab start here."
        cards += Flashcard(lessonId, FlashcardKind.INFO, text, back = text)
    }

    return cards
}
