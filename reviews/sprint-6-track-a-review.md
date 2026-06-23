# Sprint 6 review: Track A close-out, round 4

Snapshot date: 2026-06-23. Verified by reading the Sprint 6 commit (`f3f8c4f`) and rerunning the relevant checks directly in Codex's environment where possible.

## Must fix

- `J-2` still does not clear the dependency/vulnerability bar. I reran `npm.cmd audit --json` in both `order-ui` and `inventory-ui`; both still report `14` total vulnerabilities with `6` high. I also reran `npm.cmd outdated --json`; both apps still lag current published packages (`@angular/cdk` and `@angular/material` latest `22.0.2`, `typescript` latest `6.0.3`, `zone.js` latest `0.16.2`). `CLAUDE.md:52` presents the current state as an accepted end point by asserting the remaining highs "require Angular 22", but the audit output itself only proves unresolved transitive build-tooling vulnerabilities under the current Angular 21 toolchain, not that Angular 22 is the sole confirmed fix. This is still not vulnerability-free or fully current.
- The required browser smoke test is still not evidenced at the level the brief asked for. `docs/backlog/tasks/sprint-6/J-2-finish-angular-verification.md:34-36` required login, dashboard, order placement, and stock view to be actually performed and described. Instead, `CLAUDE.md:52` says only "Dev servers respond on 4200/4201; SPA routes confirmed," and `docs/backlog/sprint-6-handoff.md:10,16` explicitly says those smoke-test claims were not independently verified in the handoff. Route availability is not the required end-to-end flow verification.

## Should fix

- `CLAUDE.md` still has stale framework-version text outside the status snapshot. The repo overview and frontend architecture sections still describe the UIs as Angular 18 SPAs at `CLAUDE.md:12` and `CLAUDE.md:85`, which is now false after the Angular 21 migration. `J-3` fixed the snapshot, but the broader repo guidance is still inconsistent.

## Tests/checks missing

- `J-1`'s skip-path fix is real. The current Surefire XML at `inventory-service/target/surefire-reports/TEST-com.example.inventoryservice.InventoryIntegrationTest.xml:2` now shows `tests="1"` and `skipped="1"` instead of the old `tests="0"` / `skipped="0"`, and the code change in `inventory-service/src/test/java/com/example/inventoryservice/InventoryIntegrationTest.java` matches the brief.
- I could not verify the positive Testcontainers path locally because there is no working container runtime in this environment: `docker` is not installed and local `podman` cannot connect. That means I could not prove a real Docker-backed passing run for `J-1`; I could only verify the no-Docker skip path.
- Both Angular production builds now pass in the reviewer environment.
- The lockfile drift called out in Sprint 5 is resolved materially. A direct diff between `order-ui/package-lock.json` and `inventory-ui/package-lock.json` now differs only in the root package name lines.

## Task scorecard

- `J-1`: PASS on the skip-reporting fix; positive path remains unverified in this environment.
- `J-2`: PARTIAL.
- `J-3`: PARTIAL.

## Verdict

Reject. Sprint 6 fixes the JUnit skip-reporting bug and cleans up the frontend twin-consistency work, but it still does not close Track A because the frontend remains vulnerable/outdated and the required browser smoke test is still not evidenced at the requested level.
