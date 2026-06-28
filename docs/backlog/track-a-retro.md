# Track A Retrospective

**Scope:** Sprints 1–13, Track A close-out (Order/Inventory domain reshape + browser smoke gate)  
**Closed:** 2026-06-28 — Sprint 13 approved by Codex  
**Format:** Async, artifact-driven. Three perspectives written independently; synthesis at the end.

---

## Coordinator perspective (Claude Code)

### What worked

**Pre-review check script** (`scripts/pre-review-check.sh`) — added after the Sprint 4 round 2 failure where 2 of 5 tasks were unstarted and reached Codex with no commit checkpoint. After that it caught every incomplete sprint before it became a wasted review round.

**Handoff doc format** — per-task evidence tables (commit SHA, file:line, before/after) gave Codex a precise starting point for independent verification. Reviews were faster and more actionable when the handoff was specific.

**Isolated worktrees per task** — prevented the Sprint 1 cross-contamination where concurrent edits on one shared tree produced incomplete renames across files. Every sprint after that used worktrees and no cross-task interference occurred.

**The browser gate** — frustrating to close, but correct to have. Every Angular CD issue that would have silently broken the demo in a user's browser was caught by the Playwright gate. The gate paid for itself.

**Dependency currency check at sprint start** — Angular 18→22 migration happened proactively rather than reactively. Not having to do it under pressure was worth the early sprint slot it consumed.

**"Show actual output, not asserted Pass" rule** — proxy-signal false positives dropped significantly in the second half of Track A after this was made explicit. Codex's early reviews were dominated by unverified Pass claims; later ones weren't.

### What failed

**Coordinator introduced an Angular CD bug (Sprint 9 M-1)** — Removed `ChangeDetectionStrategy.Eager` from both dashboard components as "code hygiene," not understanding that Angular 22 computes `onPush` as `changeDetection !== ChangeDetectionStrategy.Eager`. When the field is absent, `undefined !== 1` → `true` → OnPush. Cost two full sprint rounds (N-1 to restore it, then another round to discover inventory-ui's mat-table needed `MatTableDataSource`). Root fix was straightforward once the actual Angular 22 source was read.

**Coordinator-direct code changes (Sprint 11 O-1, O-2)** — Both fixes were targeted and passed Codex, but they bypassed the agent self-report / coordinator diff-check / Codex code-review chain entirely. The workflow exists because self-reports aren't trusted; the coordinator self-reporting is no different. Rule now written into CLAUDE.md.

**Claude subagents consistently edited files without committing** — Every agent worktree task required the coordinator to manually apply the verified diff to main afterward. The isolation worked; the commit step didn't. This happened in every sprint that used Claude agents as fallback implementers.

**Lockfile regeneration with `--package-lock-only` against stale node_modules (Sprint 10 N-3)** — `npm install --package-lock-only` reflects what is in `node_modules`, not just `package.json`. The Windows-side node_modules still had `sockjs-client` installed (removed from `package.json` in Sprint 8 but not from node_modules in WSL). The freshly-generated lockfile re-included it. Needed a second sprint slot to fix surgically.

**Three rounds of Playwright locator failures (Sprints 11–13)** — All caused by the same underlying gap: neither the coordinator nor any implementer could run the smoke test against the real DOM. The DOM structure (outer "Orders" `mat-card` wrapping inner order `mat-card`s) was readable from the Angular template — it just wasn't read before writing the locators. Rule for Track B: read the actual template before writing any E2E selector.

**Sprint 4 round 2 / incomplete handoffs early on** — Reaching Codex with unstarted tasks and no commit checkpoint was the most expensive single failure mode. The pre-review check script was the fix, but it took until Sprint 4 to add it.

### Root causes

**No browser access for coordinator or implementers** — Angular rendering bugs, STOMP zone issues, and Playwright selector failures all required Codex to catch them in a real browser. Neither Claude Code (WSL) nor opencode can run `ng serve` and validate in Chromium. This is structural; the mitigation is to flag browser-required acceptance criteria explicitly in the brief and route them to Codex's verification step rather than claiming a proxy pass.

**Persistent local H2 database across smoke runs** — `order-service` uses H2 which survives between dev server restarts. The smoke test saw accumulated `PENDING` and `CONFIRMED` orders from prior runs, causing Playwright strict-mode violations when selectors matched multiple elements. Three sprint rounds wasted on locator fixes that would have been caught immediately with a fresh DB on each run.

**Unclear coordinator-vs-agent boundary until Sprint 11** — The "coordinator-direct is only for doc typos" rule was implicit until Dimitri called it out. It's now in CLAUDE.md under "Who implements what." It wasn't followed consistently before it was written down.

**WSL ↔ Windows tooling split** — Angular builds (`ng build`, `npm install` with node_modules writes) only work from Windows PowerShell; git and bash scripts only from WSL. This split created persistent uncertainty about what could be verified where, and led to the `--package-lock-only` workaround that produced the stale lockfile.

**Angular 22 behavioral changes undocumented in migration guides** — `ChangeDetectionStrategy.Eager` as the CheckAlways opt-in (replacing `Default`) and the `onPush` computation change are not in Angular's upgrade guide. Had to reverse-engineer from `node_modules/@angular/core/fesm2022/_debug_node-chunk.mjs`. For Track B: when Angular behavior doesn't match expectations, read the compiled source before writing a workaround.

---

## Reviewer perspective (Codex)

*— to be filled in by Codex —*

---

## Implementer + product perspective (Dimitri / opencode)

*— to be filled in —*

---

## Synthesis — Track B working rules

*To be written after all three perspectives are in. Placeholder structure:*

### What we're keeping
- Pre-review check mandatory before every Codex handoff, no exceptions
- Handoff doc must state: exact commits, task done/not-done per item, actual command output (not summarized), checks that couldn't run and why, whether browser validation is required
- Isolated worktree per task; coordinator verifies the diff was actually committed before declaring done
- "Show actual output, not asserted Pass" — if the environment can't run the check, say so

### What we're changing
*(to be determined from all three perspectives)*

### Open risks going into Track B
- Full `npm audit` reports 8 dev-tooling advisories per UI — unresolvable at Angular 22.0.4 floor; not a runtime exposure but should be rechecked when Angular 22.x patches ship
- Angular webpack builder deprecation warning — will become a blocker when Angular removes webpack support; track for next Angular version upgrade
- Mockito/Java agent self-attach warning (OpenJ9 environments) — accepted caveat, documented in Sprint 2 review
- Testcontainers/Surefire shutdown warning in inventory-service — exit 0 but noisy; should be cleaned up in B-3
- `order-service` H2 accumulates orders across runs — B-3 (Testcontainers) should enforce test isolation so the smoke test doesn't depend on a fresh database
