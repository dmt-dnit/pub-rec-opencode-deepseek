# Sprint 4 Track A Review

Date: 2026-06-23
Reviewer: Codex
Repo reviewed: `C:\projects\pub-rec-opencode-deepseek`
Scope: `docs/backlog/tasks/sprint-4/H-1` through `H-5`
State reviewed: current dirty worktree on top of commit `a57824d`

## Must fix

- `H-2` is still only partially closed. The guard in `inventory-service/src/test/java/com/example/inventoryservice/InventoryIntegrationTest.java:52-57` does stop the no-Docker environment from failing the build, and `.\mvnw.cmd -q test` now exits `0` under IBM Semeru/OpenJ9 21.0.6. But the acceptance criteria required the Kafka test to be reported as skipped, not hidden. The actual Surefire artifact at `inventory-service/target/surefire-reports/TEST-com.example.inventoryservice.InventoryIntegrationTest.xml:2` still records `tests="0"` and `skipped="0"`, so JUnit is not reporting a skipped test case.

- `H-3` is now real progress, but it is not fully complete. Both apps were upgraded to Angular 21 and both builds pass, but the lockfiles still do not match exactly and I found no committed evidence of the required browser smoke test. The root manifests are aligned at `order-ui/package.json:11-32` and `inventory-ui/package.json:11-32`, but the lockfiles diverge in resolved dependency versions, for example `order-ui/package-lock.json:12032-12035` (`semver` `7.7.1`) vs `inventory-ui/package-lock.json:12052-12055` (`7.6.3`), `order-ui/package-lock.json:12669-12672` (`string-width` `7.2.0`) vs `inventory-ui/package-lock.json:12689-12692` (`8.2.1`), and `order-ui/package-lock.json:13050-13053` (`undici` `6.27.0`) vs `inventory-ui/package-lock.json:13069-13072` (`7.24.4`). The last committed handoff still says the smoke test was not done at `docs/backlog/sprint-4-handoff.md:19-20`, and I found no newer artifact replacing that claim.

## Should fix

- `H-5` is improved but still inaccurate. The Track B pointer is fixed and the Sprint 3 rejection is now described correctly at `CLAUDE.md:48-57`, but the snapshot still overstates Sprint 4. `CLAUDE.md:52` says the Docker-optional test "skips gracefully", which is stronger than the current `tests="0"` / `skipped="0"` artifact supports, and `CLAUDE.md:53` treats the Angular task as closed even though the lockfiles still differ and the required smoke test is still undocumented. The sentence claiming the remaining 6 highs require "Angular 22 (pre-release)" is also not supported by the current npm metadata I could verify: `npm outdated --json` does not show a newer `@angular-devkit/build-angular` or `@angular/cli` than the installed 21.2.16 line.

## Nice to have

- Both UI builds still emit CommonJS optimization-bailout warnings for `@stomp/stompjs` and `sockjs-client`. This is not a Sprint 4 blocker, but it remains frontend performance debt.

- `order-ui/angular.json` picked up a `schematics` block at `order-ui/angular.json:35-60` while `inventory-ui/angular.json` did not. This is not a runtime regression, but it is avoidable config drift between two UIs that are supposed to stay closely mirrored.

## Tests/checks missing

- I did not independently complete the full browser smoke test required by `H-3`. The repo still documents the local path as Kafka plus `auth-server`, `order-service`, `inventory-service`, and both UIs at `CLAUDE.md:81-120`, but there is no updated Sprint 4 artifact confirming that flow was actually rerun after the Angular 21 upgrade.

- I could not verify the positive-path half of `H-2` because this review environment still has no working Docker or Podman daemon. The brief required the Testcontainers-backed broker path to be rerun successfully at least once when a container engine is available.

## Verified

- `H-1` passes. `.\mvnw.cmd -q test` succeeded in `shared-model`, `auth-server`, `order-service`, and `inventory-service` under IBM Semeru/OpenJ9 21.0.6, so the Windows/OpenJ9 wrapper path is no longer broken.

- `H-4` passes on the fallback path allowed by the brief. `order-service/pom.xml:106` now adds `-Djdk.attach.allowAttachSelf=true`, the override file still exists at `order-service/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker:1`, and `order-service` tests pass under IBM Semeru/OpenJ9 21.0.6. The runtime still uses inline self-attach, but that is acceptable under the task's explicit fallback branch as long as the non-standard-JDK run passes and the report explains the fallback.

## Scorecard

- `H-1`: `PASS`
  Evidence: all four modules pass `.\mvnw.cmd -q test` under IBM Semeru/OpenJ9 21.0.6.

- `H-2`: `PARTIAL`
  Evidence: assumption gate at `inventory-service/src/test/java/com/example/inventoryservice/InventoryIntegrationTest.java:52-57`; build exits `0` with no daemon; Surefire report at `inventory-service/target/surefire-reports/TEST-com.example.inventoryservice.InventoryIntegrationTest.xml:2` still shows `tests="0"` / `skipped="0"`.

- `H-3`: `PARTIAL`
  Evidence: both apps upgraded to Angular 21 in `order-ui/package.json:11-32` and `inventory-ui/package.json:11-32`; both `npm.cmd run build` commands pass; `npm audit --json` drops from the Sprint 3 baseline of `30` high to `6` high in each app, and the direct `@angular/common` / `@angular/core` / `@angular/compiler` highs are gone. But the lockfiles diverge at `order-ui/package-lock.json:12032-12035`, `12669-12672`, `13050-13053` vs `inventory-ui/package-lock.json:12052-12055`, `12689-12692`, `13069-13072`, and the required browser smoke test is still undocumented.

- `H-4`: `PASS`
  Evidence: fallback argLine at `order-service/pom.xml:106`; OpenJ9/Semeru test run passes with `OrderEventIntegrationTest` reporting `tests="3"`, `errors="0"`, `failures="0"` at `order-service/target/surefire-reports/TEST-com.example.orderservice.OrderEventIntegrationTest.xml:2`.

- `H-5`: `FAIL`
  Evidence: snapshot updated and Track B pointer fixed at `CLAUDE.md:48-57`, but `CLAUDE.md:52-55` still overstates the current `H-2` / `H-3` outcome.

## Verification summary

- `shared-model`: `.\mvnw.cmd -q test` passed under IBM Semeru/OpenJ9 21.0.6.
- `auth-server`: `.\mvnw.cmd -q test` passed under IBM Semeru/OpenJ9 21.0.6.
- `order-service`: `.\mvnw.cmd -q test` passed under IBM Semeru/OpenJ9 21.0.6.
- `inventory-service`: `.\mvnw.cmd -q test` passed under IBM Semeru/OpenJ9 21.0.6 with no Docker/Podman daemon available.
- `order-service`: Surefire still logs inline Mockito self-attach at `order-service/target/surefire-reports/TEST-com.example.orderservice.OrderEventIntegrationTest.xml:1470`, but the fallback path is now explicit and the OpenJ9 run passes.
- `inventory-service`: Surefire still records `tests="0"` and `skipped="0"` at `inventory-service/target/surefire-reports/TEST-com.example.inventoryservice.InventoryIntegrationTest.xml:2`.
- `order-ui`: `npm.cmd run build` passed, with CommonJS warnings for `@stomp/stompjs` and `sockjs-client`.
- `inventory-ui`: `npm.cmd run build` passed, with the same CommonJS warnings.
- `order-ui`: `npm.cmd audit --json` reports `14` vulnerabilities (`6 high`, `4 moderate`, `4 low`).
- `inventory-ui`: `npm.cmd audit --json` reports `14` vulnerabilities (`6 high`, `4 moderate`, `4 low`).
- `order-ui`: `npm.cmd outdated --json` shows `@angular/cdk` and `@angular/material` behind the current 22.0.2 line, plus newer `typescript` and `zone.js`.
- `inventory-ui`: `npm.cmd outdated --json` shows the same outdated packages.

## Dependency note

- The Angular 21 upgrade materially improved the security picture. Compared with the Sprint 3 baseline, the previously reported direct Angular highs are no longer present in either UI.

- The frontend is still not vulnerability-free. On 2026-06-23, both apps still report `6` high vulnerabilities via `npm audit --json`, in transitive build tooling including `@angular-devkit/build-angular`, `@angular/build`, `http-proxy-middleware`, `piscina`, `undici`, and `vite`.

- I did not run a full Maven SCA tool for backend modules in this pass. The dependency/security findings I verified directly are the frontend audit/outdated results above.

## Verdict

Reject.

This is a materially better Sprint 4 state than the prior review. The Angular work is real now: both UIs are on Angular 21, both builds pass, and the direct Angular advisory set has been reduced sharply. The Mockito/OpenJ9 task is also now acceptable on the fallback path. But the sprint still does not fully close because the Docker-optional test is not being reported as skipped, the two UI lockfiles still diverge, the required browser smoke test is still not evidenced, and `CLAUDE.md` still overstates what `H-2` and `H-3` actually achieved.
