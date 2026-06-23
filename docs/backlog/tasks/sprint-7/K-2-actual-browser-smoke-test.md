# Task K-2: Perform and document the actual browser smoke test

**Resolves:** Must-fix (part 2) in `reviews/sprint-6-track-a-review.md` ("Instead, `CLAUDE.md:52` says only 'Dev servers respond on 4200/4201; SPA routes confirmed'... Route availability is not the required end-to-end flow verification").

## Context

This exact requirement has been in the acceptance criteria of an Angular-related brief for four sprints running (Sprint 4's `H-3`, Sprint 5's `I-2`, Sprint 6's `J-2`, now this one) and has been satisfied with route-availability or build-success checks every time, never the actual user flow. Codex has called this out by name three review rounds in a row. **Confirming a route returns HTTP 200 is not a smoke test of the feature.** This task exists specifically to close that gap — do not substitute a lighter check again.

## Task

1. Start the full local stack, in this order (per `CLAUDE.md`'s "Full local stack" section):
   ```
   cd order-service && docker compose up -d        # Kafka + Zookeeper
   cd ../auth-server && ./mvnw spring-boot:run      # port 9000
   cd ../order-service && ./mvnw spring-boot:run    # port 8080
   cd ../inventory-service && ./mvnw spring-boot:run # port 8081
   cd ../order-ui && npm start                       # port 4200
   cd ../inventory-ui && npm start                   # port 4201
   ```
2. Open `order-ui` (`http://localhost:4200`) in an actual browser session:
   - Log in with a seeded account (see `auth-server`'s `DataSeeder` for seeded credentials, e.g. a `CUSTOMER` role account).
   - Confirm the dashboard loads and shows the order list/placement form, not a blank page or console error.
   - Place an order for a SKU with available stock (check `inventory-service`'s seed data for a SKU with `quantityOnHand > 0`).
   - Watch the order status transition from `PENDING` to `CONFIRMED` (or `REJECTED` if you deliberately pick a zero-stock SKU) without a page reload — this is the live WebSocket update the whole saga exists to demonstrate.
3. Open `inventory-ui` (`http://localhost:4201`):
   - Log in (e.g. a `WAREHOUSE_STAFF` seeded account).
   - Confirm the stock table loads with quantities.
   - Confirm the live reservation feed shows the event from the order you just placed in step 2, and that the stock quantity for that SKU visibly decremented.
4. Check the browser console in both apps for errors during this flow — a feature that "works" but throws console errors on a newly-upgraded Angular major is still a regression worth reporting.
5. Document exactly what you did and observed — which account you logged in as, which SKU you ordered, what status it ended up at, what the inventory UI showed, and any console errors. "It works" is not sufficient; describe the actual sequence and result, the same way you'd describe it to someone who can't see your screen.

## Out of scope
- Don't fix unrelated bugs you notice during the smoke test unless they block the flow entirely — note them as a finding instead, this task is about evidencing the flow, not a general bug hunt.

## Acceptance criteria
- A real login → order placement → live status update → inventory live feed flow is described step by step, with the specific account, SKU, and outcome used — not a generic "tested and it works."
- Any console errors encountered are reported, even if the flow still completed.
- If any step in the flow fails, that's a real, reportable finding — say so plainly rather than omitting the step.
