# Task I-4: Rewrite `CLAUDE.md`'s status snapshot to match Sprint 4's actual outcome

**Resolves:** Should-fix in `reviews/sprint-4-track-a-review.md` ("`CLAUDE.md` still contains the old Sprint 3 snapshot... still says Sprint 3 'addresses those environment blockers', and still points Track B at `docs/backlog/sprint-3.md`... That is the exact stale/optimistic state Sprint 4 was supposed to replace").

## Context

This is the third sprint in a row the status-snapshot task itself has been carried over without being done (`F-5` partial, `G-5`/`H-5` not done at all). Don't repeat the pattern that caused this: this task is sequenced **last**, after `I-1` through `I-3` are genuinely verified, so the snapshot it writes describes what's actually true rather than what was hoped to be true.

## Task

1. Confirm `I-1` through `I-3` are actually complete by reading the code/diffs and verification evidence yourself.
2. Rewrite the "Current status snapshot" section in `CLAUDE.md` to accurately describe:
   - Sprint 3: rejected (`reviews/sprint-3-track-a-review.md`).
   - Sprint 4: rejected (`reviews/sprint-4-track-a-review.md`) — `H-1` confirmed working in Codex's real PowerShell/OpenJ9 environment (genuine progress), `H-2` partial (skip mechanism worked but wasn't reported correctly), `H-3`/`H-4`/`H-5` not resolved.
   - Sprint 5 (this sprint): describe what `I-1` through `I-4` actually resolved, based on your own verification — not on what the briefs hoped for.
3. Fix the Track B pointer: it must read `docs/backlog/sprint-1.md` (the actual Track B task list), not `docs/backlog/sprint-3.md` — this exact error has now been flagged twice (`H-5`'s brief already said this; it still wasn't fixed because `H-5` was never attempted).
4. Date the snapshot to when this task actually runs.
5. Do not declare the sprint accepted — that's Codex's call. State what was verified locally and that it's pending review.

## Out of scope
- Don't rewrite the rest of `CLAUDE.md` — status-snapshot paragraph and the Track B link only.
- Don't touch any file under `docs/backlog/sprint-*.md`, `*-handoff.md`, or `reviews/` — historical records.

## Acceptance criteria
- The status snapshot matches `reviews/sprint-4-track-a-review.md`'s actual scorecard and accurately states Sprint 5's outcome based on real verification.
- The Track B pointer correctly cites `docs/backlog/sprint-1.md`.
- `grep -rn -i 'kafka-demo|kafkademo|article' CLAUDE.md` still returns nothing.
- Run `scripts/pre-review-check.sh 5` after this task and confirm it passes before telling Codex the sprint is ready.
