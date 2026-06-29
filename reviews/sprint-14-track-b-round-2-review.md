# Sprint 14 Track B Round 2 Review

Date: 2026-06-29
Reviewer: Codex
Repo: `C:\projects\pub-rec-opencode-deepseek`
Source handoff: `docs/backlog/sprint-14-handoff.md`

## Verdict

**Approved with non-blocking follow-ups.**

The two Sprint 14 round-1 review issues are closed:

- P1 `mvnw` executable bit is fixed.
- P2 inventory duplicate handling is now outcome-idempotent for normal duplicate delivery.

B-5 is also live-verified: GitHub Actions run `28354413116` for `f5d81b4` completed successfully with all six jobs green. Local `HEAD` (`d2158f0`) has only docs/README changes after `f5d81b4`; there is no source or workflow diff after the live green run.

## Findings

### P2 - GitHub Actions versions are not dependency-current

This is not a Sprint 14 blocker because CI is green, but it is a real dependency-currency follow-up.

Evidence:

- `.github/workflows/ci.yml:34`, `:52`, `:73`, `:94`, `:114`, `:131` use `actions/checkout@v4`.
- `.github/workflows/ci.yml:37`, `:55`, `:76`, `:97` use `actions/setup-java@v4`.
- `.github/workflows/ci.yml:117`, `:134` use `actions/setup-node@v4`.
- GitHub tags queried on 2026-06-29 show newer majors:
  - `actions/checkout`: `v7.0.0`
  - `actions/setup-java`: `v5.4.0`
  - `actions/setup-node`: `v6.4.0`
- The handoff's Node 20 deprecation warning is consistent with this. GitHub currently auto-forces the run to pass, but the workflow is not dependency-current.

Recommendation:

- Add a small CI dependency-update task: bump `checkout`, `setup-java`, and `setup-node` to current supported majors, then verify with a live Actions run.
- Keep this separate from B-5 acceptance; the existing `f5d81b4` workflow is functionally valid and green.

## Residual Risks

### Inventory DB+Kafka publish gap remains out of scope

Round 2 deliberately chose a no-op duplicate strategy, and the brief explicitly accepted the at-least-once edge as out of scope. That is acceptable for this sprint, but the reliability boundary should stay visible.

Evidence:

- `inventory-service/src/main/java/com/example/inventoryservice/service/ReservationService.java:50` and `:61` save the `ProcessedOrder` marker before the listener publishes the resulting event.
- `inventory-service/src/main/java/com/example/inventoryservice/receiver/OrderEventListener.java:34-36` publishes after `reserve(...)` returns.
- `inventory-service/src/main/java/com/example/inventoryservice/publisher/InventoryEventPublisher.java:31-40` logs asynchronous send failure in `whenComplete(...)`; it does not make the listener fail after an async broker failure.

Implication:

- Normal duplicate delivery is now clean: one input order produces one published outcome.
- Stronger crash-safe "stock mutation and event publication are both eventually completed exactly once" still needs an outbox, transactional producer pattern, or persisted publish state. That is future hardening, not a Sprint 14 blocker.

### Vulnerability visibility is still partial

Frontend production audits were verified clean. Server-side vulnerability status was not independently proven by Dependabot or an OWASP dependency scan in this review.

Evidence:

- `npm.cmd audit --omit=dev` returned `found 0 vulnerabilities` in both UIs.
- GitHub Dependabot alerts API returned that alerts are disabled for this repository / unavailable to the token.
- No Maven vulnerability scanner is configured in the CI workflow.

Recommendation:

- Track a future dependency-security task to enable Dependabot alerts and/or add a Maven vulnerability scan with clear fail/pass policy.

## Closed Round-1 Items

| Item | Result | Evidence |
| --- | --- | --- |
| P1 Unix wrappers executable | PASS | `git ls-files --stage auth-server/mvnw inventory-service/mvnw order-service/mvnw shared-model/mvnw` shows all four as `100755`. |
| B-5 live CI | PASS | GitHub Actions run `28354413116`, commit `f5d81b4`, event `push`, conclusion `success`. Jobs green: `shared-model`, `auth-server`, `order-service`, `inventory-service`, `order-ui`, `inventory-ui`. |
| B-5 shared-model handoff | PASS | Artifact transfer removed; each downstream Maven job runs `cd shared-model && ./mvnw --batch-mode -q -DskipTests install` before its own verify. |
| P2 duplicate success output | PASS | `OrderEventListenerOutcomeIdempotencyTest.duplicateSuccessPublishesExactlyOnce` verifies publisher `times(1)`, WebSocket `times(1)`, stock `9`. |
| P2 duplicate rejection output | PASS | `OrderEventListenerOutcomeIdempotencyTest.duplicateRejectionPublishesExactlyOnce` verifies one `REJECTED` publish, WebSocket `times(1)`, marker exists, stock remains `0`. |

## Verification Run

Commands/results run by Codex on 2026-06-29:

```text
git ls-files --stage auth-server/mvnw inventory-service/mvnw order-service/mvnw shared-model/mvnw
=> all four Unix wrappers mode 100755
```

```text
gh run view 28354413116 --repo dmt-dnit/pub-rec-opencode-deepseek --json jobs,conclusion,status,url,headSha,event,createdAt
=> conclusion success; 6/6 jobs success; headSha f5d81b4591d222781cff897f4da6d71db4dbd704
```

```text
git diff --name-status f5d81b4..HEAD
=> CLAUDE.md, README.md, docs/backlog/sprint-14-handoff.md only

git diff f5d81b4..HEAD -- .github/workflows/ci.yml inventory-service/src/main/java inventory-service/src/test/java order-service/src/main/java order-service/src/test/java
=> empty
```

```text
shared-model: .\mvnw.cmd --batch-mode -q clean install
=> PASS after elevated filesystem access

auth-server: .\mvnw.cmd --batch-mode -q clean verify
=> PASS

order-service: .\mvnw.cmd --batch-mode -q clean verify
=> PASS

inventory-service: .\mvnw.cmd --batch-mode -q clean verify
=> PASS
```

Surefire summaries:

```text
order-service:
InventoryReservationListenerDltTest          Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
InventoryReservationListenerIdempotencyTest  Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
OrderEventIntegrationTest                    Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
OrderEventPublisherTest                      Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

inventory-service:
InventoryIntegrationTest                     Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
OrderEventListenerDltTest                    Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
OrderEventListenerIdempotencyTest            Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
OrderEventListenerOutcomeIdempotencyTest     Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
```

```text
order-ui: npm.cmd run build
=> PASS; initial total 524.83 kB

inventory-ui: npm.cmd run build
=> PASS; initial total 521.57 kB

order-ui: npm.cmd audit --omit=dev
=> found 0 vulnerabilities

inventory-ui: npm.cmd audit --omit=dev
=> found 0 vulnerabilities
```

Dependency-currency checks:

```text
npm.cmd outdated
=> Angular packages show Current/Wanted 22.0.4 while registry Latest is lower; not actionable as an upgrade gap.

gh api repos/actions/*/tags
=> checkout latest tag v7.0.0; setup-java v5.4.0; setup-node v6.4.0.
```

Could not run:

- `bash scripts/pre-review-check.sh 14`: this Codex environment has no installed WSL distributions.
- `actionlint .github/workflows/ci.yml`: `actionlint` is not installed locally. The workflow was instead validated by the live GitHub Actions run.

## Status

Sprint 14 round 2 is clear to move on from, with the CI action-major update and dependency-security visibility as follow-up backlog items.
