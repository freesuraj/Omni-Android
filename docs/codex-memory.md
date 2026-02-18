# Codex Working Memory (Omni-Android)

Last updated: 2026-02-18
Branch snapshot: `fix/transcription-unavailable-state` (clean working tree, **ahead 1** vs origin due local revert)

## Project Identity
- Repo: `freesuraj/Omni-Android`
- Local path: `/Users/suraj/Projects/Personal/Omni-Android`
- App: Omni Android (Jetpack Compose)
- Product parity target: Omni iOS behavior/screens

## Current High-Priority Situation
- User-reported issue remains: live audio transcription not reliably working on real device (Oppo Android 16), dashboard often shows `transcript pending`.
- Branch/PR state right now:
  - Open PR: #50 `Improve live transcription fallback and unavailable state handling`
  - PR URL: `https://github.com/freesuraj/Omni-Android/pull/50`
  - Local commit on branch: `068bc7a` = revert of prior attempt (`3b46d53`) because it "didn't really work".
  - Local branch is ahead of origin by 1 commit; revert is not pushed yet.
  - Net code on local branch is effectively back to `main` behavior for that attempted fix.

## Workflow Rules In Force (important)
- Source of truth: `AGENTS.md`
- For each requested task: create a ticket first **unless user explicitly says no ticket**.
- Board statuses allowed: `Todo`, `In Progress`, `Done`.
- Every PR requires:
  - summary
  - validation results
  - duplication/DRY notes
  - local emulator screenshots attached to PR (CI screenshot workflow removed).
- Required validation before done:
  - `./gradlew :app:lintDebug :app:assembleDebug`
  - `bundle exec fastlane android verify` (or fallback `./gradlew :app:testDebugUnitTest`)

## Major Completed Work (merged)
- PR #41: removed CI PR screenshots workflow; moved to local screenshot evidence.
- PR #43: implemented `StudyGenerationProvider` across LLM providers.
- PR #45: fixed live transcript preview/dashboard placeholder-word behavior (first pass).
- PR #47: wired Play product IDs and dynamic paywall pricing.
- PR #49: live transcription follow-up + audio transcript opening improvements.

## Key Product/Tech Decisions Already Applied
- Omni provider uses Gemini model path (`gemini-2.0-flash-lite`) under Omni abstraction.
- Omni API key is no longer hardcoded in source; build reads from Gradle/env properties.
- Premium product IDs in Android billing:
  - lifetime: `com.suraj.apps.omni.pro.forever`
  - monthly: `com.suraj.apps.omni.pro.monthly`
  - yearly: `com.suraj.apps.omni.pro.yearly`
- PR screenshot generation in CI removed; local emulator screenshots are the process.

## Areas Frequently Touched Recently
- Live transcription engine:
  - `core/data/src/main/java/com/suraj/apps/omni/core/data/transcription/AudioTranscriptionEngine.kt`
- Audio recording/viewmodel:
  - `feature/audio/src/main/java/com/suraj/apps/omni/feature/audio/AudioViewModel.kt`
- Dashboard transcription/source status:
  - `feature/dashboard/src/main/java/com/suraj/apps/omni/feature/dashboard/DashboardViewModel.kt`
- Study provider routing:
  - `core/data/src/main/java/com/suraj/apps/omni/core/data/provider/StudyGenerationGateway.kt`
  - `core/data/src/main/java/com/suraj/apps/omni/core/data/provider/RemoteStudyGenerationProviders.kt`
- Import pipeline:
  - `core/data/src/main/java/com/suraj/apps/omni/core/data/importing/DocumentImportRepository.kt`

## Board Snapshot (Project #2)
- Core tickets (#1 through #18) are now in `Done`.
- Later fixes also done and linked (examples: #40, #42, #44, #46, #48).
- No open GitHub issues were listed at last check (`gh issue list --state open` returned empty), so new work may come via freshly created tickets only.

## Validation/Env Notes
- Java for CLI builds:
  - `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home`
- Typical verify command used:
  - `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:lintDebug :app:assembleDebug :app:testDebugUnitTest`

## Immediate Next Actions For Any New Agent
1. Confirm whether to push local revert `068bc7a` to PR #50 branch or close PR #50 and open a fresh fix branch.
2. Reproduce live transcription issue on physical Android 16 device (not only emulator).
3. Add robust unavailable/timeout/error-state handling for recognizer and onboarding transcript pipeline.
4. Verify dashboard/source details behavior for all transcript states: available, pending, unavailable.
5. Run lint/build/tests, capture local screenshots, update PR with evidence, then move board ticket state accordingly.

## Risk Notes
- Speech recognizer behavior varies by OEM/ROM and Google speech services availability; emulator pass does not guarantee device pass.
- Current branch divergence (`ahead 1`) can confuse PR #50 unless reconciled (push revert or reset strategy agreed).
