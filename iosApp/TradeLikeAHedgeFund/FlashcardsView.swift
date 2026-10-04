import SwiftUI

/// Flashcard system (mirrors shared/.../ai/FlashDecks.kt): one mixed deck
/// (30 guess-and-correct + 24 info) plus one deck per lesson, shuffled every
/// run, best score kept on-device in UserDefaults. Lesson decks unlock with
/// Learn progress, and extra decks are built live from the user's portfolio
/// positions or any picked stock — numbers come only from the position/quote.
struct FlashCard: Identifiable {
    let id = UUID()
    let kind: Kind
    let front: String
    let back: String
    let choices: [String]
    let answerIndex: Int
    enum Kind { case guess, info }
}

struct FlashDeck: Identifiable {
    let id: String
    let title: String
    let cards: [FlashCard]
    var guessCount: Int { cards.filter { $0.kind == .guess }.count }
}

func buildFlashDecks(lessons: [Lesson]) -> [FlashDeck] {
    func guessCards(from questions: [QuizQuestion]) -> [FlashCard] {
        questions.map { q in FlashCard(kind: .guess, front: q.question, back: q.explanation,
                                      choices: q.choices, answerIndex: q.answerIndex) }
    }
    func infoCards(from bullets: [String]) -> [FlashCard] {
        bullets.map { b in FlashCard(kind: .info, front: b, back: b, choices: [], answerIndex: 0) }
    }
    var decks: [FlashDeck] = lessons.map { lesson in
        let cards = guessCards(from: lesson.quiz + lesson.extraQuiz)
            + infoCards(from: Array(lesson.learn.prefix(4)))
        return FlashDeck(id: "lesson-\(lesson.id)", title: lesson.title, cards: cards)
    }
    let mixed = decks.flatMap(\.cards).shuffled()
    let guess = mixed.filter { $0.kind == .guess }.prefix(30)
    let info = mixed.filter { $0.kind == .info }.prefix(24)
    decks.insert(FlashDeck(id: "mixed", title: "Mixed — all strategies",
                           cards: Array(guess) + Array(info)), at: 0)
    return decks
}

private enum FlashScores {
    static func bestLabel(_ deckId: String) -> String? {
        UserDefaults.standard.string(forKey: "tlhf_flash_best_\(deckId)")
    }
    static func record(_ deckId: String, correct: Int, total: Int) {
        UserDefaults.standard.set("\(correct)/\(total)", forKey: "tlhf_flash_best_\(deckId)")
    }
}

/// Deterministic shuffle (SplitMix64 + Fisher-Yates): the same seed always
/// reproduces the same order, so a saved deck run (LearnProgressStore)
/// resumes with exactly the card order it started with.
func seededShuffle<T>(_ items: [T], seed: Int64) -> [T] {
    var result = items
    guard result.count > 1 else { return result }
    var state = UInt64(bitPattern: seed)
    func nextRandom() -> UInt64 {
        state &+= 0x9E3779B97F4A7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
        z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
        return z ^ (z >> 31)
    }
    for i in stride(from: result.count - 1, through: 1, by: -1) {
        let j = Int(nextRandom() % UInt64(i + 1))
        result.swapAt(i, j)
    }
    return result
}

struct FlashcardsView: View {
    var lessons: [Lesson]
    var onReviewMistakes: (String) -> Void

    @State private var deck: FlashDeck?
    @State private var decks: [FlashDeck] = []
    private let progressStore = LearnProgressStore()

    /// Portfolio-backed decks: positions are read from the on-device store;
    /// quotes are fetched live (cards are never built on invented prices).
    @StateObject private var portfolio = PortfolioStore()
    @State private var posQuotes: [String: StockQuote] = [:]
    @State private var posLoaded = false
    @State private var stockInput = ""
    @State private var stockError: String?

    var body: some View {
        Group {
            if let deck { DeckRunnerView(deck: deck, onExit: { self.deck = nil }, onReviewMistakes: onReviewMistakes) }
            else { deckPicker }
        }
        .background(Color.navyBg)
        .onAppear { if decks.isEmpty { decks = buildFlashDecks(lessons: lessons) } }
        .task { await loadPositionQuotes() }
    }

    private func loadPositionQuotes() async {
        for p in portfolio.positions {
            if let q = try? await PortfolioEngine.fetchQuote(p.symbol) {
                posQuotes[p.symbol] = q
            }
        }
        posLoaded = true
    }

    private func lessonId(fromDeckId id: String) -> String? {
        id.hasPrefix("lesson-") ? String(id.dropFirst("lesson-".count)) : nil
    }

    /// "Marks: x/y correct" for lesson decks once any guess card has a mark.
    private func marksSummary(for d: FlashDeck, lessonId: String) -> String? {
        var correctCount = 0
        var recorded = 0
        for card in d.cards where card.kind == .guess {
            if let mark = LearnProgressStore.cardMark(lessonId: lessonId, front: card.front) {
                recorded += 1
                if mark { correctCount += 1 }
            }
        }
        return recorded > 0 ? "Marks: \(correctCount)/\(recorded) correct" : nil
    }

    private var deckPicker: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                Text("Flashcards").font(.title2).bold().foregroundColor(.ink)
                Text("Shuffled every run · best score saved on this phone")
                    .font(.caption).foregroundColor(.muted)

                portfolioSection
                stockPickerSection

                ForEach(decks) { d in
                    deckRow(d)
                }
            }
            .padding()
        }
    }

    @ViewBuilder
    private func deckRow(_ d: FlashDeck) -> some View {
        if let lid = lessonId(fromDeckId: d.id), !LearnProgressStore.isUnlocked(lid) {
            let levelIndex = LearnProgressStore.levelOrder.firstIndex(of: lid) ?? 0
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(d.title).font(.headline).foregroundColor(.muted)
                    Text("\(d.cards.count) cards · \(d.guessCount) quiz")
                        .font(.caption).foregroundColor(.muted)
                    Text("Locked — Level \(levelIndex + 1)")
                        .font(.caption).foregroundColor(.muted)
                }
                Spacer()
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14).background(Color.navySurface).cornerRadius(10)
        } else {
            Button { deck = d } label: {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(d.title).font(.headline).foregroundColor(.bronzeGold)
                        Text("\(d.cards.count) cards · \(d.guessCount) quiz")
                            .font(.caption).foregroundColor(.muted)
                        if let lid = lessonId(fromDeckId: d.id),
                           let summary = marksSummary(for: d, lessonId: lid) {
                            Text(summary).font(.caption).foregroundColor(.muted)
                        }
                        if let saved = progressStore.deckProgress(d.id), !saved.finished {
                            Text("In progress — card \(saved.index + 1) of \(saved.totalCards) · tap to continue")
                                .font(.caption).foregroundColor(.bronzeGold)
                        } else if progressStore.finishedDeckIds().contains(d.id) {
                            Text("Finished ✓").font(.caption).foregroundColor(.bullGreen)
                        }
                    }
                    Spacer()
                    if let best = FlashScores.bestLabel(d.id) {
                        Text("Best \(best)").font(.caption).foregroundColor(.bullGreen)
                    } else {
                        Text("Not tried").font(.caption).foregroundColor(.muted)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(14).background(Color.navySurface).cornerRadius(10)
            }
        }
    }

    private var portfolioSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("From your portfolio").font(.headline).foregroundColor(.bronzeGold)
            if portfolio.positions.isEmpty {
                Text("No positions yet. Add positions in the Portfolio tab and this section builds cards from them.")
                    .font(.caption).foregroundColor(.muted)
            } else {
                ForEach(portfolio.positions) { p in
                    if let q = posQuotes[p.symbol] {
                        Button { deck = buildPositionDeck(position: p, quote: q) } label: {
                            HStack {
                                Text("\(p.symbol) — your position")
                                    .font(.headline).foregroundColor(.bronzeGold)
                                Spacer()
                                Text("Build cards").font(.caption).foregroundColor(.muted)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(14).background(Color.navySurface).cornerRadius(10)
                        }
                    } else if posLoaded {
                        Text("\(p.symbol) — quote unavailable; cards need a live price.")
                            .font(.caption).foregroundColor(.bearRed)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(14).background(Color.navySurface).cornerRadius(10)
                    } else {
                        Text("\(p.symbol) — loading quote…")
                            .font(.caption).foregroundColor(.muted)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(14).background(Color.navySurface).cornerRadius(10)
                    }
                }
            }
        }
    }

    private var stockPickerSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Pick a stock").font(.headline).foregroundColor(.bronzeGold)
            HStack(spacing: 8) {
                TextField("Ticker, e.g. AAPL", text: $stockInput)
                    .textFieldStyle(.roundedBorder)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                Button("Build cards") { buildStock() }
                    .buttonStyle(.bordered).tint(.bronzeGold)
                    .disabled(stockInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            if let stockError {
                Text(stockError).font(.caption).foregroundColor(.bearRed)
            }
        }
    }

    private func buildStock() {
        let sym = stockInput.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard !sym.isEmpty else { return }
        stockError = nil
        Task { @MainActor in
            if let q = try? await PortfolioEngine.fetchQuote(sym) {
                deck = buildStockDeck(symbol: sym, quote: q)
            } else {
                stockError = "Couldn't get a price for \(sym) — check the ticker and your connection."
            }
        }
    }
}

// MARK: - Position / stock deck builders (every number from the position/quote)

private func fmt2(_ v: Double) -> String {
    let f = NumberFormatter()
    f.numberStyle = .currency
    f.currencyCode = "USD"
    f.minimumFractionDigits = 2
    f.maximumFractionDigits = 2
    return f.string(from: NSNumber(value: v)) ?? String(format: "$%.2f", v)
}

private func fmtShares(_ v: Double) -> String {
    v == v.rounded() ? String(Int(v)) : String(format: "%.2f", v)
}

/// Guess card from string choices: distractors are de-duplicated against the
/// correct answer, and the correct answer is placed at `seed % 4` so it is
/// not always first.
private func guessCardStrings(front: String, back: String, correct: String,
                              distractors: [String], seed: Int) -> FlashCard {
    var unique: [String] = []
    for d in distractors where d != correct && !unique.contains(d) {
        unique.append(d)
    }
    var n = 2
    while unique.count < 3 {
        let extra = "\(correct) (\(n))"
        if !unique.contains(extra) { unique.append(extra) }
        n += 1
    }
    var choices = Array(unique.prefix(3))
    let idx = ((seed % 4) + 4) % 4
    choices.insert(correct, at: min(idx, choices.count))
    return FlashCard(kind: .guess, front: front, back: back, choices: choices, answerIndex: idx)
}

private func guessCardMoney(front: String, back: String, correctValue: Double,
                            candidates: [Double], seed: Int) -> FlashCard {
    guessCardStrings(front: front, back: back, correct: fmt2(correctValue),
                     distractors: candidates.map { fmt2($0) }, seed: seed)
}

private func buildPositionDeck(position p: Position, quote q: StockQuote) -> FlashDeck {
    let price = q.price
    var cards: [FlashCard] = []

    let infoText = "Your \(p.symbol) position: \(fmtShares(p.shares)) shares at avg \(fmt2(p.avgCost)). Latest price \(fmt2(price))."
    cards.append(FlashCard(kind: .info, front: infoText, back: infoText, choices: [], answerIndex: 0))

    let unrealized = (price - p.avgCost) * p.shares
    cards.append(guessCardMoney(
        front: "What is the unrealized gain/loss on your \(p.symbol) position at \(fmt2(price))?",
        back: "(\(fmt2(price)) − \(fmt2(p.avgCost))) × \(fmtShares(p.shares)) shares = \(fmt2(unrealized)).",
        correctValue: unrealized,
        candidates: [-unrealized, unrealized / 2, unrealized + p.shares * 1.0],
        seed: p.symbol.count))

    if p.shares >= 100 {
        let contracts = Int(p.shares / 100)
        cards.append(guessCardStrings(
            front: "How many covered-call contracts can you write against \(fmtShares(p.shares)) shares of \(p.symbol)?",
            back: "One contract needs 100 shares, so \(fmtShares(p.shares)) shares ÷ 100 = \(contracts).",
            correct: "\(contracts)",
            distractors: ["\(contracts + 1)", "\(max(contracts - 1, 0))", "\(contracts * 10)"],
            seed: p.symbol.count + 1))
    } else {
        let text = "Covered calls need 100 shares per contract — you hold \(fmtShares(p.shares)) shares of \(p.symbol), so you can't write a covered call on this position yet."
        cards.append(FlashCard(kind: .info, front: text, back: text, choices: [], answerIndex: 0))
    }

    let positionValue = p.shares * price
    let moveGain = positionValue * 0.10
    cards.append(guessCardMoney(
        front: "If \(p.symbol) rises 10% from \(fmt2(price)), how much does your position value gain?",
        back: "Position value is \(fmtShares(p.shares)) × \(fmt2(price)) = \(fmt2(positionValue)); a 10% rise adds 10% of that = \(fmt2(moveGain)).",
        correctValue: moveGain,
        candidates: [moveGain / 2, moveGain * 2, price * 0.10],
        seed: p.symbol.count + 2))

    return FlashDeck(id: "position-\(p.symbol)", title: "\(p.symbol) — your position", cards: cards)
}

private func buildStockDeck(symbol: String, quote q: StockQuote) -> FlashDeck {
    var cards: [FlashCard] = []

    let infoText = "\(symbol) last price \(fmt2(q.price)). One options contract controls 100 shares = \(fmt2(q.price * 100)) at this price."
    cards.append(FlashCard(kind: .info, front: infoText, back: infoText, choices: [], answerIndex: 0))

    cards.append(guessCardMoney(
        front: "What would 100 shares of \(symbol) cost at \(fmt2(q.price)) a share?",
        back: "100 × \(fmt2(q.price)) = \(fmt2(q.price * 100)).",
        correctValue: q.price * 100,
        candidates: [q.price * 10, q.price * 50, q.price * 200],
        seed: symbol.count))

    cards.append(guessCardMoney(
        front: "If \(symbol) rises 5% from \(fmt2(q.price)), what is the new price?",
        back: "\(fmt2(q.price)) × 1.05 = \(fmt2(q.price * 1.05)).",
        correctValue: q.price * 1.05,
        candidates: [q.price * 0.95, q.price * 1.5, q.price + 5],
        seed: symbol.count + 1))

    return FlashDeck(id: "stock-\(symbol)", title: "\(symbol) — stock cards", cards: cards)
}

private struct DeckRunnerView: View {
    let deck: FlashDeck
    var onExit: () -> Void
    var onReviewMistakes: (String) -> Void

    private let progressStore = LearnProgressStore()
    @State private var seed: Int64 = 0
    @State private var order: [FlashCard] = []
    @State private var index = 0
    @State private var flipped = false
    @State private var picked: Int?
    @State private var correct = 0
    @State private var answered = 0
    @State private var missed: [String] = []
    @State private var done = false

    /// Built-in decks persist their run so it can resume; position/stock
    /// decks are rebuilt from live quotes on every visit, so a saved shuffle
    /// would resume against different cards — those always start fresh.
    private var persistable: Bool {
        !deck.id.hasPrefix("position-") && !deck.id.hasPrefix("stock-")
    }

    /// Key for per-card marks: the lesson id for lesson decks, otherwise the
    /// deck id itself ("mixed" marks are best-effort).
    private var deckLessonKey: String {
        deck.id.hasPrefix("lesson-") ? String(deck.id.dropFirst("lesson-".count)) : deck.id
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Button("← Decks", action: onExit).tint(.bronzeGold)
                Spacer()
                Text(deck.title).font(.headline).foregroundColor(.ink)
                Spacer()
            }
            .padding(.horizontal)

            if done {
                doneView
            } else if !order.isEmpty {
                let card = order[index]
                Text("Card \(index + 1) of \(order.count)").font(.caption).foregroundColor(.muted).padding(.horizontal)
                ProgressView(value: Double(index + 1) / Double(order.count)).padding(.horizontal)
                ScrollView {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(card.kind == .guess ? "Quiz — pick the answer" : "Info — tap to flip")
                            .font(.caption).foregroundColor(.bronzeGold)
                        Button {
                            if card.kind == .info { flipped.toggle() }
                        } label: {
                            Text(card.kind == .info && flipped ? card.back : card.front)
                                .font(.body).foregroundColor(.ink)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        if card.kind == .guess && picked == nil {
                            ForEach(card.choices.indices, id: \.self) { ci in
                                Button {
                                    picked = ci; answered += 1
                                    LearnProgressStore.recordCardMark(lessonId: deckLessonKey,
                                                                      front: card.front,
                                                                      correct: ci == card.answerIndex)
                                    if ci == card.answerIndex { correct += 1 }
                                    else { missed.append(card.front) }
                                } label: {
                                    Text(card.choices[ci]).foregroundColor(.ink)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .padding(10).background(Color.navySurface).cornerRadius(8)
                                }
                            }
                        }
                        if card.kind == .guess, let picked {
                            ForEach(card.choices.indices, id: \.self) { ci in
                                Text(card.choices[ci]).foregroundColor(.ink)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(10)
                                    .background(ci == card.answerIndex ? Color.bullGreen.opacity(0.25)
                                              : ci == picked ? Color.bearRed.opacity(0.25) : Color.navySurface)
                                    .cornerRadius(8)
                            }
                            Text(picked == card.answerIndex ? "Correct." : "Not quite.")
                                .foregroundColor(picked == card.answerIndex ? .bullGreen : .bearRed)
                            Text(card.back).font(.body).foregroundColor(.muted)
                        }
                    }
                    .padding(16).background(Color.navySurface).cornerRadius(10)
                    .padding(.horizontal)
                }
                let canNext = card.kind == .info || picked != nil
                Button(index + 1 >= order.count ? "See score" : "Next card") { next() }
                    .buttonStyle(.borderedProminent).tint(.bronzeGold)
                    .disabled(!canNext)
                    .frame(maxWidth: .infinity).padding(.horizontal)
            }
            Spacer()
        }
        .onAppear {
            guard order.isEmpty else { return }
            // Resume an unfinished run exactly where it stopped: the saved
            // seed reproduces the same shuffle. A saved entry whose card
            // count no longer matches the deck is ignored.
            if persistable, let saved = progressStore.deckProgress(deck.id), !saved.finished,
               saved.totalCards == deck.cards.count {
                seed = saved.seed
                order = seededShuffle(deck.cards, seed: saved.seed)
                index = saved.index; correct = saved.correct
                answered = saved.answered; missed = saved.missedFronts
            } else {
                seed = Int64(Date().timeIntervalSince1970 * 1000)
                order = seededShuffle(deck.cards, seed: seed)
            }
        }
    }

    private func next() {
        flipped = false; picked = nil
        let isLast = index + 1 >= order.count
        // Persist at every card boundary, mirroring the Android runner.
        if persistable {
            progressStore.saveDeckProgress(DeckProgress(
                deckId: deck.id, seed: seed, totalCards: order.count,
                index: isLast ? index : index + 1, correct: correct,
                answered: answered, missedFronts: missed, finished: isLast))
        }
        if isLast {
            done = true
            FlashScores.record(deck.id, correct: correct, total: answered)
            // Scoring 80%+ on a lesson deck also completes that lesson.
            if deck.id.hasPrefix("lesson-"), answered > 0, correct * 5 >= answered * 4 {
                LearnProgressStore.markLessonComplete(String(deck.id.dropFirst("lesson-".count)))
            }
        } else { index += 1 }
    }

    private var doneView: some View {
        VStack(spacing: 10) {
            let pct = answered > 0 ? correct * 100 / answered : 0
            Text("\(pct)%").font(.largeTitle).bold().foregroundColor(.bronzeGold)
            Text("You got \(correct) of \(answered) quiz cards right.").foregroundColor(.ink)
            Text("Best score saved on this phone.").font(.caption).foregroundColor(.muted)
            HStack {
                Button("Again") {
                    if persistable { progressStore.clearDeckProgress(deck.id) }
                    seed = Int64(Date().timeIntervalSince1970 * 1000)
                    order = seededShuffle(deck.cards, seed: seed); index = 0; correct = 0
                    answered = 0; missed = []; done = false
                }.buttonStyle(.borderedProminent).tint(.bronzeGold)
                Button("All decks", action: onExit).buttonStyle(.bordered)
            }
            if !missed.isEmpty {
                Button("Review my mistakes with the tutor →") {
                    onReviewMistakes(AiPrompts.reviewMistakes(deckTitle: deck.title, missed: missed))
                }
                .buttonStyle(.bordered).tint(.bronzeGold)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(16).background(Color.navySurface).cornerRadius(10)
        .padding(.horizontal)
    }
}
