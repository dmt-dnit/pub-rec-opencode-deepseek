# Sprint 5 review: Track A close-out, round 3

Snapshot date: 2026-06-23. Verified by reading the committed code and rerunning the relevant checks directly in Codex's environment instead of relying on status text.

## Must fix

- `I-1` is still open. `inventory-service/src/test/java/com/example/inventoryservice/InventoryIntegrationTest.java:52-57` still puts `Assumptions.assumeTrue(...)` in `@BeforeAll`, and the current Surefire report at `inventory-service/target/surefire-reports/TEST-com.example.inventoryservice.InventoryIntegrationTest.xml:2` still records `tests="0"` and `skipped="0"` when no Docker runtime is available. That is still silent omission, not an honest reported skip.
- `I-2` is only partial. Both UIs are now on Angular 21 in `order-ui/package.json:15,25,29,32` and `inventory-ui/package.json:15,25,29,32`, and I reran `npm.cmd run build` successfully in both apps. But the resolved lockfiles still diverge: `order-ui/package-lock.json:12032-12033` vs. `inventory-ui/package-lock.json:12052-12053` (`semver`), `order-ui/package-lock.json:12669-12670` vs. `inventory-ui/package-lock.json:12689-12690` (`string-width`), and `order-ui/package-lock.json:13050-13051` vs. `inventory-ui/package-lock.json:13069-13070` (`undici`). There is also config drift: `order-ui/angular.json:35-58` contains a `schematics` block that `inventory-ui/angular.json` does not have. I reran `npm.cmd audit --json` in both apps and both currently report the same `14` total vulnerabilities (`6 high`, `4 moderate`, `4 low`), so the major-version work is real progress, but the twin-consistency acceptance bar is still not met at the lockfile/config level. I also found no committed evidence of the required browser smoke test; `docs/backlog/sprint-5-handoff.md:12,19,29` still calls that out as missing.
- `I-4` is still inaccurate. `CLAUDE.md:48-57` still frames this round as "Sprint 4" using `H-1` through `H-5`, says `H-2` "test skips gracefully" even though the Surefire report still shows `tests="0"` / `skipped="0"`, and presents `H-3` as fully closed even though the remaining Angular drift and missing smoke-test evidence are real.

## Should fix

- `I-3` no longer needs carryover as an active blocker. `order-service/pom.xml:106` now includes the documented fallback `-Djdk.attach.allowAttachSelf=true`, and the latest Surefire report at `order-service/target/surefire-reports/TEST-com.example.orderservice.OrderEventIntegrationTest.xml:2,1454` shows both that all 3 tests pass and that Mockito still self-attaches. That means the root cause is still unknown, but the accepted fallback path is in place and working in the reviewer environment.

## Tests/checks run

- `.\mvnw.cmd -q test` in `shared-model`, `auth-server`, `order-service`, and `inventory-service` under IBM Semeru/OpenJ9 21.0.6: pass.
- `npm.cmd run build` in `order-ui` and `inventory-ui`: pass.
- `npm.cmd audit --json` in `order-ui` and `inventory-ui`: both report `14` total vulnerabilities (`6 high`, `4 moderate`, `4 low`).
- Re-read the generated Surefire XML for `inventory-service` and `order-service` to verify the current skip-reporting and Mockito runtime behavior directly.

## Verdict

Reject. Sprint 5 made real progress, especially the Angular 21 migration and the OpenJ9-safe Mockito fallback, but it still does not close Track A because skipped-test reporting, Angular twin-consistency verification, and the `CLAUDE.md` status snapshot are not yet actually finished.
