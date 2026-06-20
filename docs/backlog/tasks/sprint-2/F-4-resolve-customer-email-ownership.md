# Task F-4: Resolve `customerEmail` ownership — derive from JWT, remove the dead field

**Resolves:** Should-fix 1 in `reviews/sprint-1-track-a-review.md`.

## Context
Task A-4's original brief specified `POST /api/orders` accepting `customerEmail` in the request body. What actually got built ignores that field and derives the customer's email from the authenticated JWT subject instead (`order-service/src/main/java/com/example/orderservice/controller/OrderController.java`), while `order-ui`'s dashboard still shows an editable `customerEmail` text input that has no effect on the resulting order. This is a decision that needs making, not just a bug to mechanically patch — **the decision has been made for you below; implement it.**

## Decision: derive `customerEmail` from the JWT subject. Do not accept it from the request body.

Rationale: `auth-server` already issues a JWT whose subject is the authenticated user's email. Accepting a client-supplied `customerEmail` in the request body and using it as-is would let any authenticated user place an order *as* a different customer simply by editing the request — that's a real authorization smell, not just an inconsistency, and it's not something a consultancy's own reference implementation should model even in a showcase. The backend behavior DeepSeek actually built (derive from JWT) is the correct one; the bug is that the request DTO and the UI still pretend otherwise.

## Task

### `order-service`
- `OrderController.OrderRequest` record: remove the `customerEmail` field. It should be `record OrderRequest(List<Item> items)`.
- `OrderController.placeOrder()`: remove any reference to `request.customerEmail()` (there shouldn't be one currently, since the method already derives `customerEmail` from `jwt.getSubject()` — just confirm the request record change doesn't break compilation and that nothing silently still reads the old field).

### `order-ui`
- `pages/dashboard/dashboard.component.ts`: remove the editable `customerEmail` input from the order placement form. Instead, show the logged-in user's email as **read-only** context (e.g. "Placing order as: {{ user.email }}") so it's still visible in the UI without implying it's editable.
- `services/order.service.ts`: update `placeOrder()`'s signature to stop sending `customerEmail` in the request body — it should only send `{ items }`.
- `models/user.model.ts`: if `OrderLineItemRequest`/request-shaped interfaces include `customerEmail`, remove it there too. The `Order` *response* interface keeps `customerEmail` — the server still returns it (derived from the JWT at creation time), this change only affects what the client sends, not what it receives.

## Out of scope
- Don't change `auth-server` or the JWT issuance flow.
- Don't add a "place order on behalf of another customer" admin feature — that's a real feature, not a bug fix, and isn't part of this showcase's scope.

## Acceptance criteria
- `grep -rn "customerEmail" order-ui/src/app/pages/dashboard order-ui/src/app/services/order.service.ts order-ui/src/app/models/user.model.ts` shows it only appearing in display/response contexts, never in a request body being sent to the server.
- `cd order-service && ./mvnw clean compile` succeeds with the trimmed `OrderRequest` record.
- Placing an order via the UI while logged in as `customer1@example.test` results in a created order whose `customerEmail` is `customer1@example.test`, with no way for the UI to submit a different value.
