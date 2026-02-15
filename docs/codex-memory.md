# Codex Working Memory (Omni-Android)

Last updated: 2026-02-16

## Project identity
- Repo: `freesuraj/Omni-Android`
- Local path: `/Users/suraj/Projects/Personal/Omni-Android`
- Product: **Omni** Android app (Jetpack Compose), parity with iOS Omni (formerly Doc2Quiz)
- Package/app id: `com.suraj.apps.Doc2Quiz` (kept intentionally)

## Guardrails currently in effect
- Source of truth for agent workflow: `AGENTS.md`
- Always run validation before marking work done:
  - `./gradlew :app:lintDebug :app:assembleDebug`
  - `./gradlew :app:testDebugUnitTest` (or fastlane verify lane once fully wired)
- Check for duplication with `rg` before adding code.
- GitHub Project board uses 3 states only: `Todo`, `In Progress`, `Done`.
- iOS model parity reference: `references/ios-swiftdata-to-android-model.md`

## Environment notes
- Android Studio installed.
- Java runtime used for CLI builds:
  - `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home`
- Local SDK expected at `~/Library/Android/sdk` (set in `local.properties`).

## What has been implemented

### Ticket #1 (Done)
- Android multi-module scaffold and navigation shell created:
  - `app`
  - `core:designsystem`, `core:model`, `core:data`
  - feature modules: `library`, `audio`, `settings`, `dashboard`, `quiz`, `notes`, `summary`, `qa`, `analysis`, `paywall`
- Hilt application shell and basic app nav setup added.

### Ticket #2 (Done)
- Design system baseline and first-pass parity placeholders added:
  - theme tokens, typography, reusable UI components, progress overlay
- Feature placeholder routes updated for initial UX structure.

### Ticket #3 (Done)
- Room persistence parity foundation implemented:
  - entities, relations, DAOs, converters, DB factory, migration scaffold
  - schema export enabled and committed under:
    - `core/data/schemas/com.suraj.apps.omni.core.data.local.OmniDatabase/1.json`
- DAO integration tests added for:
  - relation loading
  - cascade deletes (document -> children, quiz -> questions)

### Ticket #4 (Done)
- Import/storage pipeline implemented:
  - document import (PDF/TXT)
  - audio file import
  - web article import by URL
- Full text artifacts persisted to `files/text/<documentId>.txt`
- Imported file copies persisted to `files/imports/<documentId>.<ext>`
- Metadata/preview persisted to Room `documents`.
- Free-tier document cap enforced (1 document for non-premium) with paywall routing.
- Library UI now has + menu actions and web URL dialog.
- Dashboard route now accepts `documentId`.

## Current board status snapshot
- `#1` Done
- `#2` Done
- `#3` Done
- `#4` Done
- `#5` In Progress (Live audio recording tab + waveform + live transcript)
- `#6+` Todo

## Important implementation files to know first
- Navigation:
  - `app/src/main/java/com/suraj/apps/omni/navigation/AppRoutes.kt`
  - `app/src/main/java/com/suraj/apps/omni/navigation/OmniAppNavHost.kt`
- Library/import:
  - `feature/library/src/main/java/com/suraj/apps/omni/feature/library/LibraryRoute.kt`
  - `feature/library/src/main/java/com/suraj/apps/omni/feature/library/LibraryViewModel.kt`
  - `core/data/src/main/java/com/suraj/apps/omni/core/data/importing/DocumentImportRepository.kt`
- Persistence:
  - `core/data/src/main/java/com/suraj/apps/omni/core/data/local/OmniDatabase.kt`
  - `core/data/src/main/java/com/suraj/apps/omni/core/data/local/dao/*.kt`
  - `core/data/src/main/java/com/suraj/apps/omni/core/data/local/entity/*.kt`

## Known caveats / follow-ups
- Import pipeline currently uses placeholder extraction for PDF/audio text; deeper extraction/transcription is planned in later tickets.
- Premium status currently read from shared preferences (`omni_access.premium_unlocked`) as interim behavior until billing parity ticket is completed.
- Fastlane + screenshot upload flow is scaffolded but still needs full end-to-end usage as features land.

## Recommended startup sequence for future Codex runs
1. Open this file and `AGENTS.md`.
2. Verify board status (`gh project item-list 2 --owner freesuraj --format json`).
3. Move next ticket to `In Progress`.
4. Implement with duplication check.
5. Run validation commands.
6. Comment on issue with changes + validation.
7. Move ticket to `Done` and next ticket to `In Progress`.
