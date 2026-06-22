# Sprint 3 Track A Review

Date: 2026-06-20
Reviewer: Codex
Repo reviewed: `C:\projects\pub-rec-opencode-deepseek`
Scope: `docs/backlog/tasks/sprint-3/G-1` through `G-5`
State reviewed: current dirty worktree on top of commit `b31f482`

## Must fix

- `G-1` is still not closed. The new Windows wrapper exists, but it is not usable from this Windows/PowerShell review environment. Running `.\mvnw.cmd -v` in `shared-model` fails with `Cannot index into a null array` and `Cannot start maven from wrapper`, with the failure coming from the generated hybrid script path around [shared-model/mvnw.cmd:35]. `CLAUDE.md` already claims this is done at [CLAUDE.md:42] and [CLAUDE.md:50], but the actual wrapper invocation is still broken.

- `G-3` still leaves `inventory-service` red in the reviewer environment. The test was moved to Testcontainers at [inventory-service/pom.xml:74], [inventory-service/src/test/java/com/example/inventoryservice/InventoryIntegrationTest.java:42], [inventory-service/src/test/java/com/example/inventoryservice/InventoryIntegrationTest.java:50], and [inventory-service/src/test/java/com/example/inventoryservice/InventoryIntegrationTest.java:60], but `mvn -q test` now fails with `Could not find a valid Docker environment.` This replaces the OpenJ9 embedded-Kafka crash with a hard Docker dependency that is not satisfied here, so the sprint goal of making the reviewer's environment verifiable is still unmet.

- `G-4` is still open. The package manifests were bumped in [order-ui/package.json:16] and [inventory-ui/package.json:16], but `npm audit --json` on 2026-06-20 is still unchanged in both apps: `50` total vulnerabilities, `30` high, `13` moderate, `7` low. That does not meet the "materially fewer high-severity findings" acceptance target.

## Should fix

- `G-2` is only partially convincing. `order-service` does pass `mvn -q test` on IBM Semeru/OpenJ9 21.0.6 now, which is real progress, and the override file exists at [order-service/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker:1]. But the latest `OrderEventIntegrationTest` report still logs `Mockito is currently self-attaching to enable the inline-mock-maker`, so the runtime behavior does not match the documented explanation in [CLAUDE.md:43]. If the suite is still relying on inline attach, the portability risk is reduced in practice here but not actually removed.

- `G-5` is not accurate as written. The stale naming grep is clean, but the new snapshot in [CLAUDE.md:42] through [CLAUDE.md:50] prematurely claims G-1 through G-5 are done, even though this review still finds G-1, G-3, and G-4 open. It also points Track B at `docs/backlog/sprint-3.md` at [CLAUDE.md:48], but Track B remains the backlog in `docs/backlog/sprint-1.md`.

## Nice to have

- `inventory-ui` still builds with CommonJS optimization-bailout warnings for `@stomp/stompjs` and `sockjs-client`. This is not a sprint blocker, but the frontend performance debt is still present.

## Tests/checks missing

- I could not cleanly verify `order-ui`'s build from the current install state. `npm run build` fails because `ng` is not resolved from local `node_modules`, and `npm ci` then fails with `ENOTEMPTY` while trying to clean `node_modules`. Because `inventory-ui` builds and both apps' manifests match, I am treating this as a verification gap in the current worktree rather than a committed manifest regression, but it means round 3 still lacks clean evidence that both UIs build reproducibly.

- No standard HotSpot Java 21 JDK was installed on this machine. Available JDKs were IBM Semeru/OpenJ9 21.x, OpenJDK 25, and Temurin 17, so Java-21 verification was limited to OpenJ9.

## Scorecard

- `G-1`: `FAIL`
- `G-2`: `PARTIAL`
- `G-3`: `FAIL`
- `G-4`: `FAIL`
- `G-5`: `FAIL`

## Verification summary

- `shared-model`: `mvn -q test` under IBM Semeru/OpenJ9 21.0.6 passed.
- `auth-server`: `mvn -q test` under IBM Semeru/OpenJ9 21.0.6 passed.
- `order-service`: `mvn -q test` under IBM Semeru/OpenJ9 21.0.6 passed.
- `inventory-service`: `mvn -q test` under IBM Semeru/OpenJ9 21.0.6 failed because Testcontainers could not find a valid Docker environment.
- `shared-model`: `.\mvnw.cmd -v` failed in PowerShell with `Cannot index into a null array` / `Cannot start maven from wrapper`.
- `inventory-ui`: `npm run build` passed outside the sandbox, with the existing CommonJS warnings.
- `order-ui`: `npm run build` did not verify on the current install; local CLI resolution is broken in this worktree.
- `order-ui`: `npm audit --json` still reports `50` vulnerabilities (`30 high`, `13 moderate`, `7 low`).
- `inventory-ui`: `npm audit --json` still reports `50` vulnerabilities (`30 high`, `13 moderate`, `7 low`).
- `rg -n -i "kafka-demo|kafkademo|article" CLAUDE.md` returned no hits.

## Verdict

Reject.

Round 3 improved the backend story compared with sprint 2, especially `order-service` on OpenJ9, but it still does not satisfy the actual close-out criteria. The Windows wrapper is still unusable, `inventory-service` still cannot be verified in this review environment, and the Angular vulnerability blocker is unchanged. `CLAUDE.md` was also updated too early and now overstates what is complete.
