# On-device AI module — patch contents

Unzip at the **repository root** (the folder that contains `androidApp/`,
`iosApp/`, `shared/`). All paths below are repo-root-relative.

## New files

Shared KMP (`shared/src/commonMain/kotlin/com/tlhf/shared/ai/`):
- `AiModels.kt` — bundled model catalog (Faster 0.5B / Balanced 1.5B)
- `AiChat.kt` — encrypted chat sessions, 10 sessions x 40 messages
- `AiDownload.kt` — download state, retry/backoff, 30-min idle unload
- `LlmEngine.kt` — platform engine interface
- `AiPrompts.kt` — tutor/topic/study/mistake prompts + card parser
- `FlashDecks.kt` — static 54-card deck, shuffling, best scores

Shared tests (`shared/src/commonTest/kotlin/com/tlhf/shared/ai/`):
- `AiChatTest.kt`, `AiPromptsTest.kt`, `AiDownloadTest.kt`, `FlashDecksTest.kt`
  (40/40 tests pass: `./gradlew :shared:jvmTest`)

Android (`androidApp/src/main/java/com/tradelikeahedgefund/app/`):
- `ai/AiPlatform.kt`, `ai/ModelDownloader.kt`, `ai/MediaPipeEngine.kt`
- `ui/AiTutorScreen.kt`, `ui/AiTopicsScreen.kt`, `ui/FlashcardsScreen.kt`

iOS (`iosApp/TradeLikeAHedgeFund/`):
- `AiModels.swift`, `AiPrompts.swift`, `AiChatStore.swift`,
  `ModelDownloader.swift`, `LlamaEngine.swift`, `AiController.swift`,
  `AiTutorView.swift`, `AiTopicsView.swift`, `FlashcardsView.swift`,
  `DynamicCardsView.swift`
- `../README-IOS-AI.md` — one-time llama.cpp Xcode setup (5 min)

## Modified files

- `androidApp/src/main/java/com/tradelikeahedgefund/app/ui/LearnScreen.kt`
  — Learn tab now has Lessons | Flashcards | AI Topics | AI Tutor sub-tabs
- `androidApp/build.gradle.kts`
  — adds `com.google.mediapipe:tasks-genai:0.10.35` and
    `androidx.security:security-crypto:1.1.0-alpha06`
- `iosApp/TradeLikeAHedgeFund/LearnView.swift` — same sub-tab structure
- `iosApp/TradeLikeAHedgeFund.xcodeproj/project.pbxproj`
  — registers the 10 new Swift files

## After applying

- Android: build normally (`assembleDebug` verified). No google-services.json
  needed for the AI screens.
- iOS: follow `iosApp/README-IOS-AI.md` step 1 (add llama.cpp), then build.

## Privacy notes

- The model catalog is bundled in the app — no remote fetch.
- Models download once from the URL shown in the picker, then run offline.
- Android: models in app-private `filesDir/ai/`; chats in EncryptedSharedPreferences.
- iOS: models in Application Support `tlhf/ai/`; chats in the Keychain.
