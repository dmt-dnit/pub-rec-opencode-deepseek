# Sprint 14 Track B Review

Date: 2026-06-28
Reviewer: Codex
Repo: `C:\projects\pub-rec-opencode-deepseek`

## Verdict

**Rejected / not cleared for Track B Sprint 1.**

B-1 mostly passes local behavioral verification, but B-5 has a CI-blocking defect: the GitHub Actions workflow invokes Unix Maven wrappers as executables while the repository tracks those wrapper files as non-executable.

## Must Fix

### P1 - GitHub Actions Maven jobs will fail on Ubuntu because `mvnw` is not executable

Evidence:

- `.github/workflows/ci.yml:48`, `:80`, `:103`, and `:126` run Maven as `./mvnw ...` on `ubuntu-latest`.
- Git index mode for all Unix wrappers is `100644`, not executable:

```text
100644 ... auth-server/mvnw
100644 ... inventory-service/mvnw
100644 ... order-service/mvnw
100644 ... shared-model/mvnw
```

Why this matters:

On a Linux GitHub runner, `./mvnw` requires the executable bit. With the current mode, the Maven jobs will fail before any Java test runs, so B-5's CI acceptance criteria are not met.

Required fix:

- Prefer setting the executable bit in git:

```powershell
git update-index --chmod=+x auth-server/mvnw inventory-service/mvnw order-service/mvnw shared-model/mvnw
```

- Or change each workflow Maven invocation to `bash ./mvnw ...`.

The first option is cleaner because it fixes the repository metadata instead of compensating in CI.

## Should Fix

### P2 - Inventory duplicate handling is stock-idempotent, but not outcome-idempotent

Evidence:

- `inventory-service/src/main/java/com/example/inventoryservice/service/ReservationService.java:33-40` returns a new `RESERVED` event when an order id was already processed.
- `inventory-service/src/main/java/com/example/inventoryservice/receiver/OrderEventListener.java:35-37` always publishes the returned outcome and sends it to the WebSocket feed.
- `inventory-service/src/main/java/com/example/inventoryservice/service/ReservationService.java:43-52` returns `REJECTED` before saving any processed marker, so a duplicate rejected order can be reprocessed later and can produce a different result if stock has changed.

Why this matters:

The new inventory test proves "no double-decrement" for a successful duplicate, which is the most important corruption case. It does not prove "one input event produces one output event." A redelivered successful order still emits another `InventoryReservationEvent` and another inventory UI feed item. A redelivered rejected order is not deduplicated at all.

Suggested direction:

- Store enough processed-outcome state to make duplicate `OrderPlacedEvent` handling side-effect-free.
- Or make `ReservationService.reserve(...)` return an explicit "duplicate/no-op" result that the listener does not publish.
- Add tests for duplicate successful events publishing once and duplicate rejected events remaining stable.

This is not the sprint's primary blocker because the brief's explicit stock assertion passes, but it is a real hardening gap for B-1's stated "idempotent listeners" goal.

## Scorecard

| Item | Result | Evidence |
| --- | --- | --- |
| B-5 valid CI topology | PARTIAL | Six jobs exist and downstream Maven jobs depend on `shared-model`, but Maven jobs invoke non-executable `mvnw` scripts on Ubuntu. |
| B-5 live Actions run | NOT VERIFIED | `gh run list --repo dmt-dnit/pub-rec-opencode-deepseek --json ...` returned `[]`; local `HEAD` is ahead of `origin/main`, so no live CI run was available. |
| B-1 DLT on listener exception | PASS | DLT tests pass in both services and assert matching `orderId` on `.DLT` records. |
| B-1 inventory no double-decrement | PASS | `OrderEventListenerIdempotencyTest` asserts `quantityOnHand == 9` after duplicate `OrderPlacedEvent`. |
| B-1 order no re-apply | PASS | `InventoryReservationListenerIdempotencyTest` verifies `SimpMessagingTemplate.convertAndSend(...)` exactly `times(1)`. |
| Backoff config | PASS | Both services use `ExponentialBackOffWithMaxRetries(3)`, initial `500ms`, multiplier `2.0`, max `4000ms`. |
| Java module tests | PASS | `shared-model clean install`; `auth-server`, `order-service`, `inventory-service clean verify` passed under Java 21. |
| UI builds | PASS | `npm.cmd run build` passed in both UIs. |
| Production audits | PASS | `npm.cmd audit --omit=dev` returned `found 0 vulnerabilities` in both UIs. |

## Verification Run

Commands/results:

- `bash scripts/pre-review-check.sh 14`: could not run in this Codex environment; WSL has no installed distributions.
- `actionlint .github/workflows/ci.yml`: not run; `actionlint` is not installed.
- YAML parse fallback: not run; local Python has no `yaml` module and local Node has no `yaml` package.
- `git ls-files --stage auth-server/mvnw inventory-service/mvnw order-service/mvnw shared-model/mvnw`: all four are mode `100644`.
- `gh auth status`: authenticated as `dmt-dnit`.
- `gh run list --repo dmt-dnit/pub-rec-opencode-deepseek --limit 10 --json ...`: `[]`, no live workflow run available.
- `git log origin/main..HEAD`: local Sprint 14 commits are ahead of remote, so GitHub cannot have run this workflow yet.
- `shared-model`: `.\mvnw.cmd --batch-mode -q clean install` with Java 21 passed.
- `auth-server`: `.\mvnw.cmd --batch-mode -q clean verify` with Java 21 passed.
- `order-service`: `.\mvnw.cmd --batch-mode -q clean verify` with Java 21 passed.
- `inventory-service`: `.\mvnw.cmd --batch-mode -q clean verify` with Java 21 passed.
- `order-ui`: `npm.cmd run build` passed.
- `inventory-ui`: `npm.cmd run build` passed.
- `order-ui`: `npm.cmd audit --omit=dev` passed with `found 0 vulnerabilities`.
- `inventory-ui`: `npm.cmd audit --omit=dev` passed with `found 0 vulnerabilities`.

Surefire summaries from the B-1 modules:

```text
order-service:
InventoryReservationListenerDltTest: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
InventoryReservationListenerIdempotencyTest: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
OrderEventIntegrationTest: Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
OrderEventPublisherTest: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

inventory-service:
InventoryIntegrationTest: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
OrderEventListenerDltTest: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
OrderEventListenerIdempotencyTest: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

## Residual Notes

- The local inventory run executed `InventoryIntegrationTest` instead of skipping it because Docker is available in this environment. It passed.
- The new EmbeddedKafka tests are slow but not hanging: DLT tests took roughly 30-58 seconds in this run.
- Generated build outputs did not leave tracked changes; final status was clean except the existing untracked `.claude/` directory before writing this review.
