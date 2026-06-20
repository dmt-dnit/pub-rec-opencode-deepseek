# Task A-5: Inventory Service — own `Product`/`Stock`, consume orders, decide reservation, publish outcome

**Depends on:** A-1 (rename to `inventory-service`/`com.example.inventoryservice`) and A-3 (`shared-model` now has `OrderPlacedEvent`, `OrderItem`, `InventoryReservationEvent`) must both be merged first. Can be done in parallel with A-4 (different module), but the catalog SKUs below must stay consistent with A-4's price catalog — they're the same three SKUs by design.

## Context
`inventory-service` is being repurposed from an article-publishing demo into the Inventory side of a choreographed saga: it owns product stock, reacts to `OrderPlacedEvent` by attempting to reserve stock, and publishes the outcome as `InventoryReservationEvent` for the Order Service to react to.

## Current state (post A-1/A-3, pre this task)
- `inventory-service/src/main/java/com/example/inventoryservice/controller/ArticleController.java` — `POST /api/articles/publish`. **Replaced.**
- `inventory-service/src/main/java/com/example/inventoryservice/publisher/ArticlePublisherService.java` — wraps `KafkaTemplate<String, ArticlePublishedEvent>`. **Replaced/retyped.**
- `inventory-service/src/main/java/com/example/inventoryservice/receiver/ArticleReceiver.java` — `@KafkaListener`, pushes to `/topic/messages`. **Replaced/retyped.**
- `inventory-service/src/main/java/com/example/inventoryservice/config/KafkaTopicConfig.java` — declares a `NewTopic` bean for `${app.kafka.topic}`.
- `inventory-service/src/main/resources/application.yml` — currently `app.kafka.topic: article-events-2`, `app.kafka.listen-topic: article-events`; consumer `spring.json.trusted.packages: com.example.sharedmodel`, `spring.json.value.default.type: com.example.sharedmodel.ArticlePublishedEvent`.
- No `Product` entity exists yet.

## Task

### 1. `application.yml`
- `app.kafka.topic: inventory-events` (this service's publish topic).
- `app.kafka.listen-topic: order-events` (this service's consume topic).
- `spring.kafka.consumer.properties.spring.json.value.default.type: com.example.sharedmodel.OrderPlacedEvent` (the type this service *consumes*).
- Leave `spring.json.trusted.packages: com.example.sharedmodel` as-is.

### 2. Local persistence — `model/Product.java`, `repository/ProductRepository.java`
- `Product` is a JPA `@Entity` (H2): `sku` (`String`, `@Id`), `name` (`String`), `quantityOnHand` (`int`).
- `ProductRepository extends JpaRepository<Product, String>`.

### 3. Seed data — `InventoryDataSeeder.java` (`CommandLineRunner`, same pattern as `auth-server`'s `DataSeeder`)
Seed exactly this catalog on startup if the table is empty — quantities are deliberately uneven so some orders succeed and some realistically get rejected for the demo:

| SKU | Name | Quantity on hand |
|---|---|---|
| `SKU-001` | Widget | `50` |
| `SKU-002` | Gadget | `5` |
| `SKU-003` | Gizmo | `0` |

### 4. Reservation logic — `service/ReservationService.java`
- Method taking an `OrderPlacedEvent` and returning the outcome:
  - For every item, look up the `Product` by SKU.
  - If **all** items have `quantityOnHand >= requested quantity`, decrement each in the same transaction (`@Transactional`) and return `RESERVED` (no `reason`).
  - If **any** item is short (including an unrecognized SKU — treat as zero stock), make **no** changes to any product's quantity (no partial decrement) and return `REJECTED` with `reason` naming the first failing SKU, e.g. `"Insufficient stock for SKU-003"`.

### 5. Rewrite the listener — `OrderEventListener.java` (replaces `ArticleReceiver.java`)
- `@KafkaListener(topics = "${app.kafka.listen-topic}", groupId = "${spring.kafka.consumer.group-id}")` consuming `OrderPlacedEvent`.
- Call `ReservationService`, build an `InventoryReservationEvent(orderId, status, reason, Instant.now())`, publish it via the publisher (step 6).
- Push the same outcome to the frontend via the existing `SimpMessagingTemplate.convertAndSend("/topic/messages", ...)` pattern — send the **`InventoryReservationEvent` itself** (serializes to `{orderId, status, reason, processedAt}`) so the Inventory UI's live feed (A-7) can show it without polling. This is the contract the Inventory UI (task A-7) is built against — don't send a different shape.

### 6. Rewrite the publisher — `InventoryEventPublisher.java` (replaces `ArticlePublisherService.java`)
- Same shape as the current publisher, but `KafkaTemplate<String, InventoryReservationEvent>`, publishing to `${app.kafka.topic}` keyed by `orderId`. Keep the existing success/failure logging pattern.

### 7. New read endpoint — `InventoryController.java`
- `GET /api/inventory` — returns all products with their current `quantityOnHand`, for the Inventory UI's initial dashboard load (A-7).

### 8. `KafkaTopicConfig.java`
- Rename the bean from `articleEventsTopic()` to `inventoryEventsTopic()`, backed by `${app.kafka.topic}` (now resolves to `inventory-events`).

### 9. `src/test/resources/application-test.yml`
- This file currently sets `spring.kafka.consumer.properties.spring.json.value.default.type: com.example.sharedmodel.ArticlePublishedEvent` for the embedded-Kafka integration test. Change it to `com.example.sharedmodel.OrderPlacedEvent` (the type this service consumes in tests) — otherwise the existing integration test won't compile/run once `ArticlePublishedEvent` is deleted in A-3. Update the existing integration test itself to publish an `OrderPlacedEvent` and assert on the resulting `InventoryReservationEvent`, keeping the same test infrastructure (`@EmbeddedKafka`, `@ActiveProfiles("test")`, the security-autoconfiguration exclusions).

## Out of scope
- Don't touch `order-service` in this task — that's A-4.
- Don't implement retry/DLQ/idempotency (e.g. redelivery of the same `OrderPlacedEvent` double-decrementing stock) — that's B-1. It's fine for this task to be naively non-idempotent.
- Don't add OpenAPI annotations — that's B-4.

## Acceptance criteria
- `cd inventory-service && ./mvnw clean compile` succeeds.
- On startup, `GET /api/inventory` returns the three seeded products with the quantities above.
- Manually publishing an `OrderPlacedEvent` requesting `SKU-001` qty `1` onto `order-events` results in: `inventory-events` receiving an `InventoryReservationEvent{status: RESERVED}` for that `orderId`, and `GET /api/inventory` showing `SKU-001` at `quantityOnHand: 49`.
- Manually publishing an `OrderPlacedEvent` requesting `SKU-003` qty `1` (zero stock) results in an `InventoryReservationEvent{status: REJECTED, reason: "Insufficient stock for SKU-003"}`, and `GET /api/inventory` shows all quantities unchanged.
