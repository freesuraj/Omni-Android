# Omni Android

Native Android build of Omni using Jetpack Compose.

## Read first
- Session memory and current delivery status: `docs/codex-memory.md`
- Agent workflow and guardrails: `AGENTS.md`

## Architecture foundation
- App shell with multi-module boundaries.
- Navigation graph with placeholder destinations for parity flows.
- Hilt-enabled application bootstrap.
- Core modules: `core:designsystem`, `core:model`, `core:data`.
- Feature modules under `feature:*`.

## Required references
- UI parity source: `assets/ios-reference-screenshots/**`
- Data parity source: `references/ios-swiftdata-to-android-model.md`

## Local validation
```bash
./gradlew :app:lintDebug :app:assembleDebug :app:testDebugUnitTest
```

## Omni API key (build-time)
- Omni provider key is injected at build/package time only.
- Provide `OMNI_GEMINI_API_KEY` as:
  - environment variable, or
  - Gradle property `-POMNI_GEMINI_API_KEY=...`
- Example:
```bash
OMNI_GEMINI_API_KEY=your_key_here ./gradlew :app:assembleDebug
```

## Firebase monitoring (Analytics + Crashlytics)
- Add Firebase Android app in Firebase Console using package id `com.suraj.apps.Doc2Quiz`.
- Download `google-services.json` and place it at `app/google-services.json` (gitignored).
- Once present, Gradle automatically enables:
  - `com.google.gms.google-services`
  - `com.google.firebase.crashlytics`
- Included runtime SDKs:
  - Firebase Analytics
  - Firebase Crashlytics
- Release builds generate native symbol tables (`ndk.debugSymbolLevel=SYMBOL_TABLE`) to improve native crash/ANR debugging.

## PR screenshot evidence (local emulator)
- CI no longer generates PR screenshots.
- For each PR, capture screenshots from your local emulator for the affected flow(s).
- Recommended command:
```bash
bundle exec fastlane android screenshots_pr pr:<PR_NUMBER>
```
- Upload images from `artifacts/pr/<PR_NUMBER>/screenshots` to the PR body or a PR comment.
