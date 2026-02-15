---
name: android-feature-delivery
description: Use this skill when implementing Android features or bug fixes for Omni with Jetpack Compose, including parallel subagent execution, DRY refactoring checks, and mandatory lint/debug build validation.
---

# Android Feature Delivery

Use this workflow for any ticket that changes Android code.

## Inputs required
- Ticket link/ID and acceptance criteria.
- Target module(s) and affected user flow(s).
- Linked PR (when available).
- Data reference: `references/ios-swiftdata-to-android-model.md` (mandatory for persistence work).

## Workflow
1. Start lifecycle:
   - Ask `board-state-manager` to move ticket `Backlog -> In Progress`.
2. Plan implementation slices:
   - Split work by layer for parallel execution where safe:
     - UI (Compose screen/components)
     - Domain/application logic
     - Data/integration (network/db/billing)
   - If ticket touches persistence, align fields/relations with `references/ios-swiftdata-to-android-model.md`.
3. Implement with DRY enforcement:
   - Before adding code, run `rg` for equivalent logic/components.
   - If duplication exists, refactor shared code before adding variants.
4. Validate locally (required):
   - `./gradlew :app:lintDebug :app:assembleDebug`
   - `bundle exec fastlane android verify` (preferred)
   - Fallback if fastlane lane is unavailable: `./gradlew :app:testDebugUnitTest`
5. Prepare handoff for QA:
   - Summarize changed files.
   - Include duplication/refactor note.
   - Include command output summary.

## Done criteria
- Acceptance criteria met.
- No unresolved lint errors.
- Debug app assembles successfully.
- Tests pass in configured verification path.
- Handoff packet delivered to `qa-fastlane-manager`.

## Output template
- Ticket:
- Scope:
- Files changed:
- DRY/refactor actions:
- Validation commands + result:
- Risks/open items:
