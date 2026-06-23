# Task J-3: Rewrite `CLAUDE.md` so the status snapshot matches Sprint 5's real outcome

**Resolves:** Should-fix in `reviews/sprint-5-track-a-review.md` ("`CLAUDE.md` still frames this round as `Sprint 4` using `H-1` through `H-5` ... and overstates both the skipped-test and Angular state").

## Context

The Track B pointer is already fixed. The remaining problem is accuracy:

- `CLAUDE.md:48-57` still describes the latest work as "Sprint 4" instead of Sprint 5.
- It says the Docker-optional test "skips gracefully" even though the current Surefire XML still shows `tests="0"` / `skipped="0"`.
- It presents the Angular work as fully closed even though lockfile/config drift and the missing browser smoke test are still open.

This task should still run last, after `J-1` and `J-2`, so the snapshot describes what is actually true.

## Task

1. Re-read `reviews/sprint-5-track-a-review.md` and the latest sprint handoff before editing `CLAUDE.md`.
2. Rewrite the "Current status snapshot" section so it accurately states:
   - Sprint 1, 2, and 3 were rejected.
   - Sprint 4 fixed the wrapper problem and got the project onto a workable portability path, but did not fully close Track A by itself.
   - Sprint 5's actual scorecard was: skipped-test reporting still open, Angular migration materially improved but not fully verified/normalized, Mockito fallback accepted, snapshot itself still inaccurate.
   - Track B remains gated until Sprint 6 is completed and re-reviewed by Codex.
3. Keep the Track B pointer as `docs/backlog/sprint-1.md`.
4. Date the snapshot to the day this task actually runs.
5. State what was verified locally and that acceptance still depends on Codex's review; do not write the snapshot as if the sprint is already approved.

## Out of scope

- Do not rewrite unrelated sections of `CLAUDE.md`.
- Do not edit historical handoff or review files.

## Acceptance criteria

- The snapshot uses the correct sprint number and correct task IDs for the latest round.
- The snapshot no longer overstates the Docker-optional test or Angular-upgrade status.
- The Track B pointer remains correct.
- `scripts/pre-review-check.sh 6` passes after the sprint is committed and the new handoff exists.
