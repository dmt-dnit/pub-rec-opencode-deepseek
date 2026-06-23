# Sprint 5 handoff — Track A close-out, round 3

Snapshot date: 2026-06-23. Verified by reading the actual diffs and running checks directly — not by trusting "DeepSeek finished" or the self-report written into `CLAUDE.md`'s status snapshot.

**ID mismatch note:** this work was implemented against `docs/backlog/sprint-5.md`'s tasks (`I-1`–`I-4`), but `CLAUDE.md`'s own status-snapshot update (presumably written as part of `I-4`) describes it as continuing "Sprint 4" and uses the old `H-1`–`H-5` IDs throughout. The table below maps to the Sprint 5 IDs that were actually briefed; cross-reference by content, not by the ID label currently sitting in `CLAUDE.md`.

## Status by task

| Task | Status | Evidence |
|---|---|---|
| I-1 — fix `inventory-service`'s skipped-test reporting | **Not started** | `git diff --stat inventory-service/` is empty. No change anywhere in the module. `CLAUDE.md`'s snapshot still describes the *old* Sprint 4 behavior ("test skips gracefully (exit 0)") without mentioning the `tests="0"`/`skipped="0"` reporting bug Codex flagged — i.e. the snapshot itself doesn't acknowledge this task exists. |
| I-2 — finish the Angular upgrade (both UIs, audit floor, smoke test) | **Mostly done** | Both `order-ui` and `inventory-ui` are now on identical `@angular/*` ^21.2.17 / `@angular/cli` ^21.2.16 versions (twin-consistency requirement finally satisfied — confirmed via diff, not just claimed). Re-ran `npm audit` myself directly against the committed lockfiles: both UIs report **identical** `14` total (`6 high`, `4 moderate`, `4 low`) — exact match to the `30 → 6` high-severity claim in `CLAUDE.md`. **Not independently verified here:** the "both UIs build clean" claim — a fresh `npm install` was in progress when interrupted; the existing lockfile-based audit is solid evidence, but `npm run build` wasn't re-confirmed by me on the new versions. **Still missing:** no evidence anywhere of the manual browser smoke test the brief required (login, dashboard, order placement / stock view) — this has now been required and skipped across three consecutive sprints. |
| I-3 — investigate Mockito self-attach | **Fallback applied, root cause still not found** | `order-service/pom.xml`'s surefire `argLine` now includes `-Djdk.attach.allowAttachSelf=true` (correctly appended, not replacing `-Xmx512m`) — this is the brief's documented fallback path, used after `CLAUDE.md` reports "no conflicting resource found in any jar" (i.e. the classpath-scan investigation step was attempted, root cause not found). I ran `OrderEventIntegrationTest` three times directly in this environment (standard OpenJDK 21, not the OpenJ9/Semeru build Codex uses): first run hit 3 transient errors (Spring context load failure, looked like flakiness, not reproducible), two subsequent clean runs both passed 3/3. The log still shows `Mockito is currently self-attaching to enable the inline-mock-maker` on every run — the override file is still not controlling mock-maker selection, exactly as before — but the new flag makes that self-attach succeed instead of crashing. **Not independently verified here:** the specific claim "All 3 tests pass on OpenJ9 (Semeru 21.0.6)" — I don't have that JDK vendor available; my confirmation is on standard OpenJDK 21 only, which is consistent with but not equivalent to that claim. |
| I-4 — fix `CLAUDE.md` status snapshot | **Partially done, with a real inaccuracy** | The Track B pointer is correctly fixed to `docs/backlog/sprint-1.md` (confirmed). However the snapshot frames this entire round as "Sprint 4 (Track A close-out, round 2)" rather than Sprint 5/round 3, doesn't mention `I-1` at all (silently omits that the skipped-test-reporting fix was never attempted), and doesn't cite `reviews/sprint-4-track-a-review.md`'s actual findings by name. This is the same kind of premature/incomplete status-snapshot problem this task has had in every prior sprint it was attempted. |

## What's left

1. `I-1` has not been attempted at all — `inventory-service`'s Testcontainers test still reports `tests="0"` instead of an honest skip when no Docker/Podman is available.
2. `I-2`'s remaining gaps: confirm `npm run build` succeeds on both UIs post-upgrade (not yet independently verified), and do the manual browser smoke test that's been required for three sprints running.
3. `I-3`'s root cause is still unknown — the fallback makes the symptom (JVM crash on certain OpenJ9 builds) go away, but Mockito still isn't using the intended `mock-maker-subclass` override for reasons nobody has actually identified.
4. `I-4` needs a second pass once `I-1`–`I-3` are actually closed, written against the correct sprint number and citing this review.

## Verification commands run directly in this environment

- `npm audit --json` in `order-ui` and `inventory-ui` (against committed lockfiles, no reinstall needed) — both report identical `14` total / `6` high.
- `./mvnw -q test` in `order-service`, three times — final state: 3/3 passing, exit `0`, self-attach warning still present but non-fatal.
- `git diff --stat` across all changed modules to confirm which tasks actually have any code change at all.

Not run in this pass (time/scope-bounded, flagged rather than silently skipped per this repo's own verification-standards rule): fresh `npm install` + `npm run build` on the Angular 21 lockfiles in both UIs; any check on IBM Semeru/OpenJ9 specifically (not available in this environment); the manual browser smoke test (requires a running browser session, not something this verification pass covers).
