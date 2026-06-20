# Task F-3: Fix `inventory-service`'s test Kafka listener type conflict

**Resolves:** Blocker 3 in `reviews/sprint-1-track-a-review.md`. Read this whole brief before touching code — the actual root cause is more specific than the review's summary suggests, and the obvious-looking fix (just use two topics) won't fix it on its own.

## Context
`inventory-service`'s test suite fails on Java 21 with `Cannot convert from [com.example.sharedmodel.OrderPlacedEvent] to [com.example.sharedmodel.InventoryReservationEvent]`.

## Root cause (verified by reading the actual files, not just the error message)
`inventory-service/src/test/resources/application-test.yml` sets one consumer-wide default:
```yaml
spring.kafka.consumer.properties.spring.json.value.default.type: com.example.sharedmodel.OrderPlacedEvent
```
This is correct **for the real production listener** (`OrderEventListener`, which genuinely consumes `OrderPlacedEvent`). The problem is that the test (`InventoryIntegrationTest.java`) adds a *second* `@KafkaListener` into the same Spring test context — `TestReservationConsumer`, which exists purely to let the test assert on the `InventoryReservationEvent` the service publishes back out. Spring Boot's autoconfigured Kafka listener container factory is shared by every `@KafkaListener` in the context unless told otherwise, so `TestReservationConsumer` inherits the same `value.default.type: OrderPlacedEvent` setting that's correct for `OrderEventListener` but wrong for itself. Putting the two events on separate topics (as the test already does — `order-events`-equivalent vs. the reservation flow both currently collapse onto one topic, `test-integration-topic-2`, in the test) **does not fix this on its own**, because the default-type setting isn't topic-scoped — it's applied to whichever listener container factory handles the message, regardless of topic.

## Task

### 1. Use two separate embedded-Kafka topics
In `InventoryIntegrationTest.java`'s `@EmbeddedKafka` annotation and the test's topic overrides, stop reusing one topic for both directions. Use two, e.g. `test-orders-in` (carries the `OrderPlacedEvent` the test publishes to trigger reservation logic) and `test-reservations-out` (carries the `InventoryReservationEvent` the service publishes back, which `TestReservationConsumer` listens to). Set `app.kafka.listen-topic=test-orders-in` and `app.kafka.topic=test-reservations-out` via the existing `System.setProperty(...)` static block pattern. This mirrors production's real topic separation (`order-events` / `inventory-events`) and makes the test easier to reason about — but by itself this is necessary, not sufficient; step 2 is the actual fix.

### 2. Give the test-only consumer its own listener container factory
Add a dedicated `ConcurrentKafkaListenerContainerFactory<String, InventoryReservationEvent>` bean, scoped to the test (e.g. a static nested `@TestConfiguration` class inside `InventoryIntegrationTest`, or a separate test-only `@Configuration` class under `src/test/java`), configured with its own `JsonDeserializer<InventoryReservationEvent>` (constructed with that target type directly, e.g. `new JsonDeserializer<>(InventoryReservationEvent.class)` wrapped in an `ErrorHandlingDeserializer`, or via consumer factory properties scoped to this bean only — don't change the autoconfigured default factory's properties). Reference it explicitly:
```java
@KafkaListener(topics = "test-reservations-out", groupId = "test-group-2",
                containerFactory = "testReservationContainerFactory")
```
Leave the default autoconfigured factory (driven by `application-test.yml`'s `spring.kafka.consumer.*`, default type `OrderPlacedEvent`) completely untouched — it continues to correctly serve the real `OrderEventListener` on `test-orders-in`.

### 3. Verify the production code path is unaffected
This task only changes test wiring (`src/test/**`) and `application-test.yml`. Do not change `inventory-service`'s production `application.yml`, `OrderEventListener`, or `InventoryEventPublisher` — production already works correctly because each real service only ever has one consumed event type in its context (this dual-type problem is purely an artifact of the test needing to listen for its own service's output).

## Acceptance criteria
- `cd inventory-service && ./mvnw test` passes on Java 21, including `InventoryIntegrationTest`.
- The test still proves the real end-to-end behavior: publishing an `OrderPlacedEvent` for a SKU with sufficient stock results in `TestReservationConsumer` receiving an `InventoryReservationEvent{status: RESERVED}` for the same `orderId`.
- `grep -n "value.default.type" inventory-service/src/test/resources/application-test.yml` still shows exactly one line, unchanged, set to `OrderPlacedEvent` — confirming the fix didn't come from weakening or removing that setting, but from isolating the test consumer onto its own factory.
