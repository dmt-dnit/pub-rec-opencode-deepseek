# Task F-1: Fix Angular dev-proxy routing for both UIs

**Resolves:** Blocker 1 in `reviews/sprint-1-track-a-review.md`.

## Context
Sprint 1 rewrote both Angular apps to call the new domain APIs (`order-ui` calls `POST/GET /api/orders`, `inventory-ui` calls `GET /api/inventory`), but nobody updated the dev-server proxy config that routes those calls to the backend. Both `proxy.conf.json` files still only forward the old `/api/articles/**` path. Under `ng serve`, any request to `/api/orders` or `/api/inventory` falls through to no matching rule and never reaches the backend.

## Current state
- `order-ui/proxy.conf.json` — has a rule for `/api/articles/**` → `http://localhost:8080`. No rule for `/api/orders/**`.
- `inventory-ui/proxy.conf.json` — has a rule for `/api/articles/**` → `http://localhost:8081`. No rule for `/api/inventory/**`.
- The `/api/auth/**`, `/oauth2/**`, `/login/**`, and `/ws/**` rules in both files are correct and unrelated to this bug — don't touch them.

## Task
- `order-ui/proxy.conf.json`: replace the `/api/articles/**` entry with `/api/orders/**`, same target (`http://localhost:8080`), same `secure`/`logLevel` settings.
- `inventory-ui/proxy.conf.json`: replace the `/api/articles/**` entry with `/api/inventory/**`, same target (`http://localhost:8081`), same settings.

## Acceptance criteria
- `grep -r "api/articles" order-ui inventory-ui` returns nothing.
- With `order-service` running on 8080 and `order-ui` served via `npm start`: submitting the order form in the browser results in a real `200`/`201` response from `order-service` (check the browser network tab, not just that the UI doesn't show an error) — not a 404 from the dev server.
- Same check for `inventory-ui` against `inventory-service` on 8081: the stock table populates with real data from `GET /api/inventory`, visible in the network tab as a successful request to port 8081, not a proxy miss.
