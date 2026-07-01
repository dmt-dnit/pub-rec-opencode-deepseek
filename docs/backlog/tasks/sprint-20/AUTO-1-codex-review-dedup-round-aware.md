# AUTO-1 — Make the Codex-app dispatch automations update-aware

**Sprint:** 20 (Track B Sprint 7) — candidate
**Type:** Process/tooling (Codex-app automation prompts, **not** repo source code)
**Owner:** Dimitri (edits the Codex app jobs; coordinator has no access to that app)

## Why this exists

Two Codex-app dispatch automations share the same **path/number-based dedup bug**:
once a file has been sent for a given sprint, an *updated* version of that same
file (same basename) is treated as "already handled" and **never re-sent**, even
though its `LastWriteTime` advanced. The initial send is mtime-gated (only files
newer than the setup timestamp), but the dedup is identity-gated, so re-sends of an
updated file are silently suppressed. This bites **every** sprint that produces a
round 2+ or a revised note.

### Instance A — review-dispatch (proven failure, Sprint 19)

Handoff dispatcher, target reviewer thread `019ee066-8550-78f0-811f-e6d17664c79d`.
Dedup clause:

> already handled if the thread already contains that exact file path **or a review
> request for the same sprint handoff**.

Sprint 19 round-2 fix (`9b8216e`) was pushed and correct, but the reused
`sprint-19-handoff.md` basename matched "same sprint handoff", so no fresh round-2
request was sent. Codex "resumed" and re-stated round-1 P1/P2 verbatim against
pre-fix code → **stale false-reject**, no new review file written.

### Instance B — demo-notes dispatch (same latent bug)

Demo-material dispatcher, target Track A conference thread
`019f0e9b-aa1d-7dc2-bbe2-ca07161956b9`, scans `docs/` for `*demo*.md`. Dedup clause:

> already handled if the thread already contains that exact file path **or a request
> for the same demo Markdown file**.

Same flaw: a revised `docs/demo-notes-sprint-N.md` (e.g. updated after a round-2
review adds demo-worthy beats or captured evidence) reuses its path and is
suppressed → the conference/YouTube thread never gets the update.

## Scope

Edit the two Codex-app automation prompts only. No repository changes.

## Change (apply to BOTH automations)

Make "already handled" freshness-aware instead of identity-only. For each, replace
the identity dedup with a key that incorporates the file's freshness, e.g. one of:

- **(file identity + LastWriteTime)** — resend when a matching file's mtime is newer
  than the mtime of the last request already in the thread for that file; **or**
- an explicit **round/version marker** the coordinator writes into the file (e.g. a
  `Round: N` line in handoffs, a `Rev: N` line in demo notes), dedup on
  `(file identity + marker)`.

Keep each automation's existing setup-timestamp floor (review: ignore ≤
`2026-06-29T18:44:53+02:00`; demo: ignore ≤ `2026-06-29T18:48:38+02:00`) and the
existing basename filters (review: numeric `sprint-<n>-handoff.md` /
`sprint-<n>handoff.md`, ignore non-numeric variants; demo: basename contains `demo`,
`.md`).

## Acceptance criteria (observable outcomes — per automation)

1. **Update resend fires:** with a file already sent once, touch it (newer mtime /
   bumped marker) and confirm the automation sends a **new** message to the correct
   target thread referencing that file. Show the new thread message.
   - Review: thread `019ee066-8550-78f0-811f-e6d17664c79d`, `sprint-<N>-handoff.md`.
   - Demo: thread `019f0e9b-aa1d-7dc2-bbe2-ca07161956b9`, `docs/demo-notes-sprint-N.md`.
2. **No duplicate on unchanged file:** re-run with the file unchanged (same
   mtime/marker) → sends **nothing** for that file.
3. **Setup-timestamp floor still holds:** a file at/older than the automation's setup
   timestamp is still not sent.
4. **Basename filters still hold:** review still ignores non-numeric variants
   (e.g. `sprint-7-codex-handoff.md`); demo still requires `demo` in the basename.

## Verification note

These are Codex-app prompt changes, so acceptance evidence is **screenshots/thread
messages from the Codex app**, not repo test output. State that limitation explicitly
in the handoff — coordinator cannot exercise the Codex app directly.

## Related

- Stale false-reject write-up: CLAUDE.md status snapshot (2026-07-01), commit `b040b00`.
- Round-1 reject (stale findings origin): `reviews/sprint-19-track-b-review.md`.
- The verified fix under review: `scripts/startup-all.sh` @ `9b8216e` (L30–37, L89).
