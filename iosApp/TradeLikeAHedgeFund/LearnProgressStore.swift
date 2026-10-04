import Foundation
import Security

/// Learn progress — THE one store (merge unification, mirroring the unified
/// Kotlin `LearnProgress`): lesson completion + levels, per-card marks,
/// half-finished flashcard deck runs, and lesson quiz answers, all in one
/// Keychain blob (never plaintext on disk) with the same JSON schema as the
/// shared KMP store (`tlhf_learn_progress_v1` on Android):
/// { completed, marks, decks, lessons }; load failures degrade to empty.
/// (This file began as the deck/lesson store; the revamp branch kept its
/// levels/marks in a separate UserDefaults enum inside LearnView.swift —
/// the merge folded both into this single type, statics included.)
///
/// A deck run's card order is fully determined by its seed (see
/// `seededShuffle`), so persisting the seed plus the position reproduces
/// the run exactly on resume.
struct DeckProgress: Codable, Equatable {
    var deckId: String
    var seed: Int64
    var totalCards: Int
    var index: Int
    var correct: Int
    var answered: Int
    var missedFronts: [String] = []
    var finished: Bool = false

    private enum CodingKeys: String, CodingKey {
        case deckId, seed, totalCards, index, correct, answered, missedFronts, finished
    }

    init(deckId: String, seed: Int64, totalCards: Int, index: Int, correct: Int,
         answered: Int, missedFronts: [String] = [], finished: Bool = false) {
        self.deckId = deckId
        self.seed = seed
        self.totalCards = totalCards
        self.index = index
        self.correct = correct
        self.answered = answered
        self.missedFronts = missedFronts
        self.finished = finished
    }

    // The Kotlin store omits fields holding their default value, so every
    // defaulted field must decode as optional here.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        deckId = try c.decode(String.self, forKey: .deckId)
        seed = try c.decode(Int64.self, forKey: .seed)
        totalCards = try c.decode(Int.self, forKey: .totalCards)
        index = try c.decode(Int.self, forKey: .index)
        correct = try c.decode(Int.self, forKey: .correct)
        answered = try c.decode(Int.self, forKey: .answered)
        missedFronts = try c.decodeIfPresent([String].self, forKey: .missedFronts) ?? []
        finished = try c.decodeIfPresent(Bool.self, forKey: .finished) ?? false
    }
}

/// Quiz answers for one lesson. `answers` maps a question index — over the
/// lesson's quiz + extraQuiz lists combined, in that order — to the picked
/// choice index. On disk the keys are strings ("0", "1", ...), exactly like
/// the Kotlin store's Map<Int, Int>; the custom Codable conformance below
/// performs that conversion.
struct LessonProgress: Codable, Equatable {
    var lessonId: String
    var answers: [Int: Int] = [:]
    var finished: Bool = false

    private enum CodingKeys: String, CodingKey { case lessonId, answers, finished }

    private struct AnswerKey: CodingKey {
        var stringValue: String
        var intValue: Int? { Int(stringValue) }
        init(_ value: Int) { stringValue = String(value) }
        init?(stringValue: String) { self.stringValue = stringValue }
        init?(intValue: Int) { stringValue = String(intValue) }
    }

    init(lessonId: String, answers: [Int: Int] = [:], finished: Bool = false) {
        self.lessonId = lessonId
        self.answers = answers
        self.finished = finished
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        lessonId = try c.decode(String.self, forKey: .lessonId)
        finished = try c.decodeIfPresent(Bool.self, forKey: .finished) ?? false
        var decoded: [Int: Int] = [:]
        if c.contains(.answers) {
            let nested = try c.nestedContainer(keyedBy: AnswerKey.self, forKey: .answers)
            for key in nested.allKeys {
                if let index = key.intValue {
                    decoded[index] = try nested.decode(Int.self, forKey: key)
                }
            }
        }
        answers = decoded
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(lessonId, forKey: .lessonId)
        try c.encode(finished, forKey: .finished)
        var nested = c.nestedContainer(keyedBy: AnswerKey.self, forKey: .answers)
        for (index, picked) in answers {
            try nested.encode(picked, forKey: AnswerKey(index))
        }
    }
}

private struct ProgressFile: Codable {
    var completed: [String] = []
    var marks: [String: Bool] = [:]
    var decks: [String: DeckProgress] = [:]
    var lessons: [String: LessonProgress] = [:]

    private enum CodingKeys: String, CodingKey { case completed, marks, decks, lessons }

    init(completed: [String] = [], marks: [String: Bool] = [:],
         decks: [String: DeckProgress] = [:], lessons: [String: LessonProgress] = [:]) {
        self.completed = completed
        self.marks = marks
        self.decks = decks
        self.lessons = lessons
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        completed = try c.decodeIfPresent([String].self, forKey: .completed) ?? []
        marks = try c.decodeIfPresent([String: Bool].self, forKey: .marks) ?? [:]
        decks = try c.decodeIfPresent([String: DeckProgress].self, forKey: .decks) ?? [:]
        lessons = try c.decodeIfPresent([String: LessonProgress].self, forKey: .lessons) ?? [:]
    }
}

private enum LearnKeychain {
    static let service = "com.tradelikeahedgefund.ai"
    static let account = "tlhf_learn_progress_v1"

    static func load() -> Data? {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                kSecAttrService as String: service,
                                kSecAttrAccount as String: account,
                                kSecReturnData as String: true,
                                kSecMatchLimit as String: kSecMatchLimitOne]
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess else { return nil }
        return out as? Data
    }

    static func save(_ data: Data) {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                kSecAttrService as String: service,
                                kSecAttrAccount as String: account]
        let attrs: [String: Any] = [kSecValueData as String: data,
                                    kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
        if SecItemCopyMatching(q as CFDictionary, nil) == errSecSuccess {
            SecItemUpdate(q as CFDictionary, attrs as CFDictionary)
        } else {
            var add = q; add[kSecValueData as String] = data
            add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            SecItemAdd(add as CFDictionary, nil)
        }
    }

    static func remove() {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                kSecAttrService as String: service,
                                kSecAttrAccount as String: account]
        SecItemDelete(q as CFDictionary)
    }
}

/// Thread-safe progress store. Mirror of the shared `LearnProgress`.
final class LearnProgressStore {
    static let key = "tlhf_learn_progress_v1"

    // MARK: - Levels (strategy ladder)

    /// Lesson ids in level order: finishing the first N unlocks Level N+1.
    static let levelOrder = ["coveredCall", "cashPut", "bullCall", "bearPut", "ironCondor", "leapsCall"]

    static let levelStrategyName: [String: String] = [
        "coveredCall": "Covered Call",
        "cashPut": "Cash-Secured Put",
        "bullCall": "Bull Call Spread",
        "bearPut": "Bear Put Spread",
        "ironCondor": "Iron Condor",
        "leapsCall": "LEAPS Call",
    ]

    private static let shared = LearnProgressStore()

    static func completedLessons() -> Set<String> { shared.completedLessons() }

    static func markLessonComplete(_ id: String) { shared.markLessonComplete(id) }

    /// 1 + the number of lessons completed in level order from the start,
    /// capped at 6.
    static func currentLevel() -> Int {
        let completed = completedLessons()
        var prefix = 0
        for id in levelOrder {
            guard completed.contains(id) else { break }
            prefix += 1
        }
        return min(6, 1 + prefix)
    }

    static func isUnlocked(_ lessonId: String) -> Bool {
        guard let index = levelOrder.firstIndex(of: lessonId) else { return false }
        return index < currentLevel()
    }

    static func canTradeMessage(level: Int) -> String {
        switch level {
        case 1: return "At Level 1 you can plan Covered Calls in the Trade tab."
        case 2: return "At Level 2 you can also plan Cash-Secured Puts in the Trade tab."
        case 3: return "At Level 3 you can also plan Bull Call Spreads in the Trade tab."
        case 4: return "At Level 4 you can also plan Bear Put Spreads in the Trade tab."
        case 5: return "At Level 5 you can also plan Iron Condors in the Trade tab."
        default: return "At Level 6 you can plan every strategy, including LEAPS Calls, in the Trade tab."
        }
    }

    // MARK: - Per-card marks

    static func recordCardMark(lessonId: String, front: String, correct: Bool) {
        shared.recordCardMark(key: cardKey(lessonId: lessonId, front: front), correct: correct)
    }

    static func cardMark(lessonId: String, front: String) -> Bool? {
        shared.cardMark(key: cardKey(lessonId: lessonId, front: front))
    }

    /// lessonId + "|" + a stable djb2 hash (hex) of the card front.
    /// String.hashValue is randomized per launch, so it can't key storage.
    static func cardKey(lessonId: String, front: String) -> String {
        lessonId + "|" + djb2Hex(front)
    }

    private static func djb2Hex(_ s: String) -> String {
        var hash: UInt64 = 5381
        for scalar in s.unicodeScalars {
            hash = hash &* 33 &+ UInt64(scalar.value)
        }
        return String(hash, radix: 16)
    }

    // MARK: - Instance API (completion, marks, deck runs, lesson answers)

    func completedLessons() -> Set<String> { Set(read().completed) }

    func markLessonComplete(_ id: String) {
        var file = read()
        guard !file.completed.contains(id) else { return }
        file.completed.append(id)
        write(file)
    }

    func recordCardMark(key: String, correct: Bool) {
        var file = read()
        file.marks[key] = correct
        write(file)
    }

    func cardMark(key: String) -> Bool? { read().marks[key] }

    private let lock = NSLock()
    private var cached: ProgressFile?
    private var loaded = false

    private func read() -> ProgressFile {
        lock.lock(); defer { lock.unlock() }
        if !loaded {
            if let data = LearnKeychain.load(),
               let file = try? JSONDecoder().decode(ProgressFile.self, from: data) {
                cached = file
            }
            loaded = true
        }
        return cached ?? ProgressFile()
    }

    private func write(_ file: ProgressFile) {
        lock.lock(); defer { lock.unlock() }
        cached = file
        loaded = true
        if let data = try? JSONEncoder().encode(file) {
            LearnKeychain.save(data)
        }
    }

    func deckProgress(_ deckId: String) -> DeckProgress? { read().decks[deckId] }

    func saveDeckProgress(_ progress: DeckProgress) {
        var file = read()
        file.decks[progress.deckId] = progress
        write(file)
    }

    func clearDeckProgress(_ deckId: String) {
        var file = read()
        file.decks.removeValue(forKey: deckId)
        write(file)
    }

    func lessonProgress(_ lessonId: String) -> LessonProgress? { read().lessons[lessonId] }

    /// Records one picked answer and returns the updated progress. `finished`
    /// flips to true exactly when `totalQuestions` is positive and every
    /// question index `0..<totalQuestions` has a recorded answer.
    @discardableResult
    func recordLessonAnswer(lessonId: String, questionIndex: Int, picked: Int, totalQuestions: Int) -> LessonProgress {
        var file = read()
        var answers = file.lessons[lessonId]?.answers ?? [:]
        answers[questionIndex] = picked
        let finished = totalQuestions > 0 && (0..<totalQuestions).allSatisfy { answers[$0] != nil }
        let updated = LessonProgress(lessonId: lessonId, answers: answers, finished: finished)
        file.lessons[lessonId] = updated
        write(file)
        return updated
    }

    func clearLessonProgress(_ lessonId: String) {
        var file = read()
        file.lessons.removeValue(forKey: lessonId)
        write(file)
    }

    func finishedDeckIds() -> Set<String> {
        Set(read().decks.values.filter { $0.finished }.map { $0.deckId })
    }

    func finishedLessonIds() -> Set<String> {
        Set(read().lessons.values.filter { $0.finished }.map { $0.lessonId })
    }

    func clearAll() {
        lock.lock()
        cached = nil
        loaded = false
        lock.unlock()
        LearnKeychain.remove()
    }
}
