---
name: fastlane-android-ops
description: Use this skill for Android fastlane setup and execution for tests, deployment, lint/build validation, and PR screenshot generation/upload for Omni.
---

# Fastlane Android Ops

Use this skill for any QA/release action in the Android project.

## Goals
- Standardize verification and release automation.
- Ensure every PR has screenshot evidence for changed flows.
- Keep manual intervention minimal.

## Baseline setup
- Follow fastlane Android setup: https://docs.fastlane.tools/getting-started/android/setup/
- Ensure lanes exist for:
  - `verify` (lint/build/tests)
  - `screenshots_pr`
  - `internal` (optional deploy lane)

## Lane contract
- `verify`
  - Must run lint + debug assemble + unit tests.
  - Suggested actions:
    - `gradle(task: "lintDebug")`
    - `gradle(task: "assembleDebug")`
    - `gradle(task: "testDebugUnitTest")`
- `screenshots_pr`
  - Captures screenshots for affected flow(s).
  - Stores outputs in a deterministic path (example: `artifacts/pr/<PR_NUMBER>/screenshots`).
  - Posts PR comment with screenshot references.

## Required PR evidence
- Verification summary:
  - Lint: pass/fail
  - Build debug: pass/fail
  - Tests: pass/fail
- Screenshot section:
  - Flow covered
  - Device/emulator
  - Image links/paths

## Failure policy
- If `verify` fails:
  - Block merge.
  - Return ticket to implementer with logs.
- If screenshots fail:
  - Block `Done` transition until screenshot evidence is available.

## Output template
- PR:
- Lanes run:
- Validation result:
- Screenshot paths:
- Uploaded to PR:
- Blockers:

