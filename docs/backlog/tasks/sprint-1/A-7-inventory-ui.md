# Task A-7: Inventory UI — stock dashboard + live reservation feed

**Depends on:** A-2 (renamed to `inventory-ui`) and A-5 (Inventory Service exposes `GET /api/inventory` and pushes `InventoryReservationEvent` over `/topic/messages`) must both be merged first.

## Context
`inventory-ui` is structurally identical to `order-ui` (same Angular 18 + Material setup, same auth/routing/guard/interceptor code) but paired with the Inventory Service instead. This task replaces its article-publishing form and message feed with a read-only stock dashboard and a live reservation feed — there's no "publish" action in this UI, inventory only reacts to orders. Auth, routing, guards, interceptors, login/register pages are **unaffected** — leave them as-is.

## Current state
- `inventory-ui/src/app/models/user.model.ts` — has `ArticleEvent { id, title, author, publishedAt }` (to be removed).
- `inventory-ui/src/app/services/article.service.ts` — `ArticleService.publish(event)`. **Delete — there is no publish action in this UI.**
- `inventory-ui/src/app/services/websocket.service.ts` — STOMP/SockJS client, `messages$` typed `ArticleEvent`. **Generalize the payload type.**
- `inventory-ui/src/app/pages/dashboard/dashboard.component.ts` — publish form + live message list. **Replace with a stock table + reservation feed; keep the toolbar/logout structure.**

## Task

### 1. `models/user.model.ts`
- Remove `ArticleEvent`.
- Add:
  ```typescript
  export interface Product { sku: string; name: string; quantityOnHand: number; }
  export type ReservationStatus = 'RESERVED' | 'REJECTED';
  export interface InventoryReservation {
    orderId: string;
    status: ReservationStatus;
    reason: string | null;
    processedAt: string;
  }
  ```

### 2. `services/inventory.service.ts` (replaces `article.service.ts`)
- `listProducts(): Observable<Product[]>` → `GET /api/inventory`.
- Delete `article.service.ts`.

### 3. `services/websocket.service.ts`
- Change the generic payload type from `ArticleEvent` to `InventoryReservation` (the backend now pushes the `InventoryReservationEvent` shape on `/topic/messages` — see A-5). Connection/reconnect/SockJS logic stays the same.

### 4. `pages/dashboard/dashboard.component.ts`
- Keep the toolbar (title → "Inventory UI") and logout button as-is.
- Replace the publish form with a **read-only stock table**: SKU, name, quantity on hand. Populate it via `listProducts()` on `ngOnInit`. There's no "Connected" indicator change needed beyond what already exists.
- After placing the stock table, **also re-fetch and refresh it whenever a new reservation event arrives** over the WebSocket (a `RESERVED` reservation means stock just changed) — simplest correct approach is to call `listProducts()` again inside the `messages$` subscription handler rather than trying to compute the delta client-side.
- Replace the live message list with a reservation feed:
  - Subscribe to `messages$` (now typed `InventoryReservation`) and prepend each incoming entry to a list.
  - Render each entry as: `orderId` (truncate to first 8 chars for display), a status badge (`RESERVED` = green, `REJECTED` = red), `reason` (shown only when present, i.e. for `REJECTED`), and `processedAt`.
  - Keep the existing "Connected"/"Disconnected" WebSocket indicator.

## Out of scope
- Don't add any write/mutation UI — this app only displays state, it never calls `order-service` or mutates inventory directly.
- Don't touch auth, routing, guards, interceptors, login/register pages.

## Acceptance criteria
- `npm start` in `inventory-ui` serves without build errors.
- On load, the stock table shows the three seeded products (`SKU-001`/`SKU-002`/`SKU-003`) with their current quantities.
- After an order is placed (via `order-ui` or directly via the Order Service API) that successfully reserves stock, the affected product's `quantityOnHand` visibly decreases in this UI without a page reload, and a `RESERVED` entry appears in the reservation feed.
- An order that gets rejected for insufficient stock produces a `REJECTED` entry in the feed showing the `reason`, and the stock table is unchanged.
