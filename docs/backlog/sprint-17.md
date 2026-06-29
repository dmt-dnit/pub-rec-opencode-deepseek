# Sprint 17 (Track B Sprint 4) — finish core hardening: observability, real-Kafka tests, exactly-once

**Track:** B — hardening. **Theme:** close the remaining Track B hardening backlog (everything except B-6 Docker Compose, which is its own sprint).

## Dependency currency (cadence step 1, 2026-06-29)
All current: Spring Boot **4.1.0**, Spring Security 7.1.0 (patched), Java 21 (LTS), Angular 22.0.4 (latest), GitHub Actions checkout@v5/setup-java@v5/setup-node@v6, codeql-action@v4, springdoc 3.0.3, Snyk SCA green. No drift. **Watch-item:** bump Angular off 22.0.4 when a patched 22.x ships (clears the dev-only http-proxy-middleware CVE — none available yet).

## Tasks (3, loosely coupled)

| ID | Title | Brief | Risk | Implementer (rec) |
|----|-------|-------|------|-------------------|
| B-2 | Observability — correlation IDs, structured logs, actuator | `tasks/sprint-17/B-2-observability.md` | Low–Med (additive) | Claude sonnet agent |
| B-3 | Testcontainers + contract tests | `tasks/sprint-17/B-3-testcontainers-contract-tests.md` | Med (test infra) | Claude sonnet agent or DeepSeek |
| B-O1 | Inventory transactional outbox (exactly-once publish) | `tasks/sprint-17/B-O1-inventory-outbox.md` | **High** (saga publish path) | opencode+DeepSeek (rule-5: subtle correctness) |

**Sequencing:** B-2 and B-3 are independent of each other — can run in parallel. **B-O1 must land before/after B-2** carefully (both touch inventory-service Kafka publish path): run B-O1 against current main, then reconcile B-2's correlation-header change into the outbox publish. Recommend: B-2 + B-3 in parallel first, land them, then B-O1 on the settled main. If B-O1 needs extended iteration, ship B-2+B-3 and roll B-O1 to Sprint 18.

## Why these
- **B-2** (Track B backlog) — the demo has a live saga but no correlation across the two services' logs; this makes the saga traceable end-to-end (a strong demo/showcase beat) and exposes actuator health/metrics. auth-server currently has no actuator.
- **B-3** (Track B backlog) — current Kafka tests use EmbeddedKafka (no Docker); add real-Kafka Testcontainers integration tests + a contract test that catches event-schema drift between the two services (the shared-model boundary).
- **B-O1** (Sprint 14 follow-up 2, restated by Codex in the Sprint 14 review) — current idempotency is crash-safe only for normal duplicate delivery; a transactional outbox makes "stock mutation + event publication both happen exactly once" crash-safe.

## Deferred to Sprint 18+
B-6 (one-command Docker Compose full stack) — largest item, its own sprint.

## Acceptance (sprint-level)
1. Correlation ID generated at `POST /api/orders`, propagated as a Kafka header order→inventory→order, present in every backend log line + the WebSocket payload; grepping one correlation ID across both services shows the full order lifecycle in causal order.
2. Structured (JSON) logs on all 3 backends; `/actuator/health` + `/actuator/metrics` reachable on all 3.
3. Testcontainers-backed Kafka integration tests pass via `./mvnw test` (Docker present); a contract test fails if `OrderPlacedEvent`/`InventoryReservationEvent` JSON drifts between services.
4. Inventory: stock mutation + reservation event are published exactly once even across a simulated crash/redelivery (outbox); all Sprint 14/16 hardening tests still pass.
5. CI green (incl. Snyk); `pre-review-check.sh 17` passes.

## Loop note
Per Dimitri's setup, the review is auto-picked-up by the reviewer once the sprint is handed off (testing that automation this sprint).
