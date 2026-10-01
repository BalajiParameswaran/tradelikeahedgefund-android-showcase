import SwiftUI

/// AI Topics: 5 dynamic flashcards generated on-device for a ticker + strategy.
/// Borrows the shared engine from AiController (owned by LearnView).
struct AiTopicsView: View {
    @ObservedObject var ai: AiController
    var lessons: [Lesson]
    var onGoToTutor: () -> Void

    @State private var ticker = "AAPL"
    @State private var strategy: String?
    @State private var cards: [DynamicCard] = []
    @State private var busy = false
    @State private var error: String?
    @State private var cacheKey = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("AI Topics").font(.title2).bold().foregroundColor(.ink)
                Text("Dynamic flashcards generated on your phone for any ticker + strategy.")
                    .font(.caption).foregroundColor(.muted)

                if !ai.engineLoaded {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Load the on-device model in the AI Tutor tab to unlock dynamic topics.")
                            .foregroundColor(.ink)
                        Button("Go to AI Tutor", action: onGoToTutor)
                            .buttonStyle(.borderedProminent).tint(.bronzeGold)
                    }
                    .padding(12).background(Color.navySurface).cornerRadius(10)
                } else {
                    HStack {
                        TextField("Ticker", text: $ticker)
                            .textFieldStyle(.roundedBorder)
                            .textInputAutocapitalization(.characters)
                            .frame(maxWidth: 120)
                        Button(busy ? "Building…" : "Generate") { generate() }
                            .buttonStyle(.borderedProminent).tint(.bronzeGold)
                            .disabled(busy)
                    }
                    Text("Strategy").font(.headline).foregroundColor(.bronzeGold)
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 140))], spacing: 8) {
                        ForEach(lessons) { lesson in
                            Button { strategy = lesson.title } label: {
                                Text(lesson.title).font(.caption).foregroundColor(.ink)
                                    .frame(maxWidth: .infinity).padding(.vertical, 8).padding(.horizontal, 6)
                                    .background(strategy == lesson.title ? Color.bronzeGold.opacity(0.25) : Color.navySurface)
                                    .cornerRadius(8)
                            }
                        }
                    }
                    if let error { Text(error).foregroundColor(.bearRed).font(.caption) }
                    if !cards.isEmpty {
                        HStack {
                            Text(cacheKey).font(.caption).foregroundColor(.muted)
                            Spacer()
                            Button("Regenerate") { generate() }.font(.caption).tint(.bronzeGold).disabled(busy)
                        }
                        DynamicCardsView(cards: cards)
                    }
                }
            }
            .padding()
        }
        .background(Color.navyBg)
        .onAppear { if strategy == nil { strategy = lessons.first?.title } }
    }

    private func generate() {
        guard !busy, ai.engineLoaded else { return }
        busy = true
        error = nil
        let t = ticker.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let s = strategy ?? lessons.first?.title ?? "Covered Call"
        ai.touch()
        ai.engine.generate(system: AiPrompts.tutorSystem(), prompt: AiPrompts.aiTopic(ticker: t.isEmpty ? "AAPL" : t, strategy: s), onToken: { _ in }) { full, err in
            busy = false
            if let err { error = "Couldn't build cards: \(err.localizedDescription)" }
            else if let full {
                let parsed = parseAiCards(full, expected: 5)
                if parsed.isEmpty { error = "The model returned no usable cards — try again." }
                else { cards = parsed; cacheKey = "\(t.isEmpty ? "AAPL" : t) · \(s)" }
            }
        }
    }
}
