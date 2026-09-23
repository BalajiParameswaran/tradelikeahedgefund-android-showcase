# Trade Like a Hedge Fund — Android app

Options-trading companion app (Capacitor + single-file web UI). v4.9.1.

## Layout

- `android/` — the Android Studio / Gradle project. Open **this folder** in Android Studio.
- `web/` — the web UI source of truth: `app.html` (the whole app), `index.html`,
  images, `models.json` (bundled on-device AI model catalog — no downloads).
- `android/app/src/main/assets/public/` — what actually ships inside the APK.

## Build on a Mac (Android Studio)

1. Install **JDK 21** (e.g. `brew install openjdk@21`) and **Android Studio**
   (its installer brings the Android SDK).
2. In `android/`, create `local.properties` with one line:
   `sdk.dir=/Users/<you>/Library/Android/sdk`
3. Open the `android/` folder in Android Studio, let it sync, then Run —
   or `./gradlew assembleDebug` from a terminal inside `android/`.
4. APK: `android/app/build/outputs/apk/debug/app-debug.apk`.

No Firebase setup needed: `google-services.json` is already in `app/`.

## Editing the app (your single-file flow)

1. Edit `web/app.html` (ask Gemini in Android Studio to help).
2. Copy it over `android/app/src/main/assets/public/app.html`
   (same for `index.html` or images if you change those).
3. Rebuild. That's it — the whole UI is that one file.

## Signing

`android/keystore/debug.keystore` (password `android`) is checked in on purpose:
every machine that builds this repo produces the **same signature**, so new APKs
install as updates over old ones, and Google Sign-In keeps working because the
SHA-1 (`5E:B2:46:52:B8:1B:0F:C9:18:38:C5:44:C1:81:5B:3B:EF:EE:37:3B`) is already
registered in Firebase. This is a **debug** key — never publish to the Play
Store with it.

## Versioning

Bump `versionCode` / `versionName` in `android/app/build.gradle` for each
release you install on your phone (Android requires a higher `versionCode`
to update).
