import SwiftUI

/// Learn tab: Lessons | Flashcards | AI Tutor.
/// One shared AiController owns the single llama.cpp engine; the AI Tutor
/// view borrows it (two loaded models would blow the RAM budget).
struct LearnView: View {
    @StateObject private var ai = AiController()
    @State private var lessons: [Lesson] = []
    @State private var selected: Lesson?
    @State private var tab = 0
    @State private var tutorPending: String?
    /// Bumped whenever lesson progress changes so the lessons list — which
    /// reads progress from the store, not @Published state — re-renders.
    @State private var progressTick = 0
    @State private var completedLessons: Set<String> = []
    private let progressStore = LearnProgressStore()
    /// Question handed off from the Trade tab ("Ask tutor about this trade").
    var externalQuestion: String?
    var onExternalConsumed: () -> Void = {}
    private let tabs = ["Lessons", "Flashcards", "AI Tutor"]

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
                        tutorPending = prompt; tab = 2
                    }
                case 2:
                    AiTutorView(ai: ai, lessons: lessons,
                               pendingQuestion: tutorPending,
                               onPendingConsumed: { tutorPending = nil })
                default:
                    lessonsView
                }
            }
            .background(Color.navyBg)
            .navigationTitle("Learn")
            .onAppear {
                load()
                completedLessons = LearnProgressStore.completedLessons()
            }
            .onChange(of: externalQuestion) { q in
                if let q {
                    tutorPending = q
                    tab = 2
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
        return Button { tab = 2 } label: {
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
        // Reading progressTick here makes SwiftUI re-evaluate this view when
        // a lesson is completed from the detail screen.
        let _ = progressTick
        let level = LearnProgressStore.currentLevel()
        return ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let lesson = selected {
                    LessonDetailView(lesson: lesson,
                                     onBack: { selected = nil },
                                     onComplete: { id in
                                         LearnProgressStore.markLessonComplete(id)
                                         completedLessons = LearnProgressStore.completedLessons()
                                         progressTick += 1
                                     })
                } else {
                    levelBanner(level: level)
                    ForEach(lessons) { lesson in
                        lessonRow(lesson, level: level)
                    }
                }
            }
            .padding()
        }
    }

    private func levelBanner(level: Int) -> some View {
        let strategyName = LearnProgressStore.levelStrategyName[LearnProgressStore.levelOrder[level - 1]] ?? ""
        return VStack(alignment: .leading, spacing: 4) {
            Text("Level \(level) — \(strategyName)")
                .font(.headline).foregroundColor(.bronzeGold)
            Text(LearnProgressStore.canTradeMessage(level: level))
                .font(.body).foregroundColor(.ink)
            if level < 6 {
                let nextId = LearnProgressStore.levelOrder[level - 1]
                let nextTitle = lessons.first { $0.id == nextId }?.title
                    ?? LearnProgressStore.levelStrategyName[nextId] ?? nextId
                Text("Next: finish '\(nextTitle)' to reach Level \(level + 1).")
                    .font(.caption).foregroundColor(.muted)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color.navySurface)
        .cornerRadius(10)
    }

    @ViewBuilder
    private func lessonRow(_ lesson: Lesson, level: Int) -> some View {
        let isCompleted = completedLessons.contains(lesson.id)
        if LearnProgressStore.isUnlocked(lesson.id) {
            Button { selected = lesson } label: {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Text(lesson.title).font(.headline).foregroundColor(.bronzeGold)
                        if isCompleted {
                            Text("✓ Completed").font(.caption).foregroundColor(.bullGreen)
                        }
                    }
                    Text(lesson.tagline).font(.body).foregroundColor(.muted)
                    if !isCompleted, let lp = progressStore.lessonProgress(lesson.id), !lp.answers.isEmpty {
                        Text("In progress — \(lp.answers.count) answered")
                            .font(.caption).foregroundColor(.bronzeGold)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(16)
                .background(Color.navySurface)
                .cornerRadius(10)
            }
        } else {
            VStack(alignment: .leading, spacing: 4) {
                Text(lesson.title).font(.headline).foregroundColor(.muted)
                Text(lesson.tagline).font(.body).foregroundColor(.muted)
                Text("Locked — finish the previous lesson to unlock.")
                    .font(.caption).foregroundColor(.muted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Color.navySurface)
            .cornerRadius(10)
        }
    }

    private func load() {
        guard lessons.isEmpty,
              let url = Bundle.main.url(forResource: "Lessons", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let decoded = try? JSONDecoder().decode([Lesson].self, from: data) else { return }
        lessons = decoded
    }
}

/// One lesson's full content: learn bullets, tip, and its quiz cards.
/// The lesson is complete when every quiz question has been answered
/// correctly at least once (retries count — correct picks latch by index).
struct LessonDetailView: View {
    let lesson: Lesson
    var onBack: () -> Void
    var onComplete: (String) -> Void

    @State private var correctLatch: Set<Int> = []
    @State private var completedFired = false
    private let progressStore = LearnProgressStore()

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Button("← All lessons") { onBack() }.tint(.bronzeGold)
            Text(lesson.title).font(.title2).bold().foregroundColor(.ink)
            Text(lesson.tagline).font(.headline).foregroundColor(.bronzeGold)
            if progressStore.lessonProgress(lesson.id)?.finished == true {
                Text("✓ Lesson finished — you answered every quiz question.")
                    .foregroundColor(.bullGreen)
                    .padding(12).background(Color.bullGreen.opacity(0.15)).cornerRadius(8)
            }
            ForEach(lesson.learn, id: \.self) { bullet in
                Text("• \(bullet)").foregroundColor(.ink)
                    .padding(12).background(Color.navySurface).cornerRadius(8)
            }
            Text("💡 Try it: \(lesson.tip)").foregroundColor(.ink)
                .padding(12).background(Color.navySurface).cornerRadius(8)
            Text("Quiz").font(.headline).foregroundColor(.bronzeGold)
            ForEach(Array((lesson.quiz + lesson.extraQuiz).enumerated()), id: \.element.id) { i, q in
                QuizCardView(
                    question: q,
                    index: i,
                    initialPicked: progressStore.lessonProgress(lesson.id)?.answers[i],
                    onAnswered: { correct in
                        if correct {
                            correctLatch.insert(i)
                            checkComplete()
                        }
                    },
                    onPick: { pick in
                        progressStore.recordLessonAnswer(
                            lessonId: lesson.id, questionIndex: i, picked: pick,
                            totalQuestions: (lesson.quiz + lesson.extraQuiz).count)
                    })
            }
            if completedFired {
                Text("Lesson complete — check the Lessons list for your new level.")
                    .font(.body).foregroundColor(.bullGreen)
            }
        }
        .onAppear {
            // Restore a half-finished lesson: answers saved by an earlier
            // visit seed the correct latch (each card restores its own pick
            // via initialPicked), so resuming can still complete the lesson.
            let saved = progressStore.lessonProgress(lesson.id)?.answers ?? [:]
            let qs = lesson.quiz + lesson.extraQuiz
            for (qi, pick) in saved where qi < qs.count && qs[qi].answerIndex == pick {
                correctLatch.insert(qi)
            }
            checkComplete()
        }
    }

    private func checkComplete() {
        let total = (lesson.quiz + lesson.extraQuiz).count
        guard total > 0, correctLatch.count >= total, !completedFired else { return }
        completedFired = true
        onComplete(lesson.id)
    }
}

struct QuizCardView: View {
    let question: QuizQuestion
    let index: Int
    var onAnswered: ((Bool) -> Void)? = nil
    var onPick: ((Int) -> Void)? = nil
    @State private var picked: Int?

    init(question: QuizQuestion, index: Int, initialPicked: Int? = nil,
         onAnswered: ((Bool) -> Void)? = nil, onPick: ((Int) -> Void)? = nil) {
        self.question = question
        self.index = index
        self.onAnswered = onAnswered
        self.onPick = onPick
        _picked = State(initialValue: initialPicked)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Q\(index + 1). \(question.question)").font(.body).foregroundColor(.ink)
            ForEach(question.choices.indices, id: \.self) { ci in
                Button {
                    if picked == nil {
                        picked = ci
                        onPick?(ci)
                        onAnswered?(ci == question.answerIndex)
                    }
                } label: {
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
