# Sprint 17 (Track B Sprint 4) — Handoff to Codex

**Coordinator:** Claude Code. **Date:** 2026-06-30.
**Tasks:** B-2 (observability), B-3 (Testcontainers + contract test), B-O1 (inventory transactional outbox). All verified by reading diffs + **re-running `mvnw verify` myself**; agent results integrated via cherry-pick/FF (apply-diff, state-verified — not whole-file).

## Round 2 (2026-06-30) — addresses Codex rejection `reviews/sprint-17-track-b-review.md`

Codex found 3 real blockers; all fixed by opencode+DeepSeek (brief `docs/backlog/tasks/sprint-17/round2-codex-fixes.md`, commits `76b77cc`/`68c8f77`/`ae1b3c0`), coordinator-verified by my own `mvnw verify`:

- **F2 — outbox relay race (user-visible: inventory-UI feed duplicates).** The relay (scheduler + listener nudge) scanned the same PENDING rows with no lock, double-publishing and **double-STOMP-pushing** (order-service idempotency hid the Kafka dup but not the WebSocket feed). Fix (`68c8f77`): `@Lock(PESSIMISTIC_WRITE)` on the PENDING fetch inside the `@Transactional` relay so concurrent callers serialize and the second sees no PENDING rows. New `OutboxRelayConcurrencyTest` runs `processPending()` on **2 concurrent threads** against 3 rows and asserts `publisher.publish` **times(3)** + `convertAndSend` **times(3)** + all SENT — proves exactly-once publish *and* STOMP push. (My earlier "absorbed by consumer idempotency" caveat under-traced the STOMP side — Codex was right.)
- **F1 — Testcontainers test not bound to the container broker** (`dockerAvailable` set in `@BeforeAll` but `@DynamicPropertySource` runs earlier). Fix (`76b77cc`): `@Testcontainers(disabledWithoutDocker = true)` + `@Container static KafkaContainer` so the container starts before property resolution and the class skips cleanly without Docker; `@DynamicPropertySource` now registers the container broker unconditionally.
- **F3 — auth-server missing correlation** (had ECS logging + actuator but no filter/MDC; handoff overstated "all three"). Fix (`ae1b3c0`): added `CorrelationIdFilter` to auth-server mirroring order-service. All three services now carry `correlationId`.

**Round-2 verification (my own `mvnw verify`, Java 21):** inventory-service `Tests run: 8, 0 fail, 1 skip` (incl. `OutboxRelayConcurrencyTest`); order-service `9, 0 fail, 2 skip` (Testcontainers); auth-server `BUILD SUCCESS`. No existing test weakened.

**Codex re-verify:** the inventory-UI reservation feed no longer duplicates (live smoke); the Testcontainers test runs in CI; auth-server logs carry `correlationId`.

---

## Commits (on `main`)

| SHA | Task | Summary |
|-----|------|---------|
| `49c74ca` | B-2 | correlation IDs (filter + Kafka RecordInterceptor + MDC), ECS structured logs, actuator on all 3 |
| `666b271` | B-3 | Testcontainers real-Kafka test (order-service) + event contract test (shared-model) |
| `6a88f1d`,`a6c46a2`,`54e0318` | B-O1 | inventory transactional outbox (entity, repo, relay, reserve()-writes-outbox, listener delegates) |

`git status --short`: clean. B-2/B-3 = Claude sonnet agents (stale-based → cherry-picked their diffs onto main, preserving Sprint 16 state); B-O1 = opencode+DeepSeek on a base I controlled (FF-merge). Dependency-currency at sprint start: all current (Boot 4.1.0, Spring Security 7.1.0, Java 21, Angular 22.0.4, actions v5/v6, Snyk green).

## B-2 — observability
- **Correlation ID:** `CorrelationIdFilter` (OncePerRequestFilter) generates/reuses `X-Correlation-Id` at `POST /api/orders` → MDC; publishers attach it as a Kafka header on `OrderPlacedEvent`/`InventoryReservationEvent`; a `CorrelationMdcRecordInterceptor` (RecordInterceptor) on each service reads the header → MDC for the listener thread; the order-service listener puts it on the WebSocket payload (`OrderResponse.correlationId`).
- **Structured logs:** Spring Boot 4.1 native `logging.structured.format.console: ecs` on all 3 (no logstash dep). ECS JSON with MDC confirmed in test output.
- **Actuator:** added `spring-boot-starter-actuator` to auth-server (others had it); `health,metrics,info` exposed; `/actuator/**` permitted in each SecurityConfig (the Sprint-16 `/v3/api-docs` permits are preserved — both present).

## B-3 — Testcontainers + contract test
- `OrderServiceKafkaContainerTest` — real `confluentinc/cp-kafka` container exercising publish + consume; skips cleanly when Docker is absent (`assumeTrue`). inventory-service already has the equivalent `InventoryIntegrationTest`.
- `EventContractTest` (shared-model, Jackson 3, no Docker) — asserts the **exact JSON field set** of `OrderPlacedEvent` + `InventoryReservationEvent` and round-trips them (incl. `Instant`). A field rename/removal on one side fails the build.

## B-O1 — inventory transactional outbox (exactly-once)
- `reserve(...)` now writes the outcome `InventoryReservationEvent` to an `OutboxEvent` row (PENDING) **in the same `@Transactional`** as the stock decrement + `ProcessedOrder` marker. Duplicate `orderId` → no second row (idempotency intact).
- `OutboxRelay` (`@Scheduled` poller, also nudged synchronously by the listener) reads PENDING rows, publishes via `InventoryEventPublisher`, marks SENT, and does the WebSocket `convertAndSend` (the feed moved from listener → relay, on successful send). On publish failure the row stays PENDING and is retried.
- **Delivery guarantee:** stock mutation + outbox write are atomic (exactly-once); Kafka publish is at-least-once; the order-service consumer is idempotent on `orderId` (Sprint 14 PENDING-guard) → effective exactly-once end-to-end.
- **Caveat:** the relay has no row-locking, so a rare concurrent double-publish (scheduler + listener nudge) is possible; absorbed by the consumer's idempotency. A `FOR UPDATE SKIP LOCKED` / single-relay-path tightening is a future nicety, not a correctness bug.
- DLQ/retry error handling unchanged.

## Independent verification (my own `mvnw verify`, Java 21, Boot 4.1.0)

```
shared-model       Tests run: 4 (EventContractTest), 0 fail       BUILD SUCCESS
auth-server        BUILD SUCCESS
order-service      Tests run: 9, 0 fail, 2 skipped (Testcontainers, no Docker)   BUILD SUCCESS
inventory-service  Tests run: 7, 0 fail, 1 skipped (Docker)       BUILD SUCCESS
   DltTest 1 · IdempotencyTest 1 · OutcomeIdempotencyTest 2 · OutboxIntegrationTest 2 · (InventoryIntegrationTest skip)
```
The outcome-idempotency test was **adapted (not weakened)** for the outbox model: a duplicate now asserts exactly one outbox row (SENT) instead of `verify(publisher, times(1))`. `OutboxIntegrationTest` proves: duplicate → 1 row + 1 publish; publish-failure → row stays PENDING → retry → SENT (no loss).

## Codex-only / not verified here
- **Live CI** (incl. Snyk) on push.
- **Actuator HTTP 200** (`/actuator/health`, `/actuator/metrics`) on all 3 — needs the running stack.
- **Cross-service correlation-ID log grep** — needs the live saga (POST → Kafka → both services' ECS logs share one UUID). The header-propagation path is unit-covered; the end-to-end grep is live-only.
- **Testcontainers real-Kafka tests** — run in CI (Docker present); skip here.
- **Containerized Playwright smoke** — the end-to-end backstop after the outbox publish-path change; please re-run.

## Pre-review
`bash scripts/pre-review-check.sh 17` — passes (clean tree, this handoff present).
