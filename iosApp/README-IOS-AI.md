# iOS on-device AI — one-time setup

The iOS app ships with everything for the AI Tutor except the llama.cpp
inference library itself (a C++ dependency that must be added in Xcode;
it cannot be vendored from Linux). This is a one-time, ~5 minute step.

## 1. Add llama.cpp to the Xcode project

Option A — Swift Package (recommended):
1. In Xcode: **File → Add Package Dependencies…**
2. Enter `https://github.com/ggerganov/llama.cpp`
3. Add the `llama` product to the **TradeLikeAHedgeFund** target.

Option B — prebuilt XCFramework: build it once with
`./build-xcframework.sh` from the llama.cpp repo (needs CMake), then drag
the resulting `llama.xcframework` into the project and link it.

Either way, `canImport(llama)` in `LlamaEngine.swift` becomes true and the
real inference path compiles in. Until then the app builds and runs fine —
the AI screens show an honest "llama.cpp isn't linked yet" message instead
of crashing.

## 2. Where models live

Downloaded GGUF files go to the app's private sandbox:

```
~/Library/Application Support/tlhf/ai/*.gguf
```

(`AiStore.modelsDir()` in `AiModels.swift`). Nothing leaves the phone:
no analytics, no network calls except the one-time model download from the
URL shown in the model picker.

## 3. What's already wired

- `AiModels.swift` — bundled catalog (Faster 0.5B / Balanced 1.5B, Q4_K_M).
  No remote catalog fetch, by design.
- `ModelDownloader.swift` — resumable downloads with pause/resume/cancel,
  progress, and delete.
- `LlamaEngine.swift` — llama.cpp wrapper: max 2048 tokens, streaming
  generation, single-generation lock, explicit unload.
- `AiChatStore.swift` — chat history in the iOS Keychain
  (`tlhf_ai_sessions_v1` schema, max 10 sessions × 40 messages).
- `AiController.swift` — one shared engine for the Tutor + Topics tabs,
  Wi-Fi-only switch, 30-minute idle unload.
- `AiTutorView.swift` — chat UI + model manager.
- `AiTopicsView.swift` — 5 dynamic flashcards per ticker + strategy.
- `FlashcardsView.swift` — static 54-card deck (30 quiz + 24 info),
  shuffled runs, best scores in UserDefaults, "review my mistakes" hands
  the missed questions to the tutor.
- `LearnView.swift` — Learn tab now has Lessons | Flashcards | AI Topics |
  AI Tutor sub-tabs.

All ten files are already registered in
`TradeLikeAHedgeFund.xcodeproj/project.pbxproj` — just open the project
and build after step 1.
