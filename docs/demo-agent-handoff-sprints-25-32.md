# Demo Agent Handoff — Sprints 25 to 32

This is a compact catch-up note for the demo/storytelling pass. It exists because no
single handoff in this chat covered Sprint 25–32 demo material end-to-end.

## What already exists in the repo

Demo notes are already present for these sprints:

- [docs/demo-notes-sprint-26.md](C:/projects/pub-rec-opencode-deepseek/docs/demo-notes-sprint-26.md)
- [docs/demo-notes-sprint-27.md](C:/projects/pub-rec-opencode-deepseek/docs/demo-notes-sprint-27.md)
- [docs/demo-notes-sprint-28.md](C:/projects/pub-rec-opencode-deepseek/docs/demo-notes-sprint-28.md)
- [docs/demo-notes-sprint-29.md](C:/projects/pub-rec-opencode-deepseek/docs/demo-notes-sprint-29.md)
- [docs/demo-notes-sprint-30.md](C:/projects/pub-rec-opencode-deepseek/docs/demo-notes-sprint-30.md)
- [docs/demo-notes-sprint-31.md](C:/projects/pub-rec-opencode-deepseek/docs/demo-notes-sprint-31.md)

If you are building a talk/demo timeline, use those first. This note only fills the
real gaps and suggests the strongest through-line.

## Missing retro notes worth capturing

### Sprint 25 — package rename (`com.example` -> `be.dnit`)

Source: [docs/backlog/sprint-25-handoff.md](C:/projects/pub-rec-opencode-deepseek/docs/backlog/sprint-25-handoff.md)

- This is not flashy UI material, but it matters for credibility: the project stopped
  carrying placeholder namespace identity right before public-facing deployment work.
- Good presentation angle: a "mechanical" rename was still treated as review-worthy
  because the real risk was not compile failure, but silent runtime drift in config
  strings like Kafka trusted packages and default type names.
- Nice detail if needed: git’s rename heuristics made the two `WebSocketConfig.java`
  files look cross-swapped in the diff even though the final file content was correct.
  That is a clean example of why reviewers must inspect actual end-state, not just diff
  presentation.

### Sprint 32 — JWT signing-key persistence across restarts

Source: [docs/backlog/sprint-32-handoff.md](C:/projects/pub-rec-opencode-deepseek/docs/backlog/sprint-32-handoff.md)

- Strong production-hardening story: Sprint 29 made accounts survive restarts; Sprint 32
  made sessions survive them by persisting the signing key.
- Good presentation angle: the bug looked like "users get logged out after redeploy,"
  but the real cause was cryptographic identity changing on every restart.
- Important process angle: this sprint was **not** independently reviewed by Codex
  because the source fix came from a Codex browser-debugging agent. That is worth saying
  plainly in a conference story because it shows the team recognized and logged a broken
  independence boundary instead of pretending the normal review gate happened.
- Nice technical proof point: the fix included the first real `auth-server` regression
  test, asserting identical key bytes and identical JWKS `kid` across two simulated
  restarts sharing the same key file.

## Strongest Sprint 25–32 narrative arc

If you need one clean storyline instead of eight sprint bullets, use this:

1. Sprint 28: the team added real admin/security behavior, but Codex caught a subtle
   frontend auth-state race.
2. Sprint 30: that race was fixed without moving trust into the frontend.
3. Sprint 29 and Sprint 32: two separate "persistence" bugs were solved in sequence:
   first accounts, then sessions.
4. Sprint 31: an actual VPS resource incident forced the project to start treating
   operational headroom as part of product correctness, not just infra polish.

That arc is more demo-worthy than Sprint 25–32 as isolated tickets because it shows:

- independent review catching bugs that "working demos" missed
- config and deployment semantics causing real product failures
- the workflow being honest about when review independence did and did not exist

## Probably skip or keep brief in a talk

- Sprint 25: mention briefly unless the audience cares about large mechanical refactors
  and review discipline.
- Sprint 27: use only if you need extra deployment-context setup.
- Sprint 31: keep short unless the talk leans into ops/production-readiness.

## Best follow-up pairing with later material

If the demo agent is choosing what to connect into Sprint 33 and beyond:

- Pair Sprint 28 -> Sprint 30 -> Sprint 33 as the frontend/auth debugging chain.
- Pair Sprint 29 -> Sprint 32 as the persistence/identity chain.

Those two chains are the highest-signal retrospective material from this block.
