package com.tlhf.shared.ai

import com.tlhf.shared.learn.LESSONS
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Prompt builders + card parser for the on-device AI, ported from the web
 * app (web/app.html). The anti-hallucination rules are the product: the model
 * must never invent prices, strikes, premiums, or expirations.
 */

data class DynamicCard(val front: String, val back: String)

fun tutorSystemPrompt(): String =
    "You are a plain-English options tutor inside a trading app. Educational only — NOT financial advice. " +
        "HARD RULES: Never invent stock prices, option strikes, premiums, or expiration dates. " +
        "Use ONLY numbers from PORTFOLIO CONTEXT and MARKET CONTEXT below. " +
        "If no live price is available, say so explicitly and teach with clearly-labeled hypothetical examples " +
        "(e.g. 'XYZ at \$100 — example only'). " +
        "Never present example numbers as real quotes. Do not predict stock moves or promise guaranteed returns. " +
        "Keep answers under 150 words unless the user asks for more detail."

/** "MARKET CONTEXT: AAPL last $227.4 (Yahoo, 3m old)." or the no-price variant. */
fun marketContext(ticker: String, spot: Double?, source: String?, ageLabel: String?): String {
    val t = ticker.uppercase()
    if (spot == null) return "MARKET CONTEXT: no live price available for $t."
    val age = ageLabel ?: "age unknown"
    return "MARKET CONTEXT: $t last \$$spot (${source ?: "unknown source"}, $age)."
}

/**
 * Full tutor prompt: system rules + market context + portfolio context +
 * last 6 chat turns + the user's question. Portfolio context is a JSON string
 * the app builds from its own state (positions, profile); pass "{}" when
 * there is nothing to include.
 */
fun buildTutorPrompt(
    market: String,
    portfolioJson: String,
    history: List<ChatMessage>,
    userText: String
): String {
    val hist = history.takeLast(6).joinToString("\n") {
        (if (it.role == "user") "User: " else "Tutor: ") + it.text
    }
    return tutorSystemPrompt() + "\n\n" + market +
        "\n\nPORTFOLIO CONTEXT:\n" + portfolioJson +
        "\n\nRECENT CHAT:\n" + hist +
        "\n\nUser: " + userText + "\nTutor:"
}

/** AI Topics: exactly 5 flashcards teaching [strategyName] with [ticker] as the running example. */
fun aiTopicPrompt(ticker: String, strategyName: String): String =
    "Create exactly 5 flashcards teaching $strategyName using $ticker as the running example. " +
        "Reply with ONLY a JSON array like [{\"front\":\"...\",\"back\":\"...\"}] — no other text. " +
        "Keep each side under 40 words, plain English, educational only. " +
        "HARD RULE: never invent real prices, strikes, or premiums for $ticker — use ONLY the price in " +
        "MARKET CONTEXT if one is given; otherwise use a clearly-labeled hypothetical example " +
        "(e.g. 'XYZ at \$100 — example only')."

/**
 * Study cards: 6 flashcards (mix of guess-and-correct and info) for
 * [strategyLabel], grounded in the learner's scenario and question.
 */
fun aiStudyPrompt(strategyLabel: String, ticker: String, price: Double?, question: String): String {
    val where = if (price != null) "$ticker at \$$price" else "$ticker (no live price available)"
    val q = question.replace(Regex("\\s+"), " ").trim().take(200)
    return "Create exactly 6 flashcards (mix of guess-and-correct and info) teaching $strategyLabel for $where. " +
        "The learner just asked: '$q'. " +
        "Make every card concrete to this scenario — use the real ticker and realistic strikes near the price above, " +
        "never invented live Greeks. " +
        "Reply with ONLY a JSON array like [{\"front\":\"...\",\"back\":\"...\"}] — no other text. " +
        "Keep each side under 40 words, plain English, educational only. " +
        "HARD RULE: never invent real prices, strikes, or premiums — use ONLY the price in MARKET CONTEXT if one is " +
        "given; otherwise use a clearly-labeled hypothetical example (e.g. 'XYZ at \$100 — example only')."
}

/** Best-effort match of free text to a lesson title (for seeding study cards). */
fun matchLessonTitle(text: String): String? {
    val lower = text.lowercase()
    return LESSONS.firstOrNull { lesson ->
        lesson.title.lowercase().split(" ", "-").filter { it.length > 3 }.any { lower.contains(it) }
    }?.title
}
fun reviewMistakesPrompt(deckTitle: String, missedQuestions: List<String>): String =
    "I missed these in the $deckTitle deck. Explain each briefly in plain English: " +
        missedQuestions.take(5).mapIndexed { i, q -> "${i + 1}. $q" }.joinToString(" ")

/**
 * Port of the web app's parseAiCards: pull the first `[...]` JSON array out
 * of the model output and parse {front, back} cards; fall back to splitting
 * on blank lines (first sentence → front, whole paragraph → back).
 */
fun parseAiCards(text: String, max: Int = 5): List<DynamicCard> {
    val cap = maxOf(1, max)
    val m = Regex("""\[[\s\S]*\]""").find(text)
    if (m != null) {
        try {
            val cards = Json.parseToJsonElement(m.value).jsonArray.mapNotNull { el ->
                val obj = el.jsonObject
                val front = obj["front"]?.jsonPrimitive?.contentOrNull?.trim()?.take(300).orEmpty()
                val back = obj["back"]?.jsonPrimitive?.contentOrNull?.trim()?.take(300).orEmpty()
                if (front.isNotEmpty() && back.isNotEmpty()) DynamicCard(front, back) else null
            }.take(cap)
            if (cards.isNotEmpty()) return cards
        } catch (_: Exception) {
            // fall through to the paragraph splitter
        }
    }
    return text.split(Regex("""\n\s*\n"""))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .take(cap)
        .map { p ->
            val first = p.split(Regex("""(?<=[.!?])\s""")).firstOrNull()?.trim().orEmpty()
            DynamicCard(first.take(300), p.take(300))
        }
        .filter { it.front.isNotEmpty() }
}
