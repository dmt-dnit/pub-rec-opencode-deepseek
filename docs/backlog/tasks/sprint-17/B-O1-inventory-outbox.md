# B-O1 — Inventory transactional outbox (crash-safe exactly-once publish)

**Sprint:** 17 (Track B Sprint 4)
**Priority:** Should — Sprint 14 follow-up 2 (Codex restated it in `reviews/sprint-14-track-b-round-2-review.md`)
**Implementer:** opencode+DeepSeek (rule-5: subtle concurrency/correctness, genuine second-opinion value). I drive headless (worktree OUTSIDE `.claude/`, `JAVA_HOME=21`, separate build commands) + intervene.
**Scope:** `inventory-service` only — `ReservationService`, `OrderEventListener`, `InventoryEventPublisher`, a new outbox entity/repository + relay. No order-service / shared-model / UI changes.

## Problem (from the Sprint 14/16 reviews)
Inventory duplicate handling is now idempotent for *normal* duplicate delivery, but the stock mutation (DB) and the `InventoryReservationEvent` publish (Kafka) are **not atomic**: if the service crashes after the DB commit but before the Kafka send (or vice-versa), state and the published outcome can diverge. The fix is the **transactional outbox** pattern: write the outgoing event to an outbox table in the *same DB transaction* as the stock change, then a relay reliably publishes it.

## What to do

### 1. Outbox table + write-in-transaction
- Add an `OutboxEvent` JPA entity (e.g. `id`, `orderId`, `payload` (the serialized `InventoryReservationEvent` JSON), `topic`, `status` (`PENDING`/`SENT`), `createdAt`, `sentAt`) + repository.
- In `ReservationService.reserve(...)` (the existing `@Transactional` method): in the **same transaction** as the stock decrement + `ProcessedOrder` marker, persist the outcome event to the outbox (instead of, or in addition to, returning it for direct publish). The stock change, the dedup marker, and the outbox row commit atomically — so the "decide + record intent to publish" step is crash-safe and exactly-once per `orderId`.

### 2. Relay → Kafka
- A relay (e.g. a `@Scheduled` poller, or transaction-synchronization `afterCommit` hook that also has the scheduled poller as the crash-safe backstop) reads `PENDING` outbox rows and publishes them to `inventory-events` via `InventoryEventPublisher`, marking them `SENT` on success. On failure they stay `PENDING` and are retried — at-least-once delivery to Kafka, with the consumer side (order-service) already idempotent on `orderId`, giving effective exactly-once end-to-end.
- The listener (`OrderEventListener`) no longer publishes the outcome directly — it calls `reserve(...)` (which writes the outbox) and lets the relay publish. Keep the WebSocket push behavior (decide whether to push from the listener or the relay — document the choice; pushing from the relay on successful send is the most consistent).

### 3. Keep idempotency + behavior
- All Sprint 14/16 hardening tests must still pass (idempotency, outcome-idempotency, DLT). The duplicate-delivery no-op behavior stays: a duplicate `OrderPlacedEvent` must not create a second outbox row or a second publish.
- The DLQ/retry error handling (`DefaultErrorHandler` + `DeadLetterPublishingRecoverer`) stays.

## Acceptance criteria (observable)
1. The stock decrement, the `ProcessedOrder` marker, and the outbox row are written in **one transaction** (show the `@Transactional` boundary); a simulated failure between the DB commit and the Kafka publish does **not** lose the event (the relay re-publishes from the outbox). Add a test that proves the event is still delivered after a publish failure on the first attempt.
2. A duplicate `OrderPlacedEvent` produces **one** outbox row and **one** published `InventoryReservationEvent` (exactly-once), not two — extend/keep the outcome-idempotency test.
3. `./mvnw verify` green in inventory-service; all existing tests pass. Real output.
4. State clearly the delivery guarantee achieved (outbox = exactly-once stock mutation + at-least-once Kafka publish; consumer idempotency closes the loop) and any simplifications.

## Notes
- This is the highest-risk task of the sprint — it changes the inventory publish path. Coordinator will diff-verify + re-run `mvnw verify`; do NOT weaken any existing test to accommodate the refactor.
- Use EmbeddedKafka (not Testcontainers) for new tests here unless B-3's Testcontainers infra is already merged — keep it Docker-free where possible.
- Don't gold-plate: a `@Scheduled` outbox poller + `@Transactional` write is enough; no need for Debezium/CDC.
