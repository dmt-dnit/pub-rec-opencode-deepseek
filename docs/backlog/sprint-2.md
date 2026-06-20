# Sprint 2 backlog: Track A stabilization

**Source:** Codex review verdict on Sprint 1 Track A — `reviews/sprint-1-track-a-review.md`, dated 2026-06-20. Verdict: **reject**. Four blockers, three should-fixes, confirmed by re-reading every cited file before writing this backlog (not taken on faith).

## Why this sprint exists

Track A (the Order/Inventory domain pivot) was implemented but doesn't actually work end to end: the UIs can't reach the renamed APIs, the live status update silently fails at runtime, and one backend module's test suite doesn't pass. Per ADR-0001, **Track B (hardening) was always gated on Track A actually landing** — it didn't, so sprint 2 is entirely about finishing Track A, not starting Track B. None of the original Track B tasks (`docs/backlog/sprint-1.md`) start until every task below is verified.

Two items from Codex's recommendations are pulled in here rather than deferred, because they're direct, concrete consequences of this review rather than speculative hardening: dependency remediation (F-6) and Java toolchain pinning (F-7) — the review's own verification run was made unreliable by an unpinned Java 25 host default colliding with Mockito/Byte Buddy.

## Process note

Sprint 1 ran multiple agent tasks against one shared working tree rather than isolated worktrees/branches per task. Some of what's broken here may be a consequence of that (incomplete renames are exactly the kind of thing concurrent edits to the same files produce). **Run each sprint-2 task in its own worktree or branch this time**, even though most of these fixes touch disjoint files — F-5 in particular should run last and alone, since it's a repo-wide grep-and-clean pass that's only meaningful once everything else is actually fixed.

## Tasks

Recommended order: **F-7 and F-1 first** (foundational, fast, unblock real verification) → **F-2, F-3, F-4 in parallel** (the actual bug fixes, disjoint files) → **F-6 anytime in parallel** → **F-5 last** (repo-wide cleanup + doc rewrite, only meaningful once the above are true).

| Task | What | Severity it resolves |
|---|---|---|
| F-1 | Fix Angular dev-proxy routing for both UIs | Blocker 1 |
| F-2 | Fix `order-service`'s entity-over-the-wire serialization bug (WS *and* REST) | Blocker 2 |
| F-3 | Fix `inventory-service`'s test Kafka listener type conflict | Blocker 3 |
| F-4 | Resolve `customerEmail` ownership: derive from JWT, drop the dead request-body/UI field | Should-fix 1 |
| F-5 | Repo-wide leftover-rename sweep + `CLAUDE.md` rewrite | Should-fix 2, 3 |
| F-6 | Angular dependency remediation (both UIs) | Blocker 4 |
| F-7 | Pin Java toolchain across all 4 Maven modules | Verification-environment risk Codex hit |

Each task's full brief is in `docs/backlog/tasks/sprint-2/`. Track B does not start until all seven are merged and re-verified against the same checks Codex used (re-run `mvn test` on every backend module, `npm run build` on both UIs, and a real browser smoke test of the order-placement flow).
