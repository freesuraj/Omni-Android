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
