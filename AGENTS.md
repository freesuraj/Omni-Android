# Omni Android Agent Guardrails

## Scope
- This folder is the operating model for building the native Android app for Omni using Jetpack Compose.
- Work is expected to run through agents/subagents with minimal human intervention.
- Source product baseline is the current iOS app behavior (Omni, previously Doc2Quiz).
- Session memory and latest progress snapshot:
  - `docs/codex-memory.md`

## Product baseline to preserve on Android
- App brand: `Omni` (do not change bundle/package IDs unless explicitly requested).
- Input sources:
  - Document import: PDF and plaintext.
  - Web article import by URL.
  - Audio file import.
  - Live audio recording tab (record, pause/resume, finish).
- Audio behavior:
  - Live recording shows waveform and live transcript.
  - Imported audio is transcribed on-device during onboarding.
  - Long audio transcription supports chunk/buffer processing with progress.
  - Audio documents can be opened to play audio and read transcript.
- Study outputs per document:
  - Quiz generation.
  - Study notes/flashcards.
  - Summary generation.
  - Document Q&A.
  - Detailed analysis view.
- Onboarding flow:
  - Navigate to document dashboard immediately after import.
  - Run analysis/generation background workflow with visible status.
- Monetization:
  - Store subscription/lifetime model with paywall and restore.
  - Free tier limits include:
    - One stored document.
    - Two lifetime live-audio recordings (even if deleted later).
  - Premium unlocks unlimited live-audio recording and other gated features.
- Localization:
  - Localized UI strings across major languages.

## Non-negotiable engineering policies
- Always check for duplication before adding new code:
  - Search for existing helpers/components with `rg`.
  - If logic is repeated in 2+ places, extract/refactor before merge.
- Always run Android lint and debug build before marking work complete:
  - `./gradlew :app:lintDebug :app:assembleDebug`
- Always run test verification path:
  - Preferred: `bundle exec fastlane android verify`
  - Fallback: `./gradlew :app:testDebugUnitTest`
- Never mark a ticket done while lint/build/tests are red.

## Board and ticket workflow (required)
- Dedicated GitHub Project board is the source of truth.
- Default intake rule:
  - For every user-requested task, create a board ticket before implementation.
  - Exception: skip ticket creation only when the user explicitly says not to create one.
- Only three states are allowed:
  - `Todo`
  - `In Progress`
  - `Done`
- Allowed transitions:
  - `Todo -> In Progress`
  - `In Progress -> Done`
  - `In Progress -> Todo` (only with explicit review feedback)
- A dedicated subagent owns state transitions and audit logging.

## PR workflow automation requirements
- Every PR must include:
  - What changed.
  - Validation results (lint/build/tests).
  - Duplication check/refactor notes.
- After each PR implementation is complete:
  - Capture screenshots on a local emulator for the affected flow(s) (fastlane lane or manual adb capture).
  - Upload screenshots to the PR (comment with image references or committed artifact paths).
- Dedicated PR-review subagent decides:
  - `Done` if accepted.
  - `Todo` with actionable findings if changes are rejected.

## CI/CD scaffolding files
- Fastlane:
  - `fastlane/Fastfile` (lanes: `verify`, `screenshots_pr`, `internal`)
  - `fastlane/Appfile`
  - `fastlane/Screengrabfile`
- GitHub workflows:
  - `.github/workflows/android-verify.yml`
  - `.github/workflows/board-status-gate.yml`
  - `.github/workflows/board-transition-on-review.yml`

## Required GitHub repository variables
- `OMNI_PROJECT_OWNER` (user/org that owns the project board)
- `OMNI_PROJECT_NUMBER` (ProjectV2 number)
- `OMNI_STATUS_FIELD_ID` (single-select status field ID)
- `OMNI_STATUS_OPTION_TODO`
- `OMNI_STATUS_OPTION_IN_PROGRESS`
- `OMNI_STATUS_OPTION_DONE`

Note: branch protection should require `Android Verify` and `Board Status Gate` checks to pass.

## Subagent roles
- `subagents/board-state-manager.md`
  - Owns board state transitions and ticket lifecycle bookkeeping.
- `subagents/android-implementer.md`
  - Builds feature/fix using Compose architecture and DRY policy.
- `subagents/qa-fastlane-manager.md`
  - Runs fastlane validation, tests, and local emulator screenshot capture/upload.
- `subagents/pr-reviewer.md`
  - Performs review gate and final board state decision.

## Skills available in this project
- `skills/android-feature-delivery/SKILL.md`
- `skills/github-board-tracker/SKILL.md`
- `skills/fastlane-android-ops/SKILL.md`
- `skills/android-pr-gatekeeper/SKILL.md`

## Data model source of truth
- Android agents must use:
  - `references/ios-swiftdata-to-android-model.md`
- Any persistence/schema change must:
  - reference this mapping in PR notes,
  - preserve parity unless divergence is explicitly approved.

## Collaboration contract
- Primary coordinator delegates tasks in this order:
  1. Board manager creates a ticket for the requested task (unless explicitly skipped by user) and moves it to `In Progress`.
  2. Android implementer executes ticket and prepares PR.
  3. QA/Fastlane manager runs verification and captures local emulator screenshots, updates PR.
  4. PR reviewer approves/rejects and triggers board move to `Done` or `Todo`.
- Each handoff must include:
  - Files changed.
  - Commands executed.
  - Result status (pass/fail).
  - Open risks or blockers.
