import SwiftUI

/// AI Tutor: on-device chat with the downloaded Qwen model, plus the model
/// manager (download / load / unload / delete, Wi-Fi-only switch) and
/// encrypted per-session chat history (max 10 sessions × 40 messages).
struct AiTutorView: View {
    @ObservedObject var ai: AiController
    var lessons: [Lesson]
    var pendingQuestion: String?
    var onPendingConsumed: () -> Void = {}

    @State private var sessions: [ChatSession] = []
    @State private var activeId: String?
    @State private var input = ""
    @State private var streaming = false
    @State private var streamText = ""
    @State private var sendError: String?
    @State private var ticker = ""
    @State private var studyCards: [DynamicCard] = []
    @State private var studyBusy = false
    @State private var showSessions = false
    @State private var showModelManager = false

    private var active: ChatSession? { sessions.first { $0.id == activeId } }

    var body: some View {
        VStack(spacing: 0) {
            // Header: session switcher + model manager
            HStack {
                Button { showSessions = true } label: {
                    Label(active?.title ?? "New chat", systemImage: "line.3.horizontal")
                }.tint(.bronzeGold)
                Spacer()
                Button { showModelManager = true } label: {
                    Image(systemName: "cpu")
                }.tint(.bronzeGold)
            }
            .padding(.horizontal)

            if !ai.engineLoaded {
                Button("Load the on-device model to start chatting") { showModelManager = true }
                    .buttonStyle(.borderedProminent).tint(.bronzeGold)
                    .padding()
            }

            ScrollViewReader { proxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: 10) {
                        ForEach(active?.messages ?? []) { msg in
                            messageBubble(msg)
                        }
                        if streaming {
                            HStack {
                                Text(streamText.isEmpty ? "Thinking…" : streamText)
                                    .foregroundColor(.ink)
                                Spacer()
                            }
                            .padding(12).background(Color.navySurface).cornerRadius(10)
                            .id("stream")
                        }
                        if !studyCards.isEmpty {
                            Text("Study cards from the last answer").font(.headline).foregroundColor(.bronzeGold)
                            DynamicCardsView(cards: studyCards)
                        }
                    }
                    .padding()
                    .onChange(of: streamText) { proxy.scrollTo("stream", anchor: .bottom) }
                }
            }

            if let sendError { Text(sendError).foregroundColor(.bearRed).font(.caption).padding(.horizontal) }

            HStack(spacing: 8) {
                TextField("Ask about options…", text: $input, axis: .vertical)
                    .textFieldStyle(.roundedBorder)
                Button { send() } label: { Image(systemName: "arrow.up.circle.fill") }
                    .font(.title2).tint(.bronzeGold)
                    .disabled(streaming || input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            .padding()

            if let last = active?.messages.last, last.role == "assistant", !streaming {
                Button(studyBusy ? "Making cards…" : "Make study cards from this answer") {
                    makeStudyCards(from: last.text)
                }
                .buttonStyle(.bordered).tint(.bronzeGold)
                .disabled(studyBusy || !ai.engineLoaded)
                .padding(.bottom, 8)
            }
        }
        .background(Color.navyBg)
        .onAppear {
            refresh()
            if activeId == nil { newChat() }
            if let q = pendingQuestion, !q.isEmpty {
                input = q
                onPendingConsumed()
                send()
            }
        }
        .sheet(isPresented: $showSessions) { sessionsSheet }
        .sheet(isPresented: $showModelManager) { ModelManagerView(ai: ai) }
    }

    private func messageBubble(_ msg: ChatMessage) -> some View {
        HStack {
            if msg.role == "user" { Spacer() }
            Text(msg.text).foregroundColor(.ink)
                .padding(12)
                .background(msg.role == "user" ? Color.bronzeGold.opacity(0.25) : Color.navySurface)
                .cornerRadius(10)
            if msg.role == "assistant" { Spacer() }
        }
    }

    private func refresh() { sessions = ai.chat.sessions() }

    private func newChat() {
        let s = ai.chat.newSession()
        refresh()
        activeId = s.id
        studyCards = []
    }

    private func historyPrefix() -> String {
        let tail = (active?.messages.suffix(8) ?? []).map { "\($0.role): \($0.text)" }.joined(separator: "\n")
        return tail.isEmpty ? "" : "Earlier in this chat:\n\(tail)\n\n"
    }

    private func send() {
        let q = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty, !streaming, ai.engineLoaded else { return }
        if activeId == nil { newChat() }
        guard let id = activeId else { return }
        ai.chat.addMessage(sessionId: id, role: "user", text: q)
        refresh()
        input = ""
        studyCards = []
        sendError = nil
        streaming = true
        streamText = ""
        ai.touch()
        let context = AiPrompts.marketContext(ticker: ticker.isEmpty ? "—" : ticker, price: nil, iv: nil, days: nil)
        ai.engine.generate(
            system: AiPrompts.tutorSystem(),
            prompt: context + "\n\n" + historyPrefix() + "user: " + q,
            onToken: { piece in streamText += piece },
            onDone: { full, err in
                streaming = false
                if let err {
                    sendError = err.localizedDescription
                } else if let full, !full.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    ai.chat.addMessage(sessionId: id, role: "assistant", text: full.trimmingCharacters(in: .whitespacesAndNewlines))
                } else {
                    sendError = "The model returned an empty reply — try again."
                }
                streamText = ""
                refresh()
            }
        )
    }

    private func makeStudyCards(from answer: String) {
        guard ai.engineLoaded, !studyBusy else { return }
        studyBusy = true
        ai.touch()
        let topic = String(answer.prefix(120))
        let prompt = AiPrompts.studyDeck(topic: topic, lessonTitle: AiPrompts.matchLessonTitle(answer, lessons: lessons))
        ai.engine.generate(system: AiPrompts.tutorSystem(), prompt: prompt, onToken: { _ in }) { full, err in
            studyBusy = false
            if let full {
                let cards = parseAiCards(full, expected: 6)
                if cards.isEmpty { sendError = "Couldn't build cards from that answer — try again." }
                studyCards = cards
            } else if let err { sendError = err.localizedDescription }
        }
    }

    private var sessionsSheet: some View {
        NavigationStack {
            List {
                Button("＋ New chat") { newChat(); showSessions = false }
                ForEach(sessions) { s in
                    HStack {
                        Button { activeId = s.id; studyCards = []; showSessions = false } label: {
                            VStack(alignment: .leading) {
                                Text(s.title).foregroundColor(.ink)
                                Text("\(s.messages.count) messages").font(.caption).foregroundColor(.muted)
                            }
                        }
                        Spacer()
                        Button(role: .destructive) { ai.chat.deleteSession(s.id); refresh(); if activeId == s.id { newChat() } } label: {
                            Image(systemName: "trash")
                        }
                    }
                }
            }
            .navigationTitle("Chats")
        }
    }
}

/// Model manager: pick a model, download (resumable), load, unload, delete.
struct ModelManagerView: View {
    @ObservedObject var ai: AiController
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Everything runs on this phone. Models are downloaded once from the listed source, then work fully offline. Chat history is stored encrypted.")
                        .font(.caption).foregroundColor(.muted)

                    Toggle("Wi-Fi only downloads", isOn: $ai.wifiOnly)

                    ForEach(AiModel.all) { model in
                        VStack(alignment: .leading, spacing: 8) {
                            Text(model.displayName).font(.headline).foregroundColor(.ink)
                            Text("\(model.speedNote) · \(formatBytes(model.sizeBytes)) · \(model.ramNote)")
                                .font(.caption).foregroundColor(.muted)
                            modelRow(for: model)
                        }
                        .padding(12).background(Color.navySurface).cornerRadius(10)
                    }
                }
                .padding()
            }
            .background(Color.navyBg)
            .navigationTitle("On-device model")
            .toolbar { Button("Done") { dismiss() } }
        }
    }

    @ViewBuilder
    private func modelRow(for model: AiModel) -> some View {
        let downloaded = AiStore.exists(model)
        if ai.selectedModel.id != model.id {
            Button("Use this model") { ai.selectedModel = model }
                .buttonStyle(.bordered).tint(.bronzeGold)
        } else if !downloaded {
            let d = ai.downloader
            switch d.state {
            case .idle:
                Button("Download (\(formatBytes(model.sizeBytes)))") {
                    guard ai.canDownload else { return }
                    d.start(model: model)
                }
                .buttonStyle(.borderedProminent).tint(.bronzeGold)
                .disabled(!ai.canDownload)
                if !ai.canDownload { Text("Waiting for Wi-Fi…").font(.caption).foregroundColor(.muted) }
            case .downloading:
                ProgressView(value: d.progress) { Text("Downloading \(Int(d.progress * 100))%") }
                HStack {
                    Button("Pause") { d.pause() }.buttonStyle(.bordered)
                    Button("Cancel", role: .destructive) { d.cancel() }.buttonStyle(.bordered)
                }
            case .paused:
                ProgressView(value: d.progress) { Text("Paused at \(Int(d.progress * 100))%") }
                HStack {
                    Button("Resume") { d.resume() }.buttonStyle(.borderedProminent).tint(.bronzeGold)
                    Button("Cancel", role: .destructive) { d.cancel() }.buttonStyle(.bordered)
                }
            case .done:
                Text("Downloaded").foregroundColor(.bullGreen)
            case .failed(let why):
                Text("Download failed: \(why)").foregroundColor(.bearRed).font(.caption)
                Button("Retry") { d.start(model: model) }.buttonStyle(.bordered)
            }
        } else if !ai.engineLoaded || ai.engine.loadedModelId != model.id {
            Button("Load into memory") { ai.loadSelected() }.buttonStyle(.borderedProminent).tint(.bronzeGold)
            Button("Delete download", role: .destructive) { ai.downloader.delete(model: model) }.font(.caption)
            if let err = ai.loadError { Text(err).foregroundColor(.bearRed).font(.caption) }
        } else {
            Text("Loaded — ready to chat").foregroundColor(.bullGreen)
            HStack {
                Button("Unload") { ai.unloadEngine() }.buttonStyle(.bordered)
                Button("Delete download", role: .destructive) {
                    ai.unloadEngine(); ai.downloader.delete(model: model)
                }.buttonStyle(.bordered)
            }
        }
    }
}
