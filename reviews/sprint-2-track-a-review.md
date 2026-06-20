# Sprint 2 Track A Re-Review

Date: 2026-06-20
Reviewer: Codex
Repo reviewed: `C:\projects\pub-rec-opencode-deepseek`
Scope: `docs/backlog/tasks/sprint-2/F-1` through `F-7`

## Must fix

- `F-6` is still open. Both UIs still declare the same Angular 18.2.x ranges in [order-ui/package.json](C:\projects\pub-rec-opencode-deepseek\order-ui\package.json:11) and [inventory-ui/package.json](C:\projects\pub-rec-opencode-deepseek\inventory-ui\package.json:11), and the current `npm audit --json` result on 2026-06-20 is still `50` vulnerabilities (`30 high`, `13 moderate`, `7 low`) in each app. `npm outdated` also shows the installed stack is unchanged within range (`@angular/core` `18.2.14`, `@angular/cli` `18.2.21`) while newer majors exist. Sprint 2's dependency-remediation acceptance was "materially fewer high-severity findings"; that did not happen.

- `order-service` is not green on Java 21 in this Codex environment. The strengthened integration test now correctly exercises the WebSocket and REST serialization paths in [OrderEventIntegrationTest.java](C:\projects\pub-rec-opencode-deepseek\order-service\src\test\java\com\example\orderservice\OrderEventIntegrationTest.java:34), but `mvn -q test` under IBM Semeru 21.0.6 fails before the assertions run because the `@MockBean` at [OrderEventIntegrationTest.java](C:\projects\pub-rec-opencode-deepseek\order-service\src\test\java\com\example\orderservice\OrderEventIntegrationTest.java:58) triggers Mockito's inline mock maker, which cannot self-attach on this OpenJ9 VM. The old lazy-loading bug appears fixed; the remaining problem is Java-21 portability of the test setup.

- `inventory-service` is also not green on Java 21 in this Codex environment. The F-3 wiring changes are present in [InventoryIntegrationTest.java](C:\projects\pub-rec-opencode-deepseek\inventory-service\src\test\java\com\example\inventoryservice\InventoryIntegrationTest.java:33), but `mvn -q test` under the same Semeru/OpenJ9 21.0.6 crashes the forked JVM with a segmentation fault inside `j9jit29.dll` while the integration test starts. That means the sprint still does not satisfy the practical "run the backend tests on Java 21 and get green" requirement in this environment.

- The Maven wrapper is still not usable from Codex's Windows PowerShell environment. Each backend module ships only a bash `mvnw` script beginning with `#!/bin/bash`, for example [shared-model/mvnw](C:\projects\pub-rec-opencode-deepseek\shared-model\mvnw:1), and there is no `mvnw.cmd` alongside it. In PowerShell, `.\mvnw -q test` fails with `Access is denied`, and `.\mvnw.cmd` is not present. Since the sprint handoff explicitly called out the wrapper repair, this is still a real verification gap in the current environment.

## Should fix

- `F-5` is almost done but not fully closed. [CLAUDE.md](C:\projects\pub-rec-opencode-deepseek\CLAUDE.md:40) still contains a stale status snapshot that says F-5 is "not started" and still mentions the old `kafka-demo` / `article` naming. The live architecture sections below it are mostly updated, but the task's acceptance was stricter: no remaining `article` / `kafka-demo` mentions in `CLAUDE.md`.

## Nice to have

- Both production builds still warn that `@stomp/stompjs` and `sockjs-client` are CommonJS dependencies used by `websocket.service.ts`, which keeps the previous optimization-bailout warning alive. This is not a sprint blocker, but it is still a measurable frontend-performance debt.

## Tests/checks missing

- I did not run a real browser end-to-end smoke test of order placement/status updates across all 5 processes. The code paths look aligned, but the backend Java-21 portability failures above make that end-to-end run lower-confidence until the test/runtime story is stable.

- I did not run a full `docker compose` environment smoke. The review stayed at module-test/build/audit level plus direct code inspection.

## Scorecard

- `F-1`: `PASS`. Proxy rules now point at `/api/orders/**` and `/api/inventory/**`; I found no remaining `api/articles` hits in live code.
- `F-2`: `PARTIAL`. The DTO/transactional serialization fix is in place and the new tests cover the right paths, but `order-service` still is not Java-21 green in this environment.
- `F-3`: `PARTIAL`. The topic split and dedicated listener container factory are implemented, but `inventory-service` still is not Java-21 green in this environment.
- `F-4`: `PASS`. `customerEmail` is now derived from JWT on the backend and removed from the request path in `order-ui`.
- `F-5`: `PARTIAL`. Runtime/config leftovers are cleaned, but `CLAUDE.md` still contains stale old-domain/status text.
- `F-6`: `FAIL`. The vulnerability counts are unchanged at `50` total (`30 high`) in both UIs.
- `F-7`: `PARTIAL`. The enforcer pin is correctly present and I verified it fails fast under Java 25, but the overall Java-21 verification story is still not reliable in this environment because `order-service` and `inventory-service` tests do not pass here.

## Verification summary

- `shared-model`: `mvn -q test` under Java 21 passed.
- `auth-server`: `mvn -q test` under Java 21 passed.
- `order-service`: `mvn -q test` under Java 21 failed with Mockito inline mock-maker self-attach errors on Semeru/OpenJ9.
- `inventory-service`: `mvn -q test` under Java 21 crashed the forked JVM on Semeru/OpenJ9.
- `shared-model`: `mvn -q compile` under Java 25 failed fast with the expected Maven Enforcer message: allowed range `[21,22)`.
- `order-ui`: `npm run build` passed outside the sandbox; sandboxed build failed due sandbox memory pressure, not a code error.
- `inventory-ui`: `npm run build` passed outside the sandbox; sandboxed build failed due sandbox memory pressure, not a code error.
- `order-ui`: `npm audit --json` on 2026-06-20 reported `50` vulnerabilities (`30 high`, `13 moderate`, `7 low`).
- `inventory-ui`: `npm audit --json` on 2026-06-20 reported the same `50` vulnerabilities.

## Verdict

Reject.

Sprint 2 clearly fixed the sprint-1 functional defects in the code, but it did not finish the dependency-remediation task, and it still does not provide a clean Java-21 verification path in this Codex Windows/Semeru environment. I would open a follow-up sprint for:

1. real Angular dependency remediation or an explicit "requires Angular major upgrade" decision,
2. Java-21/OpenJ9 test portability for `order-service` and `inventory-service`,
3. a usable Maven wrapper for Windows/Codex PowerShell, and
4. the last stale `CLAUDE.md` cleanup line.
