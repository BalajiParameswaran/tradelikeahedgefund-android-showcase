package com.tlhf.shared.ai

import com.tlhf.shared.learn.LESSONS
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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

/**
 * Wrap a plain-text prompt in the Qwen ChatML framing the model was
 * fine-tuned with. The shared builders already embed the tutor system rules
 * at the top of the text, so a single user turn carrying the whole prompt is
 * the correct minimal frame — without it (raw text on Android) the model
 * never sees a turn boundary and can return an empty completion; iOS already
 * frames its prompts this way in LlamaEngine.swift.
 */
fun wrapChatMlUserTurn(prompt: String): String =
    "<|im_start|>user\n" + prompt.trim() + "<|im_end|>\n<|im_start|>assistant\n"

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
        "(e.g. 'XYZ at \$100 — example only'). " +
        // Small models drift from the JSON contract unless it is stated bluntly:
        // the parser is tolerant, but a clean array is still the happy path.
        "Start the reply with '[' and end with ']' — no code fences, no markdown, double quotes only."

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
        "given; otherwise use a clearly-labeled hypothetical example (e.g. 'XYZ at \$100 — example only'). " +
        // Same small-model formatting guardrails as aiTopicPrompt.
        "Start the reply with '[' and end with ']' — no code fences, no markdown, double quotes only."
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
 *
 * Hardened for the 0.5B on-device model, which produces near-miss JSON:
 * code fences, trailing commas, capitalized keys, a {"cards": [...]}
 * wrapper, and truncated arrays are all tolerated. It never fabricates
 * cards — unusable input yields an empty list and callers fall back to the
 * static deck.
 */
fun parseAiCards(text: String, max: Int = 5): List<DynamicCard> {
    val cap = maxOf(1, max)
    if (text.isBlank()) return emptyList()
    // Small models love wrapping JSON in ```json fences; strip them first so
    // neither the JSON path nor the paragraph fallback sees the backticks.
    val cleaned = stripCodeFences(text)
    val arrayText = extractArrayText(cleaned)
    if (arrayText != null) {
        val cards = tryParseCardArray(arrayText)
        if (cards.isNotEmpty()) return cards.dedupeByFront().take(cap)
        // Whole-array parse failed (often a truncated final card): salvage
        // each complete {...} object inside the array on its own.
        val salvaged = salvageCards(arrayText)
        if (salvaged.isNotEmpty()) return salvaged.dedupeByFront().take(cap)
    }
    return cleaned.split(Regex("""\n\s*\n"""))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        // A paragraph that opens with '[' or '{' is JSON debris, not prose —
        // never present raw JSON garbage to the learner as a "card".
        .filter { !it.startsWith("[") && !it.startsWith("{") }
        .take(cap)
        .map { p ->
            val first = p.split(Regex("""(?<=[.!?])\s""")).firstOrNull()?.trim().orEmpty()
            DynamicCard(first.take(300), p.take(300))
        }
        .filter { it.front.isNotEmpty() }
        .dedupeByFront()
}

private fun List<DynamicCard>.dedupeByFront(): List<DynamicCard> =
    distinctBy { it.front }

private fun stripCodeFences(text: String): String =
    // Removes ``` and ```json markers wherever they appear, keeping content.
    text.replace(Regex("""```[a-zA-Z]*"""), "")

/** Strip commas that sit directly before a '}' or ']' (invalid strict JSON). */
private fun stripTrailingCommas(text: String): String {
    val trailing = Regex(""",\s*([}\]])""")
    var out = text
    // Loop: removing one comma can expose another (e.g. "[1,,]").
    while (true) {
        val next = trailing.replace(out) { it.groupValues[1] }
        if (next == out) return out
        out = next
    }
}

/**
 * Balanced-bracket scan from the first '[' to its matching ']'.
 * The old greedy regex `\[[\s\S]*\]` over-matched to the LAST ']' in the
 * output, so any ']' in trailing prose corrupted the array. If no matching
 * ']' exists (truncated output), return the remainder so the salvage pass
 * can still recover the complete objects inside it.
 */
private fun extractArrayText(text: String): String? {
    val start = text.indexOf('[')
    if (start < 0) return null
    return extractBalanced(text, start, '[', ']') ?: text.substring(start)
}

/**
 * Substring spanning the balanced open/close pair starting at [start]
 * (which must be [open]), ignoring brackets inside string literals.
 * Null when the pair never closes.
 */
private fun extractBalanced(text: String, start: Int, open: Char, close: Char): String? {
    var depth = 0
    var inString = false
    var escaped = false
    for (i in start until text.length) {
        val c = text[i]
        if (inString) {
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> inString = false
            }
        } else {
            when (c) {
                '"' -> inString = true
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
    }
    return null
}

/** Case-insensitive lookup: the model often emits "Front"/"Back". */
private fun JsonObject.stringField(name: String): String =
    entries.firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
        ?.trim()?.take(300).orEmpty()

private fun cardFromObject(obj: JsonObject): DynamicCard? {
    val front = obj.stringField("front")
    val back = obj.stringField("back")
    return if (front.isNotEmpty() && back.isNotEmpty()) DynamicCard(front, back) else null
}

private fun cardsFromArray(array: JsonArray): List<DynamicCard> =
    array.mapNotNull { el ->
        runCatching { cardFromObject(el.jsonObject) }.getOrNull()
    }

/**
 * Parse [arrayText] as a JSON array of cards, also tolerating a wrapping
 * object such as {"cards": [ ... ]}. Empty on any parse failure.
 */
private fun tryParseCardArray(arrayText: String): List<DynamicCard> =
    try {
        val element: JsonElement = Json.parseToJsonElement(stripTrailingCommas(arrayText))
        when (element) {
            is JsonArray -> cardsFromArray(element)
            is JsonObject -> {
                val inner = element.entries.firstOrNull { it.key.equals("cards", ignoreCase = true) }?.value
                if (inner is JsonArray) cardsFromArray(inner) else emptyList()
            }
            else -> emptyList()
        }
    } catch (_: Exception) {
        emptyList()
    }

/**
 * Recover cards from a malformed/truncated array by scanning for balanced
 * {...} objects and parsing each one individually; invalid or incomplete
 * objects are skipped, never invented.
 */
private fun salvageCards(arrayText: String): List<DynamicCard> {
    val cards = mutableListOf<DynamicCard>()
    var i = 0
    while (i < arrayText.length) {
        if (arrayText[i] != '{') {
            i++
            continue
        }
        val objText = extractBalanced(arrayText, i, '{', '}')
            ?: break // truncated object — nothing complete can follow it
        try {
            val obj = Json.parseToJsonElement(stripTrailingCommas(objText)).jsonObject
            cardFromObject(obj)?.let { cards.add(it) }
        } catch (_: Exception) {
            // skip this object, keep scanning for the next one
        }
        i += objText.length
    }
    return cards
}
