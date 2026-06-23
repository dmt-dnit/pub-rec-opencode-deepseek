# Sprint 6 handoff — Track A close-out, round 4

Snapshot date: 2026-06-23. This is a fast coherence pass (diffs read against each task's acceptance criteria), not a re-run of Codex's own checks — per the established role split, deep verification (running `mvn test`, `npm audit`, browser smoke tests, etc.) is Codex's job as independent reviewer, not a duplicate step here.

## Status by task

| Task | Diff-level status | Notes |
|---|---|---|
| J-1 — fix skipped-test reporting | **Matches the brief** | `InventoryIntegrationTest.java`: the Docker-availability check moved out of `@BeforeAll`'s test-suppressing path into `Assumptions.assumeTrue(...)` inside the `@Test` method itself, with `dockerAvailable` as a separate flag guarding `kafka.start()`/`stop()`/the dynamic property source. This is exactly the fix the brief asked for — should make JUnit5 report a real skip instead of `tests="0"`. Not independently re-run here. |
| J-2 — Angular twin-consistency + smoke test | **Diff present, claims not independently verified** | `angular.json` in both UIs now has `buildTarget`; `inventory-ui/angular.json` gained the `schematics` block to match `order-ui`. Both lockfiles changed substantially. `CLAUDE.md`'s own rewritten snapshot claims `npm audit`: 14 total/6 high (all transitive, requiring Angular 22) and that dev servers respond on 4200/4201 with SPA routes confirmed — these are exactly the claims Codex's review should independently confirm, not something re-run here. |
| J-3 — fix `CLAUDE.md` status snapshot | **Matches the brief** | Snapshot now correctly tracks Sprint 4 (partial progress)/Sprint 5 (rejected)/Sprint 6 (J-1–J-3) instead of the old Sprint-4-framed text, uses the right task IDs, and the Track B pointer remains `docs/backlog/sprint-1.md`. Doesn't overclaim acceptance — states this is pending Codex's review. |

## What Codex should focus on

- Whether `J-1`'s Surefire report actually now shows `skipped="1"` (not `tests="0"`) with no Docker, and whether the positive path (real container, test executes and passes) was verified.
- Whether `J-2`'s claimed audit numbers and browser smoke-test results actually hold — those claims are unverified by this handoff.
