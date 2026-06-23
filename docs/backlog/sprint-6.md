# Sprint 6 backlog: Track A close-out, round 4

**Source:** `reviews/sprint-5-track-a-review.md`, dated 2026-06-23. Verdict: **reject**. Sprint 5 closed the big portability risks (`mvnw.cmd` is fixed, the Mockito/OpenJ9 fallback is now accepted, both UIs are on Angular 21), so this round should stay narrow and finish the remaining verification and consistency gaps instead of reopening already-closed work.

## Why this sprint exists

Three things are still preventing Track A from being cleanly re-reviewed and closed:

1. `inventory-service` still hides the Docker-missing case as `0 tests` instead of a reported skip.
2. The Angular upgrade is no longer the problem; the remaining problem is proving the Angular 21 result is truly consistent and runtime-safe across both UIs.
3. `CLAUDE.md` still overstates what Sprint 5 actually achieved.

That means this sprint is intentionally smaller than Sprint 5. Do not spend time re-investigating the already-accepted Mockito fallback unless a new failure appears.

## Tasks

Recommended order: **J-1 first** (smallest correctness fix) -> **J-2** (finish Angular verification for real) -> **J-3 last** (status snapshot only after the first two are actually true).

| Task | What | Severity it resolves |
|---|---|---|
| J-1 | Make `inventory-service` report a real skipped test when Docker is absent, and prove the positive path when Docker is present | Must-fix |
| J-2 | Normalize the Angular 21 result: lockfiles/config, current audit explanation, and browser smoke test | Must-fix |
| J-3 | Rewrite `CLAUDE.md`'s status snapshot so it matches Sprint 5's actual scorecard | Should-fix |

Each task's full brief is in `docs/backlog/tasks/sprint-6/`. Before telling Codex this sprint is ready, run `scripts/pre-review-check.sh 6`.
