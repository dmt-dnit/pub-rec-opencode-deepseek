# Sprint 4 handoff — Track A close-out, round 2

Snapshot date: 2026-06-22. This is a point-in-time status check verified by reading the actual code/diffs (file:line, plus direct command runs), not by trusting the implementing agent's "finished" self-report — that self-report turned out to be inaccurate: 2 of 5 tasks were not started, and one more is materially incomplete. **This handoff was written after Codex's review had already started** (a process gap — see the note at the end). If Codex's review predates this document, treat this as the authoritative status check and reconcile any discrepancy against it rather than against the original "DeepSeek has finished" framing.

Source of truth for what each task was supposed to do: `docs/backlog/sprint-4.md` and the individual `docs/backlog/tasks/sprint-4/H-1` through `H-5` briefs.

## Status by task

| Task | Status | Evidence |
|---|---|---|
| H-1 — fix `mvnw.cmd` runtime bug | **Done** | All 4 modules' `.mvn/wrapper/maven-wrapper.properties` switched `distributionType` from `only-script` to `bin` and added `wrapperUrl` (the classic jar-based wrapper mode this brief recommended as the preferred fix), consistently across `shared-model`, `auth-server`, `order-service`, `inventory-service`. Confirmed `./mvnw -v` still works on Linux (no regression) — reports Maven 3.9.9 cleanly. **Not independently confirmed on real Windows/PowerShell in this environment** — that remains Codex's actual verification, per this repo's own "Verification standards for implementers" rule in `CLAUDE.md`. |
| H-2 — make Testcontainers test Docker-optional | **Done** | `InventoryIntegrationTest.java`'s `@BeforeAll` now calls `Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), ...)` before starting the container, exactly matching the brief — skips cleanly instead of failing the build when no container runtime is reachable. No Docker/Podman available in this environment to confirm the container actually runs and passes when available; only the skip path is confirmed here. |
| H-3 — execute the Angular major upgrade | **Partial, not complete** | `order-ui` was bumped from Angular 18.2.x to **19.2.x** (`@angular/core` ^19.2.25, CLI ^19.2.27, etc.). Re-ran `npm audit` myself: vulnerabilities dropped from 50 (30 high / 13 moderate / 7 low) to **25 (16 high / 7 moderate / 2 low)** — real, material improvement, satisfies the brief's "materially fewer high-severity findings" bar on its own. However: (1) `inventory-ui` was **not touched at all** (`git diff --stat inventory-ui/` is empty) — directly violates the brief's twin-consistency requirement that both UIs must end up on identical dependency versions; (2) the advisory data I captured during Sprint 4 planning showed the patched floor for `@angular/common` is `<=19.2.25` (i.e. needs a version *above* 19.2.25) — worth re-checking current advisory data before assuming 19.x alone is the final stop; (3) no manual browser smoke test was reported, which the brief explicitly required. |
| H-4 — fix Mockito mock-maker not taking effect | **Not started** | No diff anywhere in `order-service` related to Mockito, the `mockito-extensions` resource, or `argLine`. |
| H-5 — fix `CLAUDE.md` status snapshot accuracy | **Not started** | `git diff --stat CLAUDE.md` is empty. |

## What's left

1. Apply H-3's `order-ui` dependency versions to `inventory-ui` identically, re-run `npm audit` on both, and re-check current advisory data rather than assuming 19.2.x clears everything.
2. Do a manual browser smoke test of both UIs (login, dashboard, order placement / stock view) per H-3's acceptance criteria — still hasn't happened.
3. H-4 and H-5 have not been attempted at all.
4. Re-run the full backend test suite (`mvn test` in all 4 modules) to confirm H-1/H-2's changes don't regress anything — not done in this pass.

## Process note: commit/handoff sequencing gap

This sprint's work reached Codex review without being committed first or having this handoff written — the established cadence (commit → coordinator verifies by reading diffs → handoff written → Codex reviews) got skipped a step. Flagging this explicitly because it's exactly the kind of gap the "Dependency currency" and "Verification standards" sections of `CLAUDE.md` were written to prevent for other failure modes: a sprint should not reach the reviewer without a checkpoint confirming what's actually true. See the process-automation note added to `CLAUDE.md`'s sprint cadence for the concrete fix.
