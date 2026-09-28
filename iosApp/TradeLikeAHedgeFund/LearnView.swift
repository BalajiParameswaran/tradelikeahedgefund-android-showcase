import SwiftUI

/// Learn tab: lessons decoded from the bundled Lessons.json
/// (ported from the web app's LESSONS + FLASH_EXTRA data).
struct LearnView: View {
    @State private var lessons: [Lesson] = []
    @State private var selected: Lesson?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let lesson = selected {
                        lessonDetail(lesson)
                    } else {
                        Text("Learn").font(.title2).bold().foregroundColor(.ink)
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
            .background(Color.navyBg)
            .navigationTitle("Learn")
            .onAppear { load() }
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
