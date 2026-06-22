# Task H-5: Fix `CLAUDE.md`'s status snapshot accuracy and Track B pointer

**Resolves:** Should-fix 2 in `reviews/sprint-3-track-a-review.md` ("the new snapshot in `CLAUDE.md:42` through `CLAUDE.md:50` prematurely claims G-1 through G-5 are done, even though this review still finds G-1, G-3, and G-4 open. It also points Track B at `docs/backlog/sprint-3.md`... but Track B remains the backlog in `docs/backlog/sprint-1.md`").

## Context

Sprint 3's `G-5` rewrote `CLAUDE.md`'s status snapshot to describe Sprint 3 as complete and successful — but it did this *before* Codex's review came back, and the review found 3 of the 5 tasks (`G-1`, `G-3`, `G-4`) still failing. This is the second sprint in a row where the status-snapshot task has gotten something about the snapshot itself wrong (Sprint 2's version was stale in the other direction — too pessimistic; Sprint 3's is now too optimistic). The snapshot also has a factual link error: it tells readers Track B hardening is gated on `docs/backlog/sprint-3.md`, but Track B's actual backlog has always lived in `docs/backlog/sprint-1.md` (see that file's "Track B — Hardening" section) — `sprint-3.md` is itself a Track-A-closeout sprint, not the hardening backlog.

**This task is sequenced last in Sprint 4, same as every prior round** — it documents the end state, so it must run after H-1 through H-4 actually land, not before.

## Task

1. Confirm H-1 through H-4 are genuinely complete by reading the actual code/diffs and verification results yourself — don't infer status from task titles or self-reports.
2. Rewrite the "Current status snapshot" section in `CLAUDE.md` to accurately reflect:
   - Sprint 1: rejected (per `reviews/sprint-1-track-a-review.md`).
   - Sprint 2: rejected (per `reviews/sprint-2-track-a-review.md`).
   - Sprint 3: rejected (per `reviews/sprint-3-track-a-review.md`) — be specific that this was an environment-verifiability rejection, not a functional-correctness one; `order-service`'s OpenJ9 portability was confirmed as real progress even though the sprint as a whole didn't close.
   - Sprint 4 (this sprint): describe what H-1 through H-5 actually resolved, based on your own verification, not on what the task briefs hoped they'd resolve.
3. Fix the Track B pointer: it should read `docs/backlog/sprint-1.md` (the actual Track B task list), not `docs/backlog/sprint-3.md`.
4. Date the snapshot to when this task actually runs, not copied from a prior sprint's date.
5. Do **not** declare Sprint 4 closed/accepted in this doc — that's Codex's call, not something to assert preemptively the way Sprint 3's snapshot did. State what was verified locally and that it's pending Codex's review, same framing this file used correctly back in the Sprint-1/2 era.

## Out of scope
- Don't rewrite the rest of `CLAUDE.md` — this is the status-snapshot paragraph and the Track B link only.
- Don't touch `docs/backlog/sprint-1.md`, `sprint-2.md`, `sprint-3.md`, `*-handoff.md`, or anything in `reviews/` — historical records, not to be revised.

## Acceptance criteria
- The status snapshot in `CLAUDE.md` matches `reviews/sprint-3-track-a-review.md`'s actual scorecard for Sprint 3, and accurately states Sprint 4's outcome based on real verification (not aspiration).
- The Track B pointer in `CLAUDE.md` correctly cites `docs/backlog/sprint-1.md`.
- `grep -rn -i 'kafka-demo|kafkademo|article' CLAUDE.md` still returns nothing (don't regress the Sprint-2/3 cleanup).
- The snapshot does not assert Codex has accepted anything that Codex hasn't actually accepted yet.
