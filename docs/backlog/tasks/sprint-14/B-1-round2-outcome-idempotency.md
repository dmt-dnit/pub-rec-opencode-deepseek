# B-1 round 2 — Outcome-idempotency (Codex P2)

**Sprint:** 14 (Track B Sprint 1), round 2
**Source:** `reviews/sprint-14-track-b-review.md` §P2
**Priority:** Should-fix gap in B-1's "idempotent listeners" goal
**Implementer:** Claude sonnet worktree agent (decided with Dimitri 2026-06-29; I hold full context on the exact fix; P1 already fixed coordinator-direct).
**Scope:** `inventory-service` only — `service/ReservationService.java`, `receiver/OrderEventListener.java`, tests. Do NOT touch order-service, shared-model, auth-server, UI, or poms.

## Problem (confirmed in current code on main)

`ReservationService.reserve(...)` is stock-idempotent but not **outcome**-idempotent:

1. **Duplicate success re-publishes.** Lines 33–41: when `orderId` is already processed it returns a *fresh* `RESERVED` event. `OrderEventListener` (lines 35–37) then publishes it to `inventory-events` AND pushes a WebSocket feed item again. So one input `OrderPlacedEvent` → two output events. Violates "one input event → one output event."
2. **Duplicate rejection isn't deduped at all.** Lines 43–53: the `REJECTED` path returns *before* saving any `ProcessedOrder` marker. A redelivered rejected order is fully re-evaluated — and can produce a different result if stock changed in between.

## Required behavior

For **both** RESERVED and REJECTED, a redelivered `OrderPlacedEvent` (same `orderId`) must cause **no second** published event and **no second** WebSocket feed item, and no stock change. One inbound event → exactly one outbound outcome, ever.

## Implementation (Codex-suggested "no-op" direction)

1. Change `ReservationService.reserve(...)` to return `Optional<InventoryReservationEvent>`, all within the existing `@Transactional`:
   - If `processedOrderRepository.existsById(orderId)` → `return Optional.empty()` (duplicate → no-op, do not re-publish).
   - On the REJECTED branch → **save `new ProcessedOrder(orderId)` before returning**, then `return Optional.of(rejected)`.
   - On the success branch → save marker (as today), `return Optional.of(reserved)`.
2. Update `OrderEventListener.onOrderPlaced(...)`:
   ```java
   reservationService.reserve(event).ifPresent(outcome -> {
       publisher.publish(outcome);
       messagingTemplate.convertAndSend("/topic/messages", outcome);
   });
   ```
3. `ProcessedOrder` stays keyed on `orderId` only — the no-op approach doesn't need to store the outcome. (We accept the standard at-least-once edge: if the first delivery's publish were lost, the duplicate no-ops; out of scope for this hardening bar.)

## Tests (EmbeddedKafka, no Docker — same style as existing B-1 tests)

Keep the existing `OrderEventListenerIdempotencyTest` (no-double-decrement) green, and add/extend to prove **outcome**-idempotency:
- **Duplicate success:** send the same satisfiable `OrderPlacedEvent` twice → assert the inventory output (`InventoryEventPublisher.publish` / the `inventory-events` topic) fired **exactly once** and `convertAndSend` **exactly once**, and stock decremented once.
- **Duplicate rejection:** send the same unsatisfiable `OrderPlacedEvent` twice (SKU with 0 stock, e.g. SKU-003) → assert exactly **one** REJECTED output published, the `ProcessedOrder` marker exists, and a second delivery is a no-op (no second publish/feed, stock unchanged).

Prefer asserting on a spy/mock of `InventoryEventPublisher` and `SimpMessagingTemplate` with `times(1)` (mirrors how the order-service idempotency test was tightened). Don't loosen to `atLeast` — if `times(1)` fails the main code is wrong, not the test.

## Acceptance criteria

1. `cd inventory-service && ./mvnw test` passes — show real output. All prior tests still green.
2. Duplicate success → exactly one published `InventoryReservationEvent` + one feed item (verify `times(1)`).
3. Duplicate rejection → exactly one published REJECTED + marker saved + no-op on redelivery, stock unchanged.
4. Scope: inventory-service only; `git diff --name-only` shows nothing outside it.
5. Commit on the worktree branch; report SHA, files changed, full test output. State anything you couldn't run.
