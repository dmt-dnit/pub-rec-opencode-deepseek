# Sprint 3 backlog: Track A close-out

**Source:** Codex re-review verdict on Sprint 2 Track A — `reviews/sprint-2-track-a-review.md`, dated 2026-06-20. Verdict: **reject**. Three must-fix blockers, one should-fix, confirmed by re-reading every cited file before writing this backlog (not taken on faith).

## Why this sprint exists

Sprint 2 genuinely fixed the Sprint 1 functional defects (Codex confirms F-1, F-2, F-3, F-4, F-7 are now correct in the code), but it didn't finish, and it introduced a new visibility problem: Codex's own verification environment (Windows PowerShell, IBM Semeru/OpenJ9 JDK 21) can't run this repo's tests at all right now, for two unrelated reasons. A sprint that can't be verified can't be accepted regardless of how correct the code looks on inspection. Sprint 3 is entirely about making this repo's checks pass in the reviewer's actual environment, plus finishing the one task (F-6) that was never really done.

Track B (`docs/backlog/sprint-1.md`) remains gated — it does not start until every task below is verified and Codex re-reviews clean.

## Process note

Same as Sprint 2: run each task in its own worktree or branch, not concurrent edits on one shared tree. G-2 and G-3 touch disjoint services and can run fully in parallel. G-5 is a repo-wide doc/grep pass — sequence it last, after the others are actually true, since its whole job is to document the end state accurately.

## Tasks

Recommended order: **G-1 first** (fast, unblocks Codex's own ability to verify everything else) → **G-2 and G-3 in parallel** (disjoint services, the two Java-21 portability fixes) → **G-4 anytime in parallel** (Angular deps, unrelated files) → **G-5 last** (doc cleanup, only meaningful once the above are true).

| Task | What | Severity it resolves |
|---|---|---|
| G-1 | Add a working `mvnw.cmd` to all 4 Maven modules | Must-fix 3 (Windows wrapper) |
| G-2 | Fix `order-service` test failure on OpenJ9: Mockito inline mock-maker self-attach error | Must-fix 2 |
| G-3 | Fix `inventory-service` test crash on OpenJ9: forked JVM segfault during `@EmbeddedKafka` startup | Must-fix 2 (separate root cause from G-2) |
| G-4 | Finish Angular dependency remediation (both UIs) — F-6 carried over, still failing | Must-fix 1 |
| G-5 | Close out the last stale `CLAUDE.md` status-snapshot line; confirm F-5 fully done | Should-fix 1 |

Each task's full brief is in `docs/backlog/tasks/sprint-3/`. Track B does not start until all five are merged and re-verified against the same checks Codex used: `mvn test` on every backend module (under both a standard JDK 21 and, if available, the same Semeru/OpenJ9 build Codex used), `npm run build` + `npm audit` on both UIs, and a real browser smoke test of the order-placement flow end to end (still hasn't happened — every check so far has been code- or single-module-test-level, per the Sprint 2 handoff).
