# Subagent Orchestration Playbook

## Objective
Run Android ticket delivery end-to-end using subagents with minimal manual steps.

## Standard execution order
1. `board-state-manager`
   - Move ticket `Backlog -> In Progress`.
2. `android-implementer`
   - Implement scope and run mandatory local validation.
3. `qa-fastlane-manager`
   - Run fastlane verify and screenshots, update PR evidence.
4. `pr-reviewer`
   - Approve/reject and send board action request.
5. `board-state-manager`
   - Apply final transition:
     - approve -> `Done`
     - reject -> `Backlog`

## Parallelization guidance
- Implementation can split into parallel tracks when independent:
  - UI/Compose track
  - Data/domain track
  - Integration/billing track
- Merge tracks before QA handoff.
- Do not parallelize board transitions; board manager stays single-writer.

## Mandatory evidence bundle per ticket
- Ticket + PR link
- Changed files summary
- DRY/refactor summary
- lint/build/test results
- Screenshot paths and PR upload/comment proof
- Review verdict and board transition record

