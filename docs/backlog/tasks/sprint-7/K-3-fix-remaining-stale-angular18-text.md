> **RESOLVED this session (2026-06-23), commit `ab37adc` — closed outside the Codex loop.**
> `CLAUDE.md:12` (repo overview) and `CLAUDE.md:85` (frontend architecture) now read "Angular 22", not "Angular 18". The only remaining "Angular 18" mention is line 41's historical Dependency-currency note, which this brief explicitly says to leave alone — verified still reads as historical context, not a current-state claim. The original brief is retained below for the record.

---

# Task K-3: Fix the remaining stale "Angular 18" text in `CLAUDE.md`

**Resolves:** Should-fix in `reviews/sprint-6-track-a-review.md` ("the repo overview and frontend architecture sections still describe the UIs as Angular 18 SPAs at `CLAUDE.md:12` and `CLAUDE.md:85`, which is now false after the Angular 21 migration. `J-3` fixed the snapshot, but the broader repo guidance is still inconsistent").

## Context

`J-3` (Sprint 6) correctly updated the "Current status snapshot" section to track the real sprint history, but `CLAUDE.md` has two other places that still describe the UIs by their original Angular version:

- Line 12 (repo overview, "What this repo is"): "two Angular 18 SPAs"
- Line 85 (Architecture → Frontend): "structurally identical Angular 18 + Angular Material SPAs"

Both are now false after the Sprint 4-7 Angular migration. **Sequence this after `K-1`** — it should state whatever the real final Angular major version is once `K-1` lands, not guess at it now.

Line 41 (the "Dependency currency" section's "Angular 18.2.x (this repo's original pin)... was very likely the correct LTS choice when the project was set up") is **correctly historical** — it's describing the original pin as context for why drift-checking matters, not claiming the current state. Leave it alone.

## Task

1. Confirm `K-1`'s final Angular version.
2. Update line 12 to reflect the actual current major version (e.g. "two Angular `<N>` SPAs") rather than hardcoding "18".
3. Update line 85 similarly.
4. Grep the rest of `CLAUDE.md` for any other stale version-specific claims you find while you're in there (e.g. anywhere else "18.2" or "Angular 18" appears outside the historical Dependency-currency paragraph) and fix those too, but don't go rewriting unrelated content.

## Out of scope
- Don't touch the "Dependency currency" section's historical reference to the original 18.2.x pin (line 41) — that's accurate as written.
- Don't rewrite the status snapshot — `J-3` already handles that; if `K-1`/`K-2`'s outcome needs reflecting there too, that's this sprint's snapshot update, done as part of writing the handoff, not this task.

## Acceptance criteria
- `grep -n "Angular 18" CLAUDE.md` returns nothing outside the historical Dependency-currency paragraph (line 41), and that paragraph still reads correctly as historical context, not a current-state claim.
- The repo overview and frontend architecture sections accurately state the current Angular major version.
