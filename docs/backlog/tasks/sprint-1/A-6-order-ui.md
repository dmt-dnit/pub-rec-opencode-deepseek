# Task A-6: Order UI — order placement + live status

**Depends on:** A-2 (renamed to `order-ui`) and A-4 (Order Service exposes `POST /api/orders`, `GET /api/orders`, and pushes the full `Order` over `/topic/messages` on status change) must both be merged first.

## Context
`order-ui` is an Angular 18 + Angular Material standalone-component app. It currently has an article-publishing form and a raw WebSocket feed; this task replaces that with an order placement form and a live order list. Auth (`auth.service.ts`, `auth.guard.ts`, `auth.interceptor.ts`), routing, and the login/register pages are **unaffected by this task** — leave them as-is.

## Current state
- `order-ui/src/app/models/user.model.ts` — has `UserInfo`, `LoginResponse`, and `ArticleEvent { id, title, author, publishedAt }` (to be removed).
- `order-ui/src/app/services/article.service.ts` — `ArticleService.publish(event)` → `POST /api/articles/publish`. **Replace.**
- `order-ui/src/app/services/websocket.service.ts` — STOMP/SockJS client connecting to `/ws`, subscribing to `/topic/messages`, emitting `ArticleEvent` over `messages$`. **Generalize the payload type, keep the connection logic.**
- `order-ui/src/app/pages/dashboard/dashboard.component.ts` — standalone component with a publish form + live message list. **Replace the form and list; keep the toolbar/logout structure.**

## Task

### 1. `models/user.model.ts`
- Remove `ArticleEvent`.
- Add:
  ```typescript
  export interface OrderLineItemRequest { sku: string; quantity: number; }
  export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED';
  export interface Order {
    orderId: string;
    customerEmail: string;
    items: { sku: string; quantity: number }[];
    totalAmount: number;
    status: OrderStatus;
    placedAt: string;
    updatedAt: string;
  }
  ```

### 2. `services/order.service.ts` (replaces `article.service.ts`)
- `placeOrder(customerEmail: string, items: OrderLineItemRequest[]): Observable<Order>` → `POST /api/orders` with `{ customerEmail, items }`.
- `listOrders(): Observable<Order[]>` → `GET /api/orders`.
- Delete `article.service.ts`.

### 3. `services/websocket.service.ts`
- Change the generic payload type from `ArticleEvent` to `Order` (the backend now pushes the full `Order` on `/topic/messages` — see A-4). Connection/reconnect/SockJS logic stays the same.

### 4. `pages/dashboard/dashboard.component.ts`
- Keep the toolbar (title → "Order UI") and logout button as-is.
- Replace the publish form with an order placement form:
  - `customerEmail` text input (default it to the logged-in user's email from `auth.user$` if available, but allow editing).
  - A fixed set of three quantity inputs, one per known SKU (this is a deliberately simple catalog for the showcase — don't build a dynamic add/remove line-item UI):

    | SKU | Label |
    |---|---|
    | `SKU-001` | Widget |
    | `SKU-002` | Gadget |
    | `SKU-003` | Gizmo |

    A quantity of `0` (the default) means that SKU is excluded from the order. On submit, build the `items` array from only the SKUs with quantity `> 0`; if none are `> 0`, show a validation error instead of submitting.
  - On successful `placeOrder()`, show a snackbar with the returned `orderId` and `totalAmount`, clear the form, and prepend the new `Order` to the order list (optimistic — it'll arrive at `PENDING` and update live once the saga completes).
- Replace the live message list with an order list:
  - On `ngOnInit`, call `listOrders()` to populate initial state, then subscribe to the WebSocket `messages$` (now typed `Order`) and, for each incoming `Order`, replace the existing list entry with the same `orderId` (or prepend if not present).
  - Render each order as: `orderId` (can truncate to first 8 chars for display), `customerEmail`, `totalAmount` (currency-formatted), a status badge (`PENDING` = neutral/gray, `CONFIRMED` = green, `REJECTED` = red), and `placedAt`.
  - Keep the existing "Connected"/"Disconnected" WebSocket indicator.

## Out of scope
- Don't touch auth, routing, guards, interceptors, login/register pages.
- Don't add e2e tests — that's a Track B concern if added at all.

## Acceptance criteria
- `npm start` in `order-ui` serves without build errors.
- Submitting the form with Widget quantity 2 and the others at 0 results in a `POST /api/orders` with `items: [{sku: "SKU-001", quantity: 2}]` and the new order appears in the list as `PENDING`.
- Within a few seconds (assuming `order-service` and `inventory-service` are both running against a live broker), that order's status visibly updates to `CONFIRMED` or `REJECTED` without a page reload.
- Submitting with all quantities at `0` shows a validation error and makes no HTTP request.
