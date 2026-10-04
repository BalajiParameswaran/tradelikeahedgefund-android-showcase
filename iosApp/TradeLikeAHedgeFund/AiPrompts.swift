import Foundation

/// Prompt builders + dynamic-card parsing for the on-device AI.
/// Swift mirror of shared/.../ai/AiPrompts.kt — same wording, same rules.

enum AiPrompts {
    static func tutorSystem() -> String {
        """
        You are the Trade Like a Hedge Fund tutor, an expert options educator. \
        You teach in plain English. Rules: educational content only, never financial \
        advice, never recommend a specific trade. Never invent stock prices, \
        strike prices, option premiums, or expiration dates — use only the market \
        context the user provides, and if no live price is known say so explicitly. \
        If you use hypothetical numbers, label them "example only". Do not predict \
        market moves or promise returns. Keep answers concise and structured with \
        short sections. When the user asks to make study cards, respond with \
        exactly 6 cards in this JSON format: \
        [{"front":"...","back":"..."}, ...] and nothing else.
        """
    }

    static func marketContext(ticker: String, price: Double?, iv: Double?, days: Int?) -> String {
        var parts: [String] = ["Ticker: \(ticker.uppercased())"]
        parts.append(price.map { "Known price: \($0)" } ?? "Known price: not provided")
        if let iv { parts.append("Implied volatility: \(Int((iv * 100).rounded()))%") }
        if let days { parts.append("Days to expiration: \(days)") }
        return "Market context (use only this, never invent numbers):\n" + parts.joined(separator: "\n")
    }

    static func aiTopic(ticker: String, strategy: String) -> String {
        """
        Create exactly 5 flashcards that teach "\(strategy)" using \(ticker.uppercased()) \
        as the running example. Each card: front = a question or scenario, \
        back = the plain-English answer. Respond with ONLY a JSON array: \
        [{"front":"...","back":"..."}, ...] and nothing else. No markdown, \
        no code fences, no commentary.
        """
    }

    static func studyDeck(topic: String, lessonTitle: String?) -> String {
        let lesson = lessonTitle.map { " from the lesson \"\($0)\"" } ?? ""
        return "Turn this answer into exactly 6 flashcards\(lesson) about \(topic). " +
        "Respond with ONLY a JSON array: [{\"front\":\"...\",\"back\":\"...\"}, ...] and nothing else."
    }

    static func reviewMistakes(deckTitle: String, missed: [String]) -> String {
        "I missed these in the \(deckTitle) deck. Explain each briefly in plain English: " +
        missed.prefix(5).enumerated().map { "\($0.offset + 1). \($0.element)" }.joined(separator: " ")
    }

    /// Best-effort match of free text to one of the lesson titles.
    static func matchLessonTitle(_ text: String, lessons: [Lesson]) -> String? {
        let lower = text.lowercased()
        return lessons.first { lesson in
            lesson.title.lowercased().split { $0 == " " || $0 == "-" }
                .filter { $0.count > 3 }.contains { lower.contains($0) }
        }?.title
    }
}

struct DynamicCard: Identifiable, Hashable {
    let id = UUID()
    let front: String
    let back: String
}

/// Parse model output into cards: strict JSON array first, then ```json fences,
/// then a paragraph fallback (blank-line separated).
/// Hardened mirror of AiPrompts.kt parseAiCards — same tolerance steps for
/// the 0.5B model's near-miss JSON (fences, balanced extraction, wrapper
/// object, trailing commas, capitalized keys, truncated-array salvage,
/// JSON-debris skip, dedupe). Never fabricates cards: unusable input
/// returns [] and callers fall back to the static deck.
func parseAiCards(_ text: String, expected: Int) -> [DynamicCard] {
    let cap = max(1, expected)
    let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
    if trimmed.isEmpty { return [] }

    // Strip markdown fences (```json / ```) wherever they appear.
    let unfenced = trimmed.replacingOccurrences(
        of: "```[a-zA-Z]*", with: "", options: .regularExpression)

    func stripTrailingCommas(_ s: String) -> String {
        // Commas directly before '}' or ']' are invalid strict JSON.
        var out = s
        while true {
            let next = out.replacingOccurrences(
                of: ",\\s*([}\\]])", with: "$1", options: .regularExpression)
            if next == out { return out }
            out = next
        }
    }

    // Balanced open/close scan from `start`, ignoring brackets in strings.
    // The greedy approach over-matched to the last ']' in trailing prose.
    func extractBalanced(_ s: String, from start: String.Index,
                         open: Character, close: Character) -> String? {
        var depth = 0
        var inString = false
        var escaped = false
        var i = start
        while i < s.endIndex {
            let c = s[i]
            if inString {
                if escaped { escaped = false }
                else if c == "\\" { escaped = true }
                else if c == "\"" { inString = false }
            } else if c == "\"" {
                inString = true
            } else if c == open {
                depth += 1
            } else if c == close {
                depth -= 1
                if depth == 0 {
                    return String(s[start...i])
                }
            }
            i = s.index(after: i)
        }
        return nil
    }

    // Case-insensitive key lookup: the model often emits "Front"/"Back".
    func card(from dict: [String: Any]) -> DynamicCard? {
        func field(_ name: String) -> String {
            for (k, v) in dict where k.lowercased() == name {
                guard let s = v as? String else { return "" }
                return String(s.trimmingCharacters(in: .whitespacesAndNewlines).prefix(300))
            }
            return ""
        }
        let f = field("front")
        let b = field("back")
        return (!f.isEmpty && !b.isEmpty) ? DynamicCard(front: f, back: b) : nil
    }

    func cards(fromArray array: [Any]) -> [DynamicCard] {
        array.compactMap { ($0 as? [String: Any]).flatMap { card(from: $0) } }
    }

    func cards(fromJSON json: String) -> [DynamicCard] {
        guard let data = stripTrailingCommas(json).data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) else { return [] }
        if let arr = obj as? [Any] { return cards(fromArray: arr) }
        // Tolerate a wrapping object {"cards": [ ... ]}.
        if let dict = obj as? [String: Any] {
            for (k, v) in dict where k.lowercased() == "cards" {
                if let arr = v as? [Any] { return cards(fromArray: arr) }
            }
            // A bare single-card object is not an array; don't invent cards.
            return []
        }
        return []
    }

    func dedupe(_ cards: [DynamicCard]) -> [DynamicCard] {
        var seen = Set<String>()
        return cards.filter { seen.insert($0.front).inserted }
    }

    // Salvage: scan for balanced {...} objects and parse each individually.
    func salvage(from arrayText: String) -> [DynamicCard] {
        var result: [DynamicCard] = []
        var i = arrayText.startIndex
        while i < arrayText.endIndex {
            if arrayText[i] != "{" {
                i = arrayText.index(after: i)
                continue
            }
            guard let objText = extractBalanced(arrayText, from: i, open: "{", close: "}") else {
                break // truncated object — nothing complete can follow it
            }
            if let data = stripTrailingCommas(objText).data(using: .utf8),
               let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let c = card(from: dict) {
                result.append(c)
            }
            i = arrayText.index(i, offsetBy: objText.count)
        }
        return result
    }

    // Balanced-bracket extraction from the first '['.
    if let start = unfenced.firstIndex(of: "[") {
        let arrayText = extractBalanced(unfenced, from: start, open: "[", close: "]")
            ?? String(unfenced[start...]) // truncated: remainder for salvage
        let parsed = cards(fromJSON: arrayText)
        if !parsed.isEmpty { return Array(dedupe(parsed).prefix(cap)) }
        let salvaged = salvage(from: arrayText)
        if !salvaged.isEmpty { return Array(dedupe(salvaged).prefix(cap)) }
    } else if unfenced.trimmingCharacters(in: .whitespacesAndNewlines).hasPrefix("{") {
        // Wrapper object with no '[' would have no cards; a bare object
        // is handled by the debris skip below rather than becoming a card.
        let parsed = cards(fromJSON: unfenced)
        if !parsed.isEmpty { return Array(dedupe(parsed).prefix(cap)) }
    }

    // Paragraph fallback: skip JSON debris so raw JSON never becomes a card.
    let paras = unfenced.components(separatedBy: "\n\n")
        .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
        .filter { !$0.isEmpty && !$0.hasPrefix("[") && !$0.hasPrefix("{") }
    if paras.count >= 2 {
        return dedupe(stride(from: 0, to: paras.count - 1, by: 2).prefix(cap).map {
            DynamicCard(front: String(paras[$0].prefix(300)),
                        back: String(paras[$0 + 1].prefix(300)))
        })
    }
    return []
}
