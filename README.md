# Trade Like a Hedge Fund — native rebuild

Two native UIs over **one** shared backend, matching the top-apps pattern:

| Part | Tech | Location |
|---|---|---|
| Shared backend | Kotlin Multiplatform (`:shared`) | `shared/` |
| Android UI | Jetpack Compose + Material3 | `androidApp/` |
| iOS UI | SwiftUI (iOS 17+, Swift 5.9) | `iosApp/` |

## What lives in the shared backend (`:shared`)

- `pricing/BlackScholes.kt` — Black-Scholes price + Greeks (delta, gamma, theta, vega, rho), implied vol by bisection. Pure Kotlin, no platform deps.
- `strategies/Strategies.kt` — `analyze()` for 9 strategy types: max profit/loss, breakevens, margin, per-leg plain-English risk text. **Money convention:** per-share P&L summed first, ×100 exactly once (the v3.6 100×-off bug class is covered by `StrategiesTest.ironCondorMathIsPerShareTimes100Not100xOff`).
- `data/MarketData.kt` — `Quote`, `OptionChain`, `Greeks`, `DataResult` (provider-isolated errors).
- `data/EtradeAuth.kt` — E*TRADE OAuth 1.0a signing: pure-Kotlin SHA-1/HMAC-SHA1, signature base string, `Authorization` header builder. Unit-tested against known vectors.
- `data/TradierClient.kt` — Ktor quote + option chains, explicit prod/sandbox host toggle (a production token against the wrong host was the classic failure).
- `data/YahooClient.kt` — Yahoo chart API with query1→query2 fallback; the cookie+crumb handshake quirk is documented in a TODO.
- `learn/LearnContent.kt` — all 6 lessons + 30 quiz questions ported from the web app's `LESSONS`/`FLASH_EXTRA` data (HTML stripped to plain text, answer indices preserved), plus a `buildFlashcards()` helper.

## Build & test

**Verified 2026-09-27 PDT in the Linux sandbox:** `:shared:jvmTest` — all 14
unit tests pass; `:androidApp:assembleDebug` — APK builds successfully.

### Shared + Android

```bash
cd native
echo "sdk.dir=$ANDROID_HOME" > local.properties   # point at your Android SDK
gradle :shared:jvmTest            # shared-backend unit tests
gradle :androidApp:assembleDebug  # APK -> androidApp/build/outputs/apk/debug/
```

Plugin markers resolve via `pluginManagement` in `settings.gradle.kts`
(Google Maven + Maven Central — the Gradle plugin portal is not used).

Pinned versions (all confirmed downloadable on 2026-09-28): Kotlin 2.4.20,
Ktor 2.3.13, AGP 8.7.3, Compose BOM 2025.10.00, Firebase BOM 34.19.0,
kotlinx-coroutines 1.10.2, kotlinx-serialization-json 1.9.0.

### iOS (macOS + Xcode only — cannot build in this Linux sandbox)

```bash
open iosApp/TradeLikeAHedgeFund.xcodeproj   # hand-authored, 10 Swift files + Lessons.json
```

The SwiftUI app **compiles standalone today**: `PricingEngine.swift` is a
pure-Swift mirror of the KMP math and the views talk to it through the
`PricingService` protocol. To switch the iOS app onto the real shared core:

1. On your Mac: `./gradlew :shared:assembleXCFramework`
2. Drag `shared.xcframework` into the Xcode target's Frameworks
3. Swap `LocalPricingService` for the `KmpPricingService` sketch in `SharedBridge.swift` (views don't change)

Sign the app with your own Apple Developer team (CODE_SIGN_STYLE is Automatic; no provisioning profiles are bundled).

## Secrets

There are **none** in this tree — no `google-services.json`, no
`GoogleService-Info.plist`, no keystores, no API keys/tokens. Enabling
Firebase Auth is a documented manual step:

- **Android:** download `google-services.json` into `androidApp/`, apply the `com.google.gms.google-services` plugin in `androidApp/build.gradle.kts`, add your SHA-1 in the Firebase console. Until then `AccountScreen` shows an honest "not configured" state (the plugin is deliberately *not* applied so the app compiles without the file).
- **iOS:** add `GoogleService-Info.plist` to the Xcode target and flip `firebaseReady` in `AccountView.swift`.

## Honest status

- **Android: verified.** `:shared:jvmTest` — 14/14 tests pass (Black-Scholes known values, put-call parity, iron-condor $100/$400 math, covered-call cap, HMAC-SHA1 RFC vector). `:androidApp:assembleDebug` — builds a working APK (`com.tradelikeahedgefund.app`, v5.0.0, minSdk 26, targetSdk 35).
- **iOS: not compiled.** No Xcode in this Linux sandbox. The project is hand-authored and statically checked (10 Swift files, all brace-balanced; all pbxproj object IDs resolve; Lessons.json bundled). It compiles standalone via `PricingEngine.swift`; plug in the KMP XCFramework on a Mac per the steps above. Treat the Swift pricing file as the test oracle until then.
- Stubs (render empty/honest states, never invented data): `PortfolioScreen` holdings storage + broker sync, Google sign-in button, Yahoo cookie+crumb handshake retry, E*TRADE full OAuth connect flow UI.
