# B-1 — Failure handling: retry with backoff, dead-letter topic, idempotent listeners

**Sprint:** 14 (Track B Sprint 1)
**Priority:** Must — core hardening
**Implementer:** opencode+DeepSeek (isolated worktree/branch). Trigger (rule 5): *genuine second-opinion value* — transactional dedup/concurrency correctness is the kind of subtle logic where an independent implementation perspective earns its keep, and this task keeps the independent-implementer path exercised. Coordinator (Claude) verifies the diff against acceptance criteria; Codex reviews independently.
**Services touched:** `order-service` and `inventory-service` (Java only). Does **not** touch `shared-model`, `auth-server`, or any UI.

## Goal

Right now both Kafka listeners use Spring's default error handling and have no dedup. Two concrete failures:

1. **Lost messages on error.** If a listener throws, Spring retries with defaults and then drops the record. Nothing lands anywhere durable.
2. **Corruption on redelivery.** If `order-events` redelivers an `OrderPlacedEvent`, Inventory Service decrements stock a second time. If `inventory-events` redelivers an `InventoryReservationEvent`, Order Service re-applies the status and re-pushes a WebSocket update.

Fix both, on both services.

## Current code (read before changing)

**Inventory Service — `inventory-service/src/main/java/com/example/inventoryservice/receiver/OrderEventListener.java`** listens on `order-events` (`${app.kafka.listen-topic}`), calls `reservationService.reserve(event)`, publishes the outcome, pushes WebSocket. No `@Transactional` on the listener; the `@Transactional` boundary is inside `ReservationService.reserve(...)`.

**Inventory Service — `inventory-service/src/main/java/com/example/inventoryservice/service/ReservationService.java`** (the stock mutation, not idempotent):
```java
@Transactional
public InventoryReservationEvent reserve(OrderPlacedEvent order) {
    for (OrderItem item : order.items()) {                 // validate
        Product product = productRepository.findById(item.sku()).orElse(null);
        if (product == null || product.getQuantityOnHand() < item.quantity()) {
            return new InventoryReservationEvent(order.orderId(), REJECTED, "Insufficient stock for " + item.sku(), Instant.now());
        }
    }
    for (OrderItem item : order.items()) {                 // decrement
        Product product = productRepository.findById(item.sku()).orElseThrow();
        product.setQuantityOnHand(product.getQuantityOnHand() - item.quantity());
        productRepository.save(product);
    }
    return new InventoryReservationEvent(order.orderId(), RESERVED, null, Instant.now());
}
```

**Order Service — `order-service/src/main/java/com/example/orderservice/receiver/InventoryReservationListener.java`** listens on `inventory-events`, is already `@Transactional`, looks up the order by `orderId`, sets `CONFIRMED`/`REJECTED`, saves, pushes WebSocket. It re-applies and re-pushes unconditionally on redelivery.

**Topic config:** each service has a `config/KafkaTopicConfig.java` declaring one `NewTopic`. Producer/consumer config is in each `application.yml` (Boot autoconfigures `KafkaTemplate`). `app.kafka.topic` / `app.kafka.listen-topic` carry the topic names.

## What to build

### 1. Retry with exponential backoff + dead-letter routing (both services)

Add a `DefaultErrorHandler` bean to each service so it is wired into Boot's autoconfigured listener container factory. Use a `DeadLetterPublishingRecoverer` (default destination resolver appends `.DLT` to the source topic and reuses the partition) and `ExponentialBackOffWithMaxRetries`:

```java
@Bean
public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> template) {
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);
    ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
    backOff.setInitialInterval(500L);
    backOff.setMultiplier(2.0);
    backOff.setMaxInterval(4000L);
    return new DefaultErrorHandler(recoverer, backOff);
}
```
- 3 retries (4 total deliveries) then DLT. Keep intervals short so the DLT test runs in a few seconds.
- Inject the autoconfigured `KafkaTemplate`. If the generic types don't satisfy `DeadLetterPublishingRecoverer`'s constructor at compile time, adjust the injected generics or declare a dedicated template — **verify by compiling**, don't guess.
- Resulting DLTs: Order Service (listens on `inventory-events`) → **`inventory-events.DLT`**; Inventory Service (listens on `order-events`) → **`order-events.DLT`**. This matches the backlog's named topics.
- Add a `NewTopic` bean for each DLT (alongside the existing `NewTopic` in each `KafkaTopicConfig`) so the topic exists deterministically under EmbeddedKafka and a real broker.
- Scope: handle **processing exceptions** (listener throws). Poison-pill / deserialization-failure handling is out of scope for this brief.

### 2. Idempotent listeners (dedupe on `orderId`)

**Inventory Service** — must not double-decrement on redelivery. Persist processed order IDs in the same transaction as the stock decrement so the check-and-act is atomic:
- Add a small JPA entity (e.g. `ProcessedOrder` with `orderId` as `@Id`) + repository in `inventory-service`.
- In `ReservationService.reserve(...)` (the existing `@Transactional` method), first check whether `orderId` is already recorded. If it is, return without touching stock — the outcome was already published once; do **not** decrement again. If it isn't, record it and proceed with the existing validate/decrement logic.
- The record + the decrement commit together (same transaction), so a crash between them cannot leave stock decremented without the dedup marker.

**Order Service** — must not re-apply a status it already applied. The order's own state is the dedup key; no extra table needed:
- In `InventoryReservationListener`, only transition an order whose status is still `PENDING`. If the order is already `CONFIRMED` or `REJECTED`, log at INFO/DEBUG and return without saving or pushing a WebSocket update.

## Tests (EmbeddedKafka — no Docker)

Use `spring-kafka-test` `@EmbeddedKafka`. Do **not** use Testcontainers (that's B-3) — these tests must run in CI (B-5) with no Docker daemon. Add `spring-kafka-test` to the relevant `pom.xml` if it isn't already a test dependency.

**DLT test (one per service):** drive the listener into a thrown exception (e.g. a `@MockBean` `ReservationService` that throws on `reserve`, or a repository stub that throws), send one event, then consume from the source topic's `.DLT` and assert exactly one record arrives after retries exhaust. The point is the record is **not lost** — it's on the DLT.

**Idempotency test:**
- *Inventory:* seed `SKU-001` with a known `quantityOnHand`, send the same `OrderPlacedEvent` (same `orderId`, `SKU-001` qty 1) twice, assert `quantityOnHand` dropped by exactly 1, not 2.
- *Order:* persist a `PENDING` order, send the same `InventoryReservationEvent` (`RESERVED`, same `orderId`) twice, assert the order ends `CONFIRMED` and the status transition / WebSocket push happened once (e.g. verify the `SimpMessagingTemplate` interaction count, or assert no second state change).

Each test owns a fresh EmbeddedKafka broker and a fresh H2 schema (`create-drop`), so there is no cross-run state to manage.

## Acceptance criteria (observable outcomes)

1. **No silent loss:** a forced processing exception in each service's listener results in exactly one record on that service's `.DLT` topic after retries exhaust. Show the test and its passing output.
2. **No double-decrement:** sending the same `OrderPlacedEvent` twice to Inventory Service decrements stock once. Show the assertion and passing output.
3. **No re-apply:** sending the same `RESERVED` `InventoryReservationEvent` twice to Order Service transitions the order once (no second save / WebSocket push). Show the assertion and passing output.
4. `./mvnw test` passes in **both** `order-service` and `inventory-service` with **no manually started broker** (EmbeddedKafka only). Paste the actual `BUILD SUCCESS` / test-summary output for each — not an asserted "Pass".
5. Backoff config is 3 retries before DLT (4 total deliveries); state the exact values used.
6. If any check could not be run in your environment, say so explicitly and why (per the verification-standards rule in CLAUDE.md).

## Notes / guidance

- A single `DefaultErrorHandler` bean is picked up by Boot's autoconfigured `ConcurrentKafkaListenerContainerFactory` — you should not need to declare your own factory. Verify the handler is actually engaged (the DLT test proves it).
- Keep the existing happy-path behaviour intact: a normal `RESERVED`/`REJECTED` flow must still confirm/reject the order and push the WebSocket update exactly as today. The Track A smoke path must not regress.
- Idempotency for Inventory belongs in `ReservationService` (where the transaction + writes live), not in the listener, so the dedup marker and the decrement are atomic.
