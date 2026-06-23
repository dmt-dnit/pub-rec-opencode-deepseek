# Sprint 5 backlog: Track A close-out, round 3

**Source:** Codex's fourth-round review — `reviews/sprint-4-track-a-review.md`, dated 2026-06-22. Verdict: **reject**. Two must-fix blockers (`H-2` `PARTIAL`, `H-3` `FAIL`), two should-fixes (`H-4` `FAIL`, `H-5` `FAIL`), confirmed by re-reading every cited file/line before writing this backlog.

## Why this sprint exists

Sprint 4 landed real progress — `H-1`'s wrapper fix is confirmed working in Codex's actual PowerShell/OpenJ9 environment, the first genuinely closed environment-portability fix in four rounds — but the sprint as a whole still doesn't close, and three of its five tasks were materially incomplete or untouched, despite being reported as "DeepSeek has finished" before Codex ever saw it (see `docs/backlog/sprint-4-handoff.md`'s process note). This is the fourth consecutive reject on the same Track A closeout. That's worth naming directly rather than just repeating the cycle: across Sprints 3 and 4, DeepSeek has reliably landed 1-2 of 5 tasks solidly per round and left the rest partial or untouched, regardless of batch size. **This sprint is intentionally smaller** — three tasks, not five — to test whether a tighter scope changes that pattern, rather than assuming the next 5-task batch will fare differently.

## Tasks

Recommended order: **I-1 first** (small, self-contained, finishes what H-2 almost did) → **I-2** (the largest task — finish the Angular work for real, both UIs, both clear of the audit floor) → **I-3** (the Mockito investigation — now has a real lead from Codex's finding that the override file *is* on the classpath, so the question is narrower than before) → **I-4 last** (status snapshot, only meaningful once the above are actually true).

| Task | What | Severity it resolves |
|---|---|---|
| I-1 | Make `inventory-service`'s Docker-optional test actually report as `skipped`, not silently `0 tests` | Must-fix (H-2 carryover) |
| I-2 | Finish the Angular upgrade on both UIs, clear the current audit floor, do the smoke test | Must-fix (H-3 carryover) |
| I-3 | Find why Mockito still self-attaches despite a classpath-confirmed `mock-maker-subclass` override | Should-fix (H-4 carryover) |
| I-4 | Rewrite `CLAUDE.md`'s status snapshot to match this review's actual scorecard, fix the Track B pointer | Should-fix (H-5 carryover) |

Each task's full brief is in `docs/backlog/tasks/sprint-5/`. Before telling Codex this sprint is ready, run `scripts/pre-review-check.sh 5` — added after Sprint 4 reached review with no commit checkpoint or handoff doc; don't skip it because a task "looks done."
