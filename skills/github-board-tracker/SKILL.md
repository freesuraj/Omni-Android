---
name: github-board-tracker
description: Use this skill to manage Omni Android ticket states on the GitHub Project board with exactly three states (Todo, In Progress, Done), including review-driven rollback to Todo.
---

# GitHub Board Tracker

Use this skill whenever a ticket or PR lifecycle state changes.

## Board model
- Allowed states only:
  - `Todo`
  - `In Progress`
  - `Done`
- Allowed transitions only:
  - `Todo -> In Progress`
  - `In Progress -> Done`
  - `In Progress -> Todo` (must include rejection reason)

## Ownership
- This skill is owned by `subagents/board-state-manager.md`.
- No other subagent should mutate ticket state directly.

## Required metadata
- Ticket/issue ID
- Project owner and project number
- Project item ID
- Status field ID + option IDs
- Transition reason

## Suggested CLI flow
1. Resolve project item metadata:
   - `gh project item-list <PROJECT_NUMBER> --owner <OWNER> --format json`
2. Update status field:
   - `gh project item-edit --id <ITEM_ID> --project-id <PROJECT_ID> --field-id <STATUS_FIELD_ID> --single-select-option-id <OPTION_ID>`
3. Post trace comment to issue/PR:
   - Include from-state, to-state, actor, and reason.

## Transition rules
- `Todo -> In Progress`:
  - Requires active owner and start timestamp.
- `In Progress -> Done`:
  - Requires PR review pass and green validation.
- `In Progress -> Todo`:
  - Requires review findings with actionable steps.

## Output template
- Ticket:
- Transition:
- Reason:
- Linked PR:
- Validation status:
- Next owner:
