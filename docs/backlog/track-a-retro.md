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

### What worked from the reviewer seat

**Artifact-driven handoffs made review possible.** The strongest handoffs named exact commits, task IDs, changed files, expected commands, and known caveats. That let review start from evidence instead of reconstructing intent. The Sprint 9–13 handoffs were materially easier to verify than the early sprint reports because they separated "implemented," "verified," and "cannot verify here."

**Review files created durable feedback.** Writing `reviews/sprint-N-*.md` in the repo turned each failed review into executable backlog input for the next sprint. That mattered for Sprints 11–13: the next fix could quote the exact failing selector, failure output, and required acceptance gate instead of relying on chat memory.

**The real browser gate found the bugs that mattered.** Static checks, Angular builds, and TypeScript compile all passed while the UI was still unusable or the E2E test was still non-idempotent. The browser run caught the actual user-visible failures: Angular change detection not updating, Material table timing, and Playwright locators matching persisted orders.

**Dependency checks were useful when split by runtime vs tooling.** `npm audit --omit=dev = 0` gave a clear production-risk answer, while full audit output preserved the dev-tooling caveat. Treating those separately avoided both extremes: ignoring advisories entirely or blocking runtime work on Angular-toolchain advisories with no forward fix.

**Running on the Windows side exposed real environment constraints.** Several checks only became meaningful from PowerShell with the actual installed `node_modules`, dev servers, browser, Docker/Testcontainers, and Java versions. That made Codex's role valuable as an independent verifier rather than just a second static reviewer.

### What failed from the reviewer seat

**Too many reviews were asked to validate work that had not actually run in the target environment.** The repeated pattern was "proxy checks pass, Codex please confirm the real thing." That is acceptable when the handoff explicitly says so, but expensive when the sprint is presented as complete. Sprint 4 and the Track A browser rounds were the clearest examples.

**E2E selector fixes were designed from intent instead of DOM facts.** Sprint 11 used global `.order-header`; Sprint 12 used `mat-card.filter({ has: .order-header })` and missed the outer-card nesting. Both were avoidable by reading the rendered/template structure first and by reasoning about Playwright strict mode against accumulated data.

**The review boundary sometimes blurred into first-time QA.** Codex repeatedly had to discover basic failing behavior that should have been identified before handoff. A reviewer should be proving or disproving a claim, not being the first tool to exercise the acceptance path unless the handoff explicitly marks that path as Codex-only.

**Environment mismatch created noisy false starts.** Examples: root `mvnw.cmd` did not exist because wrappers are per module; Maven failed under JDK 25 until rerun under Java 21; inventory Testcontainers exited 0 while logging Surefire shutdown warnings; npm `latest` dist-tags reported lower Angular versions. These are manageable, but handoffs should call out the expected environment and known noise.

**Uncommitted or generated files add review risk.** `.claude/`, `e2e/test-results`, `dist`, and agent worktree leftovers repeatedly appeared during verification. Most were harmless, but every review had to distinguish intended code changes from tooling residue. Track B should keep generated artifacts out of the diff and make "clean except known untracked paths" part of the handoff.

### Root causes visible to the reviewer

**Acceptance criteria were sometimes written as implementation steps, not observable outcomes.** "Use this locator" is weaker than "the smoke test passes twice against a non-empty database." Track B briefs should define the observable behavior first, then suggest implementation paths second.

**Browser-required work was not labelled early enough.** If no implementer can run a browser, the brief should say "Codex-only verification required" up front and the handoff should avoid claiming full pass. That keeps the review from looking like a rejection when it is actually the first available real validation.

**Persistent state was treated as accidental instead of part of the test environment.** The non-empty H2 database surfaced a valid reliability requirement: smoke tests must be idempotent across repeated runs. Track B tests should assume prior data exists unless the task explicitly resets state.

**Dependency and toolchain posture needs a stable rubric.** Production vulnerabilities, dev-tooling advisories, outdated dist-tags, builder deprecations, Java warnings, and Testcontainers noise should not be re-litigated every sprint. Track B should carry a short known-caveats table and update it only when the signal changes.

### Track B reviewer asks

- Every handoff should include exact commits, changed files, commands actually run, commands not run, and why.
- Any browser/UI task should include the actual template/DOM selector reasoning, not just the intended user flow.
- Any E2E task should state whether it was run against a clean or dirty database; dirty should be the default assumption.
- Any dependency task should report production audit separately from full dev audit.
- Generated artifacts should be cleaned before handoff, with final `git status --short` included.

---

## Implementer + product perspective (Dimitri / opencode)

- impressed by the workload processed and thankful for the efforts
- struggling with ways to make some project choices survice context shrinks, terminal crashes, machine reboots and other things that could go 'wrong' somewhere halfway to process 
- would be nicer and faster if we could close the simple copy paste loop between planner and reviewer and find a way to publish-receive these "I'm done, take over" messages. Maybe deploy the app we are making and use that to send those messages, that would be really ironic and kinda special, but don't let my 'human look' get in the way of choosing the cleanest, easiest and safest way to organise this, aiming to only needing my intervention when it is truly adds value or is needed for safety (like deploying to production)
- wondering if using opencode+deepseek as workhorse is worth the extra hassle of safeguarding the code before handing it over and having to develop with a mimiced version. If we should shift to claude-cli to spin up task implementing agents with deepseek or a lower tier model of (claude/openai/3th party), would the loss of 'a extra pair of eyes/hands/intelligence' way up against the 'hassle'
 
---

## Synthesis — Track B working rules

### What we're keeping

- **Pre-review check mandatory** (`scripts/pre-review-check.sh <N>`) before every Codex handoff, no exceptions.
- **Handoff doc format** — exact commits, done/not-done per task, actual command output (not summarized), which checks couldn't run and why, whether browser validation is required, `git status --short` confirming clean tree.
- **Worktree isolation per task** — coordinator must verify the worktree branch has new commits before declaring the agent done; if not, read the diff from the worktree, apply to main manually, and note this in the handoff.
- **"Show actual output, not asserted Pass"** — if the environment can't run the check, state the limitation explicitly; a stated limitation is more useful than an unverified pass.
- **Production audit separate from full audit** — `npm audit --omit=dev = 0` is the pass/fail signal; full audit output is reported as a caveat, not a blocker unless new runtime-path advisories appear.

### What we're changing

**Acceptance criteria as observable outcomes, not implementation steps.**  
"The smoke test passes twice consecutively against a non-empty database" is the acceptance criterion. "Use this locator" is implementation guidance that belongs in the brief body, not the acceptance bar. Codex will reject criteria it can only verify by reading intent.

**Read the actual template before writing any E2E selector.**  
Three Playwright rounds were wasted because selectors were designed from intent rather than the rendered DOM. For any E2E task, the brief must include the relevant template snippet and explain why the proposed selector is unambiguous against a non-empty database.

**E2E tests assume a dirty database by default.**  
Smoke tests must be idempotent across repeated runs. Selectors must distinguish the *newly placed* order from prior data, not just *an* order. This is now a first-class acceptance criterion on every E2E task, not an afterthought.

**Known-caveats table replaces per-sprint re-litigation.**  
Standing caveats (npm dev-tooling advisories, Angular builder deprecation, Java agent warnings, Testcontainers shutdown noise) are documented once below. Handoffs reference the table; they don't re-argue whether each caveat is a blocker. The table is updated only when the signal changes.

**Browser-required tasks are flagged explicitly in the brief.**  
If no implementer or coordinator can run the UI in a browser, the brief says so and marks browser verification as Codex-only. The handoff does not claim a pass on that acceptance criterion — it says "Codex-only verification required."

**Implementation agent choice is case-by-case.**  
Default for Track B: Claude haiku agents (worktree-isolated) for mechanical tasks (targeted file edits, lockfile fixes, config changes), Claude sonnet/opus for logic-heavy work. OpenCode+DeepSeek remains an option when a task benefits from a genuinely different implementation approach or exploratory reasoning. The coordinator decides per task and notes the choice in the handoff. This removes the "copy-paste to get opencode to start" friction for routine tasks without losing the option for complex ones.

**Context resilience is maintained actively.**  
CLAUDE.md status snapshot is updated at the end of every sprint (not just when Track A milestones close). Memory files cover key decisions and workflow rules. Task briefs are self-contained enough that a fresh session can implement them without reading the full sprint history.

### On closing the coordination loop (Dimitri's ask)

The current loop — Claude writes handoff → Dimitri pastes to Codex → Codex writes review → Dimitri pastes back — has two manual steps that could be automated. Clean options in order of effort:

1. **GitHub Actions notification (low effort, Track B B-5)** — when a `reviews/sprint-N-*.md` file is committed and pushed, a workflow step posts to Slack or a webhook. Codex and Claude both work from the repo; the CI event closes the loop without any new infrastructure.
2. **Codex writes reviews directly to the repo via git** — if Codex has push access or creates PRs, the review file lands in the repo without a copy-paste step. The coordinator picks it up at the start of the next session by reading the file.
3. **The app itself (ironic path)** — wire a Kafka event or REST call when a review is ready; inventory-ui displays it. Fun, but adds real production risk to a coordination concern. Not recommended until the app is deployed and stable.

Option 1 is the right Track B target. Option 2 is achievable now if Codex is willing to push directly. Option 3 is a Track C idea.

### Open risks going into Track B

| Risk | Severity | Status |
|------|----------|--------|
| `npm audit` — 8 dev-tooling advisories per UI (piscina/vite under `@angular-devkit/build-angular`) | Low — build tooling only, not runtime | No forward fix at Angular 22.0.4 floor; recheck when Angular 22.x patches ship |
| Angular webpack builder deprecation warning | Medium — will block when webpack support is removed | Track for next Angular version upgrade sprint |
| Mockito/Java agent self-attach warning (OpenJ9) | Low — accepted caveat | Documented in Sprint 2 review; no action needed unless test suite breaks |
| Testcontainers/Surefire shutdown warning in inventory-service | Low — exit 0, just noisy | Clean up in B-3 |
| `order-service` H2 accumulates orders across smoke runs | Medium — makes E2E non-idempotent without careful selectors | B-3 (Testcontainers) should enforce per-test DB isolation; smoke test already handles dirty DB via count-based selectors |
