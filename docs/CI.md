# CI/CD — how this repo builds itself

Three GitHub Actions workflows live in `.github/workflows/`. Nothing here
needs accounts or secrets for the everyday checks; the release workflow is
manual and uses secrets only the repo owner adds.

## What runs on every push (and every pull request)

| Workflow | Runner | What it does |
| --- | --- | --- |
| `ci.yml` | Ubuntu | Sets up JDK 21 (Temurin), caches Gradle, runs the shared Kotlin Multiplatform tests (`:shared:jvmTest`), then builds the Android debug APK (`:androidApp:assembleDebug`). Uploads the APK and the shared test reports as run artifacts. |
| `ios.yml` | macOS | **Build-only.** Compiles the SwiftUI app with `xcodebuild` and signing disabled (`CODE_SIGNING_ALLOWED=NO`) so Swift breakage is caught on every push. It never signs, archives, or uploads anything. |

A green run means: all shared tests pass, the Android app builds, and the
iOS app compiles. Download the debug APK from the run's **Artifacts**
section to test that exact commit on a phone.

## How to read a failure

1. Open the failed run in the **Actions** tab and pick the red job.
2. The failing step is expanded in the log. Gradle failures name the task
   (e.g. `:shared:jvmTest FAILED`) and the first error is usually the real
   one — scroll to it rather than reading the stack trace tail.
3. For test failures, download the **shared-test-reports** artifact: the
   HTML report under `reports/tests` lists each failing test and message,
   and the XML files under `test-results` have the full detail.
4. iOS failures are compile errors in the log (file + line). Remember the
   llama.cpp note in `ios.yml`: until that framework is vendored into the
   repo, a link error there is expected, not a regression.

## Branch protection (recommended for `main`)

In **Settings → Branches → Add rule** for `main`:

- Require a pull request before merging.
- Require status checks to pass: **Shared tests + Android debug build**
  (from `ci.yml`) and **Build iOS app (unsigned)** (from `ios.yml`).
- Require branches to be up to date before merging.

That makes "CI green" the gate for everything that lands on `main`.

## Release path

Releases are manual and owner-run:

1. Bump `versionCode` / `versionName` in `androidApp/build.gradle.kts`,
   commit, and tag the commit (e.g. `v5.2.0`).
2. **Android:** Actions → **Release (manual)** → Run workflow. It needs
   four secrets (names only — see the header of `release.yml`):
   `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
   `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Download the signed
   **app-release-aab** artifact.
3. Upload the AAB in Play Console: **internal testing track** first,
   test it, then promote the same release to **production**. (Automating
   the upload later needs a Play Console service-account JSON — the
   `release.yml` header explains the `r0adkll/upload-google-play` option.)
4. **iOS:** build/archive in Xcode on a Mac with your Apple Developer
   signing, upload via App Store Connect, run it through **TestFlight**,
   then submit for App Store review. (Automation later needs an App Store
   Connect API key — see `release.yml`.)

The debug APKs from `ci.yml` are for testing only — never submit a debug
build to a store.
