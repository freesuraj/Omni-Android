---
name: android-pr-gatekeeper
description: Use this skill to perform final PR review for Omni Android, enforce quality gates, and decide whether tickets move to Done or back to Backlog with actionable findings.
---

# Android PR Gatekeeper

Use this skill as the final merge gate.

## Review priorities
1. Correctness and regressions
2. Architecture and maintainability
3. Duplication and refactor quality
4. Test coverage and verification quality
5. Product acceptance criteria

## Required checks
- Feature works as described by ticket acceptance criteria.
- DRY policy respected (new duplication avoided or refactored).
- Verification evidence present:
  - lint/debug build pass
  - tests pass
  - screenshots attached to PR
- Security/privacy-sensitive areas reviewed (auth, billing, storage).

## Decision outcomes
- Approve:
  - Request board transition `In Progress -> Done`.
- Reject:
  - Provide severity-ordered findings with file references.
  - Request board transition `In Progress -> Backlog`.
  - Include exact re-entry criteria.

## Review output template
- Verdict: Approved | Changes required
- Findings:
  - [Severity] file:path - issue and impact
- Required fixes:
- Validation evidence checked:
- Board action requested:

