# Trade Like a Hedge Fund — Native KMP v5.1.0

Mobile options strategy builder with on-device AI tutor, live ticker autocomplete, portfolio tracking, and 100% automated testing coverage across Compose, SwiftUI, and shared Kotlin Multiplatform logic.

## Architecture

- **Shared Backend (`shared/`)**: Pure Kotlin Multiplatform (KMP) logic for Black-Scholes options pricing, strategy analysis, portfolio calculations, and on-device AI chat stores.
- **Android App (`androidApp/`)**: 100% Jetpack Compose UI with MediaPipe LLM Inference engine.
- **iOS App (`iosApp/`)**: 100% SwiftUI with LlamaEngine.

## Automated Testing Suite

Comprehensive 3-tier testing framework included:
- **Unit Tests**: Coverage for pricing, strategies, portfolio math, ticker autocomplete, and AI chat prompts (`./gradlew test`).
- **Integration Tests**: End-to-end data pipeline tests (`androidApp/src/test/java/com/tradelikeahedgefund/app/IntegrationTest.kt`).
- **UI Automation Tests**: Compose UI tests for screens and ticker suggestion dropdowns (`androidApp/src/androidTest/java/com/tradelikeahedgefund/app/PortfolioUiTest.kt`).
