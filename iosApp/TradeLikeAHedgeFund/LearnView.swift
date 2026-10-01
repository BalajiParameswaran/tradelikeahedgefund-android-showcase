import SwiftUI

/// Learn tab: Lessons | Flashcards | AI Topics | AI Tutor.
/// One shared AiController owns the single llama.cpp engine; the AI Tutor and
/// AI Topics views borrow it (two loaded models would blow the RAM budget).
struct LearnView: View {
    @StateObject private var ai = AiController()
    @State private var lessons: [Lesson] = []
    @State private var selected: Lesson?
    @State private var tab = 0
    @State private var tutorPending: String?
    /// Question handed off from the Trade tab ("Ask tutor about this trade").
    var externalQuestion: String?
    var onExternalConsumed: () -> Void = {}
    private let tabs = ["Lessons", "Flashcards", "AI Topics", "AI Tutor"]

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                tutorStatusPill
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(tabs.indices, id: \.self) { i in
                            Button(tabs[i]) { tab = i }
                                .font(.subheadline)
                                .foregroundColor(.ink)
                                .padding(.vertical, 8).padding(.horizontal, 12)
                                .background(tab == i ? Color.bronzeGold.opacity(0.25) : Color.navySurface)
                                .cornerRadius(8)
                        }
                    }
                    .padding(.horizontal)
                }
                .padding(.vertical, 8)

                switch tab {
                case 1:
                    FlashcardsView(lessons: lessons) { prompt in
                        tutorPending = prompt; tab = 3
                    }
                case 2:
                    AiTopicsView(ai: ai, lessons: lessons) { tab = 3 }
                case 3:
                    AiTutorView(ai: ai, lessons: lessons,
                               pendingQuestion: tutorPending,
                               onPendingConsumed: { tutorPending = nil })
                default:
                    lessonsView
                }
            }
            .background(Color.navyBg)
            .navigationTitle("Learn")
            .onAppear { load() }
            .onChange(of: externalQuestion) { q in
                if let q {
                    tutorPending = q
                    tab = 3
                    onExternalConsumed()
                }
            }
        }
    }

    /// Always-visible on-device tutor status: download / load / ready / error.
    private var tutorStatusPill: some View {
        let dl = ai.downloader
        let (label, color): (String, Color) = {
            if let err = ai.loadError { return ("Tutor error — tap to view", .bearRed) }
            switch dl.state {
            case .downloading:
                return ("Downloading tutor model \(Int(dl.progress * 100))%…", .bronzeGold)
            case .paused:
                return ("Model download paused — tap to resume", .bronzeGold)
            case .failed(let msg):
                return ("Download failed: \(msg)", .bearRed)
            default: break
            }
            if ai.engineLoaded { return ("Tutor ready — on-device", .bullGreen) }
            if AiStore.exists(ai.selectedModel) { return ("Model downloaded — tap to load tutor", .bronzeGold) }
            return ("Tutor model not downloaded — tap to set up", .muted)
        }()
        return Button { tab = 3 } label: {
            HStack(spacing: 8) {
                Circle().fill(color).frame(width: 10, height: 10)
                Text(label).font(.subheadline).foregroundColor(.ink)
                Spacer()
                Text("AI Tutor ›").font(.subheadline).foregroundColor(.bronzeGold)
            }
            .padding(.horizontal, 14).padding(.vertical, 8)
            .frame(maxWidth: .infinity)
            .background(Color.navySurface)
            .cornerRadius(20)
            .padding(.horizontal, 12).padding(.vertical, 4)
        }
    }

    private var lessonsView: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let lesson = selected {
                    lessonDetail(lesson)
                } else {
                    ForEach(lessons) { lesson in
                        Button { selected = lesson } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(lesson.title).font(.headline).foregroundColor(.bronzeGold)
                                Text(lesson.tagline).font(.body).foregroundColor(.muted)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(16)
                            .background(Color.navySurface)
                            .cornerRadius(10)
                        }
                    }
                }
            }
            .padding()
        }
    }

    private func load() {
        guard lessons.isEmpty,
              let url = Bundle.main.url(forResource: "Lessons", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let decoded = try? JSONDecoder().decode([Lesson].self, from: data) else { return }
        lessons = decoded
    }

    private func lessonDetail(_ lesson: Lesson) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Button("← All lessons") { selected = nil }.tint(.bronzeGold)
            Text(lesson.title).font(.title2).bold().foregroundColor(.ink)
            Text(lesson.tagline).font(.headline).foregroundColor(.bronzeGold)
            ForEach(lesson.learn, id: \.self) { bullet in
                Text("• \(bullet)").foregroundColor(.ink)
                    .padding(12).background(Color.navySurface).cornerRadius(8)
            }
            Text("💡 Try it: \(lesson.tip)").foregroundColor(.ink)
                .padding(12).background(Color.navySurface).cornerRadius(8)
            Text("Quiz").font(.headline).foregroundColor(.bronzeGold)
            ForEach(Array((lesson.quiz + lesson.extraQuiz).enumerated()), id: \.element.id) { i, q in
                QuizCardView(question: q, index: i)
            }
        }
    }
}

struct QuizCardView: View {
    let question: QuizQuestion
    let index: Int
    @State private var picked: Int?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Q\(index + 1). \(question.question)").font(.body).foregroundColor(.ink)
            ForEach(question.choices.indices, id: \.self) { ci in
                Button { if picked == nil { picked = ci } } label: {
                    Text(question.choices[ci])
                        .foregroundColor(.ink)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(10)
                        .background(
                            picked == nil ? Color.navySurface :
                            ci == question.answerIndex ? Color.bullGreen.opacity(0.25) :
                            ci == picked ? Color.bearRed.opacity(0.25) : Color.navySurface
                        )
                        .cornerRadius(8)
                }
            }
            if let picked {
                Text(picked == question.answerIndex ? "Correct. " : "Not quite. ")
                    .foregroundColor(picked == question.answerIndex ? .bullGreen : .bearRed)
                Text(question.explanation).font(.body).foregroundColor(.muted)
                Button("Retry") { self.picked = nil }.font(.caption).tint(.bronzeGold)
            }
        }
        .padding(12)
        .background(Color.navySurface)
        .cornerRadius(10)
    }
}
