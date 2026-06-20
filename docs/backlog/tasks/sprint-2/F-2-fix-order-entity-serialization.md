# Task F-2: Fix `order-service`'s entity-over-the-wire serialization bug (WebSocket and REST)

**Resolves:** Blocker 2 in `reviews/sprint-1-track-a-review.md`, plus a second instance of the same root cause the review's test run didn't happen to trigger.

## Context
`order-service`'s `Order` entity has `items` mapped as `@ElementCollection`, which Hibernate fetches **lazily** by default. Two places in the codebase serialize an `Order` (or a `List<Order>`) to JSON *after* the Hibernate session that loaded it has already closed — Jackson then tries to read `items` and Hibernate throws `LazyInitializationException`, because there's no active session left to fetch it from.

1. `InventoryReservationListener.onInventoryReservation()` (`order-service/src/main/java/com/example/orderservice/receiver/InventoryReservationListener.java:41`): saves the updated `Order`, then immediately calls `messagingTemplate.convertAndSend("/topic/messages", saved)` — `save()`'s implicit transaction has already closed by the time Jackson serializes `saved` for the WebSocket frame. Codex's review caught this one directly (`mvn test` reproduces it).
2. `OrderController.getAllOrders()` (`order-service/src/main/java/com/example/orderservice/controller/OrderController.java`, the `@GetMapping` method): returns `orderRepository.findAll()` directly as the response body. Same problem — Spring serializes the response *after* the controller method returns, i.e. after the request-scoped transaction from `findAll()` has closed. This wasn't caught by the existing test suite because nothing exercises `GET /api/orders` through real HTTP/Jackson serialization (the only test for this path queries the repository directly in Java, never going through JSON). It is the same bug and needs the same fix.

The root design issue: this codebase is serializing a JPA entity directly as a wire format, in both the REST response and the WebSocket push. That's exactly the entity-vs-contract conflation that `task001_share_flows_own_entities.md` and ADR-0001 already called out as the wrong pattern for cross-boundary data — it just resurfaced one layer down (HTTP/WS response shape vs. persisted entity), not at the `shared-model` layer this time.

## Task

### 1. Add a response/view DTO — `OrderResponse.java`
Add `order-service/src/main/java/com/example/orderservice/controller/OrderResponse.java` (or `dto/OrderResponse.java` if you prefer a `dto` package — be consistent with the rest of the module) as a plain Java record:
```java
public record OrderResponse(
        String orderId,
        String customerEmail,
        List<LineItem> items,
        BigDecimal totalAmount,
        OrderStatus status,
        Instant placedAt,
        Instant updatedAt
) {
    public record LineItem(String sku, int quantity) {}

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getOrderId(),
                order.getCustomerEmail(),
                order.getItems().stream().map(i -> new LineItem(i.getSku(), i.getQuantity())).toList(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getPlacedAt(),
                order.getUpdatedAt()
        );
    }
}
```
The JSON shape must stay exactly what `order-ui`'s `Order` model expects: `{orderId, customerEmail, items: [{sku, quantity}], totalAmount, status, placedAt, updatedAt}` — this is a serialization-layer fix, not a contract change. Adjust field names above only if `OrderLineItem`'s actual getters differ from what's assumed here (check the real class before copying this verbatim).

### 2. Fix the WebSocket push
In `InventoryReservationListener.onInventoryReservation()`:
- Make the method (or wrap the relevant block) `@Transactional` so `order.getItems()` can be initialized while the Hibernate session is still open.
- Inside that transactional scope, after `orderRepository.save(order)`, call `OrderResponse.from(saved)` to force-initialize and map the collection, **then** call `messagingTemplate.convertAndSend("/topic/messages", orderResponse)` — sending the DTO, not the entity.

### 3. Fix the REST response
- Change `OrderController.getAllOrders()`'s return type to `List<OrderResponse>`, mapping each `Order` via `OrderResponse.from(...)` before returning. Wrap the method `@Transactional(readOnly = true)` if it isn't already covered by one (check whether `OrderRepository.findAll()` alone is enough — it usually isn't outside an explicitly transactional service/controller method).
- Do the same for `OrderController.placeOrder()`'s response — it currently returns the freshly-constructed `Order` directly. Since that `Order` was just built in-memory in the same method (not lazily loaded from the DB), it likely doesn't hit the same bug, but convert it to `OrderResponse` anyway for consistency — there should be exactly one JSON shape order-service ever returns for an order, used by both endpoints and the WS push.

## Out of scope
- Don't change the `Order` entity's `@ElementCollection` to `FetchType.EAGER` as an alternative fix — that's the wrong direction (it would force a join-fetch on every `findAll()`/`findById()` call regardless of whether the caller needs `items`, which is the standard JPA performance anti-pattern this exists to avoid). The DTO + transactional-mapping approach above is the intended fix.
- Don't touch `shared-model`, `inventory-service`, or either Angular app in this task — the WS JSON shape is unchanged, just no longer leaking JPA proxies.

## Acceptance criteria
- `cd order-service && ./mvnw test` passes, including the existing `OrderEventIntegrationTest`.
- **Strengthen `OrderEventIntegrationTest`** (or add a new test) so it actually proves the WebSocket push succeeds, not just that the DB row updated — e.g. subscribe a test STOMP client to `/topic/messages` before publishing the `InventoryReservationEvent`, and assert it receives a well-formed `OrderResponse` JSON payload with the order's `orderId` and `status: CONFIRMED`. The previous test passed even with the live bug present, because it only polled the database — don't repeat that gap.
- Add a test (e.g. `@WebMvcTest` or a full `@SpringBootTest` with `TestRestTemplate`/`MockMvc`) that calls `GET /api/orders` and asserts the JSON response actually serializes successfully with a non-empty `items` array — this is the test that was missing and let Blocker 2's second instance through.
- Manually placing an order and watching the order's status flip live in `order-ui` (once F-1 is also merged) should work with no exception in the `order-service` logs.
