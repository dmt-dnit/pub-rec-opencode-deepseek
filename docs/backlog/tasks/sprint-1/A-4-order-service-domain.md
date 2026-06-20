# Task A-4: Order Service — own the `Order` entity, expose placement API, publish/consume events

**Depends on:** A-1 (rename to `order-service`/`com.example.orderservice`) and A-3 (`shared-model` now has `OrderPlacedEvent`, `OrderItem`, `InventoryReservationEvent`) must both be merged first.

## Context
`order-service` is being repurposed from an article-publishing demo into the Order side of a choreographed saga: it accepts order placement requests, publishes `OrderPlacedEvent` for the Inventory Service to react to, and listens for `InventoryReservationEvent` to update its own order's status. It owns its own `Order` data — it does not share a database with Inventory Service.

## Current state (post A-1/A-3, pre this task)
- `order-service/src/main/java/com/example/orderservice/controller/ArticleController.java` — `POST /api/articles/publish`, builds and publishes an `ArticlePublishedEvent`. **This will be replaced.**
- `order-service/src/main/java/com/example/orderservice/publisher/ArticlePublisherService.java` — wraps `KafkaTemplate<String, ArticlePublishedEvent>`, publishes to `${app.kafka.topic}`. **This will be replaced/retyped.**
- `order-service/src/main/java/com/example/orderservice/receiver/ArticleReceiver.java` — `@KafkaListener` on `${app.kafka.listen-topic}`, logs the event and pushes it to `/topic/messages` via `SimpMessagingTemplate`. **This will be replaced/retyped.**
- `order-service/src/main/java/com/example/orderservice/config/KafkaTopicConfig.java` — declares a `NewTopic` bean for `${app.kafka.topic}`.
- `order-service/src/main/resources/application.yml` — currently `app.kafka.topic: article-events`, `app.kafka.listen-topic: article-events-2`; consumer `spring.json.trusted.packages: com.example.sharedmodel` and `spring.json.value.default.type: com.example.sharedmodel.ArticlePublishedEvent`.
- `WebSocketConfig.java` and the security config are unaffected by this task — leave them as-is.
- No `Order` entity or repository exists yet anywhere in this module.

## Task

### 1. `application.yml`
- `app.kafka.topic: order-events` (this service's publish topic).
- `app.kafka.listen-topic: inventory-events` (this service's consume topic).
- `spring.kafka.consumer.properties.spring.json.value.default.type: com.example.sharedmodel.InventoryReservationEvent` (the type this service *consumes*).
- Leave `spring.json.trusted.packages: com.example.sharedmodel` as-is.

### 2. Local persistence — `model/Order.java`, `repository/OrderRepository.java`
- `Order` is a JPA `@Entity` (H2, same pattern as `auth-server`'s `UserEntity`):
  - `orderId` (`String`, `@Id` — a UUID generated when the order is created, **not** auto-increment)
  - `customerEmail` (`String`)
  - `items` — a list of line items. Use `@ElementCollection` of an `@Embeddable` class named **`OrderLineItem`** (do *not* reuse the name `OrderItem` — that's the `shared-model` record name for the wire contract; using a different name avoids confusion between the persisted line item and the wire DTO). `OrderLineItem` has `sku` (String), `quantity` (int).
  - `totalAmount` (`BigDecimal`)
  - `status` — enum `OrderStatus { PENDING, CONFIRMED, REJECTED }`, stored `@Enumerated(EnumType.STRING)`
  - `placedAt` (`Instant`), `updatedAt` (`Instant`)
- `OrderRepository extends JpaRepository<Order, String>`.

### 3. Dummy price catalog
Add a small component (e.g. `service/PriceCatalog.java`) with a hardcoded in-memory catalog — this is intentionally simple, not a real product service:

| SKU | Name | Unit price |
|---|---|---|
| `SKU-001` | Widget | `9.99` |
| `SKU-002` | Gadget | `24.50` |
| `SKU-003` | Gizmo | `4.25` |

Expose a method to look up a price by SKU, throwing/returning empty for unknown SKUs.

### 4. Rewrite the controller — `OrderController.java` (replaces `ArticleController.java`)
- `POST /api/orders` — request body `{ customerEmail: string, items: [{ sku: string, quantity: number }] }`.
  - Validate every SKU exists in the price catalog; if any is unknown, return `400` with a clear error message (don't create the order).
  - Compute `totalAmount` from the catalog.
  - Generate `orderId` (UUID string), persist an `Order` with `status = PENDING`, `placedAt = updatedAt = now`.
  - Build and publish an `OrderPlacedEvent` (via the publisher from step 5) using the same `orderId`, `customerEmail`, the submitted items (mapped to `shared-model`'s `OrderItem` record), `totalAmount`, and `placedAt`.
  - Return the created `Order` (200/201).
- `GET /api/orders` — returns all orders, newest first. No need to filter by current user for this showcase; keep it simple.

### 5. Rewrite the publisher — `OrderEventPublisher.java` (replaces `ArticlePublisherService.java`)
- Same shape as the current `ArticlePublisherService`, but `KafkaTemplate<String, OrderPlacedEvent>`, publishing to `${app.kafka.topic}` keyed by `orderId`. Keep the existing success/failure logging pattern (`SendResult` callback).

### 6. Rewrite the listener — `InventoryReservationListener.java` (replaces `ArticleReceiver.java`)
- `@KafkaListener(topics = "${app.kafka.listen-topic}", groupId = "${spring.kafka.consumer.group-id}")` consuming `InventoryReservationEvent`.
- Look up the `Order` by `event.orderId()`. If found: set `status` to `CONFIRMED` (if `event.status() == RESERVED`) or `REJECTED` (if `REJECTED`), set `updatedAt = now`, save.
- Push the updated order to the frontend via the existing `SimpMessagingTemplate.convertAndSend("/topic/messages", ...)` pattern — send the **full updated `Order` entity** (serializes to `{orderId, customerEmail, items, totalAmount, status, placedAt, updatedAt}`) so the UI can match it by `orderId` and update the right row without a full refetch. This is the contract the Order UI (task A-6) is built against — don't send a different shape.
- If no matching order is found, log a warning and skip (don't throw) — robust DLQ/retry handling is a later task (B-1), keep this simple for now.

### 7. `KafkaTopicConfig.java`
- Rename the bean from `articleEventsTopic()` to `orderEventsTopic()`, still backed by `${app.kafka.topic}` (now resolves to `order-events`).

### 8. `src/test/resources/application-test.yml`
- This file currently sets `spring.kafka.consumer.properties.spring.json.value.default.type: com.example.sharedmodel.ArticlePublishedEvent` for the embedded-Kafka integration test. Change it to `com.example.sharedmodel.InventoryReservationEvent` (the type this service consumes in tests) — otherwise the existing integration test won't compile/run once `ArticlePublishedEvent` is deleted in A-3. Update the existing integration test itself (the one using `@EmbeddedKafka` + `TestArticleConsumer`) to publish an `OrderPlacedEvent`-shaped flow and assert on `InventoryReservationEvent` consumption instead of the article roundtrip — keep the same test infrastructure (`@EmbeddedKafka`, `@ActiveProfiles("test")`, the security-autoconfiguration exclusions), just retarget the payloads.

## Out of scope
- Don't touch `inventory-service` in this task — that's A-5.
- Don't implement retry/DLQ/idempotency — that's B-1.
- Don't add OpenAPI annotations — that's B-4.

## Acceptance criteria
- `cd order-service && ./mvnw clean compile` succeeds.
- `POST /api/orders` with `{"customerEmail":"customer1@example.test","items":[{"sku":"SKU-001","quantity":2}]}` (with a valid bearer token) returns `200`/`201` with `totalAmount: 19.98`, `status: "PENDING"`, and a generated `orderId`.
- `POST /api/orders` with an unknown SKU returns `400` and creates no `Order` row.
- The `OrderPlacedEvent` published to `order-events` is verifiable via a test consumer (or `kafka-console-consumer.sh --topic order-events`) and matches the request.
- Manually publishing an `InventoryReservationEvent{orderId: <that orderId>, status: RESERVED}` onto `inventory-events` results in `GET /api/orders` showing that order's `status` as `CONFIRMED`.
