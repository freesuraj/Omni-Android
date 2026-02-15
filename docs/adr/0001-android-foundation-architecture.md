# ADR 0001: Android Foundation Architecture

## Status
Accepted

## Context
Omni Android requires parallel feature delivery while preserving iOS behavior parity.
A modular architecture is needed so subagents can work independently with low merge friction.

## Decision
- Use a modular Compose architecture with:
  - `app`
  - `core:designsystem`
  - `core:model`
  - `core:data`
  - `feature:*` per product area.
- Use Navigation Compose in the app shell.
- Use Hilt for dependency injection entry points.
- Keep data model parity against `references/ios-swiftdata-to-android-model.md`.

## Consequences
- Faster parallel development across feature tracks.
- Clear ownership boundaries.
- More Gradle modules to manage.
