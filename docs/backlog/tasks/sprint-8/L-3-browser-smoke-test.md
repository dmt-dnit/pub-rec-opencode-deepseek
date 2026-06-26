# Task L-3: Execute and document the browser smoke test

**Resolves:** The one unmet must-fix that has blocked Track A close from Sprint 4 through Sprint 7: a real login → order placement → live WebSocket status update → inventory live feed flow, documented step by step.

**Depends on:** L-1 (WebSocket fix) and L-2 (proxy fix) must be merged before this task can pass.

## Context

This requirement has been in the acceptance criteria for five consecutive sprints (H-3, I-2, J-2, K-2, now L-3). Every prior attempt was satisfied with a proxy signal — build success, route availability, HTTP 200 — instead of the real flow. Codex has called this out by name in every review. L-1 removes the crash that blocked K-2; L-3 is the actual test.

Do not substitute any lighter check. "The build passes" is not this task. "The dev server responds on port 4200" is not this task.

## Task

### 1. Start the full local stack

In this order (per `CLAUDE.md` "Full local stack"):

```bash
# Backend — containerized (preferred):
bash scripts/startup-all.sh

# OR bare-metal:
#   cd auth-server    && ./mvnw spring-boot:run   # port 9000
#   cd order-service  && ./mvnw spring-boot:run   # port 8080
#   cd inventory-service && ./mvnw spring-boot:run # port 8081

# Frontends always run on the host:
cd order-ui    && npm start    # port 4200
cd inventory-ui && npm start   # port 4201
```

The Kafka broker must be running before `order-service` and `inventory-service` start (`order-service/docker-compose.yml` provides it).

Seeded credentials are in `auth-server/src/main/java/com/example/authserver/DataSeeder.java`. Seeded SKUs with stock are in `inventory-service/src/main/java/com/example/inventoryservice/config/InventoryDataSeeder.java` (`SKU-001` quantity 50, `SKU-002` quantity 5).

### 2. order-ui smoke flow (`http://localhost:4200`)

1. Navigate to `http://localhost:4200` (not `/login` — use root to avoid the proxy).
2. Log in with a `CUSTOMER`-role seeded account (e.g. `customer1@example.test / customer123`).
3. Confirm the dashboard renders — order list/placement form visible, no blank page, no console errors at this point.
4. Place an order for `SKU-001` (quantity 50 in stock — should succeed).
5. Watch the order status transition from `PENDING` to `CONFIRMED` **without a page reload**. This is the live WebSocket update the saga exists to demonstrate.
6. Record any browser console errors during this entire flow.

### 3. inventory-ui smoke flow (`http://localhost:4201`)

1. Navigate to `http://localhost:4201`.
2. Log in with a `WAREHOUSE_STAFF`-role seeded account (e.g. `warehouse1@example.test / warehouse123`).
3. Confirm the stock table loads with SKU quantities visible.
4. Confirm the live reservation feed shows the event from the order placed in step 2.2, and that the quantity for `SKU-001` visibly decremented.
5. Record any browser console errors.

### 4. Document the result

Your output must answer all of these explicitly — "it works" is not acceptable:

- Which account did you log in as for each UI?
- Which SKU did you order, and what quantity?
- What status did the order reach, and did it update live (without reload)?
- What did the inventory feed show after the order was placed?
- Were there any browser console errors in either app during the flow? (Even non-fatal ones.)
- If any step failed, say so plainly and describe what you saw instead.

## Acceptance criteria

- A real login → order placement → live status update → inventory live feed flow is described step by step, with the specific account, SKU, and outcome used — not a generic "tested and it works."
- The order status transitions from `PENDING` to `CONFIRMED` (or `REJECTED` if a zero-stock SKU was deliberately chosen) via live WebSocket update, without a page reload.
- Any console errors encountered are reported, even if the flow still completed.
- If any step in the flow fails, that is a real, reportable finding — say so plainly rather than omitting the step.
- Zero `ReferenceError: global is not defined` errors appear in either browser console.

## Out of scope
- Do not fix bugs you notice during the smoke test unless they block the flow entirely — note them as a finding instead.
- Do not change any source files in this task.
