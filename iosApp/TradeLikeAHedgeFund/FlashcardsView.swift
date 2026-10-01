import SwiftUI

/// Static 54-card flashcard system (mirrors shared/.../ai/FlashDecks.kt):
/// one mixed deck (30 guess-and-correct + 24 info) plus one deck per lesson,
/// shuffled every run, best score kept on-device in UserDefaults.
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

struct FlashcardsView: View {
    var lessons: [Lesson]
    var onReviewMistakes: (String) -> Void

    @State private var deck: FlashDeck?
    @State private var decks: [FlashDeck] = []

    var body: some View {
        Group {
            if let deck { DeckRunnerView(deck: deck, onExit: { self.deck = nil }, onReviewMistakes: onReviewMistakes) }
            else { deckPicker }
        }
        .background(Color.navyBg)
        .onAppear { if decks.isEmpty { decks = buildFlashDecks(lessons: lessons) } }
    }

    private var deckPicker: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                Text("Flashcards").font(.title2).bold().foregroundColor(.ink)
                Text("Shuffled every run · best score saved on this phone")
                    .font(.caption).foregroundColor(.muted)
                ForEach(decks) { d in
                    Button { deck = d } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(d.title).font(.headline).foregroundColor(.bronzeGold)
                                Text("\(d.cards.count) cards · \(d.guessCount) quiz")
                                    .font(.caption).foregroundColor(.muted)
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
            .padding()
        }
    }
}

private struct DeckRunnerView: View {
    let deck: FlashDeck
    var onExit: () -> Void
    var onReviewMistakes: (String) -> Void

    @State private var order: [FlashCard] = []
    @State private var index = 0
    @State private var flipped = false
    @State private var picked: Int?
    @State private var correct = 0
    @State private var answered = 0
    @State private var missed: [String] = []
    @State private var done = false

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
        .onAppear { order = deck.cards.shuffled() }
    }

    private func next() {
        flipped = false; picked = nil
        if index + 1 >= order.count {
            done = true
            FlashScores.record(deck.id, correct: correct, total: answered)
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
                    order = deck.cards.shuffled(); index = 0; correct = 0
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
