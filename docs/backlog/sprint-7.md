# Sprint 7 backlog: Track A close-out, round 5

**Source:** `reviews/sprint-6-track-a-review.md`, dated 2026-06-23. Verdict: **reject**. One must-fix with two parts (`J-2`'s dependency floor and the still-missing real smoke test), one should-fix (`J-3` left stale Angular-18 text elsewhere in `CLAUDE.md`).

## Why this sprint exists

Sprint 6 closed `J-1` for real (Codex independently confirmed the Surefire skip-reporting fix) and resolved the lockfile/config drift between the two UIs. What's left is narrower but has now failed three sprints running in a row for the same underlying reason: claiming a check is done without the evidence the brief actually asked for. `npm audit` still reports the same `14`/`6 high` it did last round — nothing about the dependency state actually changed since Sprint 6, it was just asserted to be an accepted end state ("require Angular 22") without the audit data actually proving that's the only fix. And the "browser smoke test" has now been satisfied with route-availability checks three times in a row, never the actual click-through the brief asks for.

## Tasks

Recommended order: **K-1 first** (re-establish the real current dependency floor, upgrade further if the data supports it) → **K-2** (the actual smoke test, against the final state from K-1, not an intermediate one) → **K-3 last** (the remaining stale Angular-18 text, only meaningful once K-1's real final version is known).

| Task | What | Severity it resolves |
|---|---|---|
| K-1 | Re-verify the current Angular dependency floor with fresh data and upgrade further if it actually clears findings; don't reassert "Angular 22 required" without the audit data proving it | Must-fix (part 1) |
| K-2 | Perform and document the actual browser smoke test — login, dashboard, order placement, stock view — not route-availability checks | Must-fix (part 2) |
| K-3 | Fix the remaining stale "Angular 18" text in `CLAUDE.md`'s repo overview and frontend architecture sections | Should-fix |

Each task's full brief is in `docs/backlog/tasks/sprint-7/`. Run `scripts/pre-review-check.sh 7` before telling Codex this sprint is ready.
