# AUTO-1 — Make the Codex review-dispatch automation round-aware

**Sprint:** 20 (Track B Sprint 7) — candidate
**Type:** Process/tooling (Codex-app automation prompt, **not** repo source code)
**Owner:** Dimitri (edits the Codex app job; coordinator has no access to that app)

## Why this exists

Sprint 19 round-2 produced a **stale false-reject**. The round-1 fix (`9b8216e`)
was pushed and correct, but Codex "resumed" its old thread and re-stated the
round-1 P1/P2 findings verbatim against pre-fix code, writing no new review file.

Root cause is the dispatch automation's dedup rule, which currently reads:

> treat a file as already handled if the senior software reviewer thread already
> contains that exact file path **or a review request for the same sprint handoff**.

Because a round-2 fix reuses the **same** `sprint-19-handoff.md` basename/number,
the "same sprint handoff" clause matches and the automation **never sends a fresh
round-2 request** — even though the handoff's `LastWriteTime` advanced. The initial
send is mtime-gated (only files newer than the setup timestamp), but the dedup is
sprint-number-gated, so re-sends of an updated handoff are silently suppressed.

This will recur on **every** future sprint that needs a round 2+.

## Scope

Edit the Codex-app automation prompt only. No repository changes.

## Change

Make "already handled" round-aware. Replace the sprint-number dedup with a key that
also incorporates the handoff's freshness, e.g. one of:

- **(sprint number + handoff LastWriteTime)** — resend when a matching handoff's
  mtime is newer than the mtime of the last request already in the thread for that
  sprint; **or**
- an explicit **round marker** the coordinator writes into the handoff (e.g. a
  `Round: N` line), dedup on `(sprint number + round)`.

Keep the existing setup-timestamp floor (still ignore handoffs at/older than
`2026-06-29T18:44:53+02:00`) and the non-numeric-basename filter.

## Acceptance criteria (observable outcomes)

1. **Round-2 resend fires:** with a `sprint-<N>-handoff.md` already sent once, touch
   it (newer mtime / bumped round marker) and confirm the automation sends a **new**
   review request to thread `019ee066-8550-78f0-811f-e6d17664c79d` referencing that
   sprint. Show the new thread message.
2. **No duplicate on unchanged handoff:** re-run the automation with the handoff
   unchanged (same mtime/round) and confirm it sends **nothing** for that sprint.
3. **Setup-timestamp floor still holds:** a handoff at/older than the setup timestamp
   is still not sent.
4. **Non-numeric basenames still ignored** (e.g. `sprint-7-codex-handoff.md`).

## Verification note

This is a Codex-app prompt change, so acceptance evidence is **screenshots/thread
messages from the Codex app**, not repo test output. State that limitation explicitly
in the handoff — coordinator cannot exercise the Codex app directly.

## Related

- Stale false-reject write-up: CLAUDE.md status snapshot (2026-07-01), commit `b040b00`.
- Round-1 reject (stale findings origin): `reviews/sprint-19-track-b-review.md`.
- The verified fix under review: `scripts/startup-all.sh` @ `9b8216e` (L30–37, L89).
