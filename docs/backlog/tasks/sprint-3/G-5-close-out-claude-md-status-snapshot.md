# Task G-5: Close out the stale `CLAUDE.md` status snapshot

**Resolves:** Should-fix 1 in `reviews/sprint-2-track-a-review.md` ("`CLAUDE.md` still contains a stale status snapshot that says F-5 is 'not started' and still mentions the old `kafka-demo` / `article` naming").

## Context

`CLAUDE.md`'s "Current status snapshot" paragraph (currently around line 40) still reads:

> Sprint 1 (Track A: Order/Inventory domain pivot) was rejected by Codex... Sprint 2 (Track A stabilization, tasks F-1–F-7) is in progress... F-5 (rename cleanup + rewriting this file's architecture sections to drop old `kafka-demo`/`article` naming) not started...

This is now wrong on every count: Sprint 2 is no longer "in progress" (it's been reviewed twice), F-5 was not "not started" by the time of the Sprint 2 handoff (it was partially done — runtime/config leftovers cleaned, only the doc itself was stale), and the paragraph itself is the very `kafka-demo`/`article` mention it complains about. Codex flagged this exact gap twice now across two reviews — it's small but keeps surviving because it's a single paragraph nobody's been told to specifically touch.

This task is sequenced **last** in Sprint 3, on purpose: it documents the end state, so don't run it until G-1 through G-4 are actually done — otherwise this paragraph will just go stale again immediately.

## Task

1. Confirm G-1 through G-4 are actually complete (read the code/diffs yourself, don't take it on faith — same standard as every other verification step in this workflow).
2. Run a repo-wide check for any remaining stale naming, the same grep the F-5 brief specified: `grep -rn -i 'kafka-demo\|kafkademo\|article' CLAUDE.md docs/` (and anywhere else in tracked source, excluding `docs/backlog/sprint-1.md` / `docs/backlog/sprint-2*.md` / `reviews/` — those are historical records and are *supposed* to mention the old naming since they describe what was wrong).
3. Replace the "Current status snapshot" paragraph in `CLAUDE.md` with an accurate one reflecting where things actually stand after Sprint 3 — written so it doesn't reference the old domain naming at all (describe what's done/pending in terms of `order-service`/`inventory-service`/UI names, not `kafka-demo`/`article`).
4. Update the snapshot's date to the date this task is actually completed.

## Out of scope
- Don't rewrite the rest of `CLAUDE.md`'s architecture sections — Sprint 2's F-5 already did that; this task is specifically the leftover status-snapshot paragraph and any final stray naming references, not a full doc rewrite.
- Don't touch `docs/backlog/sprint-1.md`, `sprint-2.md`, `sprint-2-handoff.md`, or anything in `reviews/` — those are point-in-time historical records of what was wrong and should keep the old naming as evidence, not be cleaned up.

## Acceptance criteria
- `grep -rn -i 'kafka-demo\|kafkademo\|article' CLAUDE.md` returns nothing.
- The status snapshot paragraph in `CLAUDE.md` accurately reflects Sprint 3's actual outcome (which tasks landed, what's still open if anything), dated to when this task ran.
- No unrelated changes to the rest of `CLAUDE.md`.
