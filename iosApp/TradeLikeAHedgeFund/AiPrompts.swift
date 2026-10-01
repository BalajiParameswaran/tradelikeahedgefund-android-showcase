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
func parseAiCards(_ text: String, expected: Int) -> [DynamicCard] {
    let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)

    func cards(from array: [[String: String]]) -> [DynamicCard] {
        array.compactMap { dict in
            guard let f = dict["front"], let b = dict["back"],
                  !f.trimmingCharacters(in: .whitespaces).isEmpty,
                  !b.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
            return DynamicCard(front: f.trimmingCharacters(in: .whitespaces),
                               back: b.trimmingCharacters(in: .whitespaces))
        }
    }

    let candidates: [String] = [trimmed] + {
        // strip markdown fences
        var t = trimmed
        if t.hasPrefix("```") {
            t = t.replacingOccurrences(of: "^```[a-zA-Z]*\\n", with: "", options: .regularExpression)
            if let r = t.range(of: "```", options: .backwards) { t = String(t[..<r.lowerBound]) }
        }
        return t == trimmed ? [] : [t.trimmingCharacters(in: .whitespacesAndNewlines)]
    }()

    for c in candidates {
        if let data = c.data(using: .utf8),
           let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: String]] {
            let cards = cards(from: arr)
            if !cards.isEmpty { return Array(cards.prefix(expected)) }
        }
    }

    // paragraph fallback
    let paras = trimmed.components(separatedBy: "\n\n")
        .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
        .filter { !$0.isEmpty }
    if paras.count >= 2 {
        return stride(from: 0, to: paras.count - 1, by: 2).prefix(expected).map {
            DynamicCard(front: paras[$0], back: paras[$0 + 1])
        }
    }
    return []
}
