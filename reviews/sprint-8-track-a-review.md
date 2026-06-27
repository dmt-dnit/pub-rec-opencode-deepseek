# Sprint 8 Track A Review

Review date: 2026-06-26

Verdict: FAIL / do not close Track A yet.

Sprint 8 fixed the two setup blockers I was asked to verify: native WebSocket transport replaced `sockjs-client`, and the broad `/login/**` dev proxy rule is gone. The real browser smoke test still fails. The backend saga completes and both STOMP streams deliver messages, but the Angular dashboards do not render the received HTTP/WebSocket state.

## Scorecard

| Task | Result | Evidence |
|---|---|---|
| L-1 Drop `sockjs-client` | PASS | `order-ui/src/app/services/websocket.service.ts:13` and `inventory-ui/src/app/services/websocket.service.ts:13` use native `ws/wss` `brokerURL`; `order-service/src/main/java/com/example/orderservice/config/WebSocketConfig.java:21` and `inventory-service/src/main/java/com/example/inventoryservice/config/WebSocketConfig.java:21` expose plain `/ws` endpoints; repo search found no `sockjs-client`, `SockJS`, or `global.crypto` references in source/package manifests. |
| L-2 Narrow `/login` proxy | PASS | `order-ui/proxy.conf.json:12` and `inventory-ui/proxy.conf.json:12` now use `"/login/oauth2/**"`; search found no remaining `"/login/**"` rule. |
| L-3 Browser smoke test | FAIL | Real login/order/inventory flow was executed. APIs and STOMP completed successfully, but both dashboards stayed stale and did not display the accepted smoke-test outcome. Details below. |

## Findings

### P1: L-3 still fails because Angular views do not render received HTTP/WebSocket state

The full browser flow was executed with the seeded accounts from the task brief:

- `order-ui`: logged in as `customer1@example.test / customer123`.
- `inventory-ui`: logged in as `warehouse1@example.test / warehouse123`.
- Ordered `SKU-001` quantity `1`.

Observed backend/API state after the order:

- `POST /api/orders` returned `200` and the order snackbar appeared: `Order da66fbb4 placed - total: $9.99`.
- Authenticated `GET /api/orders` from the same browser session returned order `da66fbb4-e0b9-4dc7-a457-ecf9044ef9de` with status `CONFIRMED`.
- Authenticated `GET /api/inventory` from the same browser session returned `SKU-001` quantity `49`, down from seeded quantity `50`.
- `order-ui` received a STOMP `/topic/messages` message for the order.
- `inventory-ui` received a STOMP `/topic/messages` reservation message with status `RESERVED`.

Observed rendered UI state at the same time:

- `order-ui` stayed on `Placing...`, showed `Orders Disconnected`, and still rendered `No orders yet. Place an order to see it here.`
- `inventory-ui` showed the stock table header only, `Reservation Feed Disconnected`, and `No reservation events yet.`
- `inventory-ui` did not show the initial stock rows even though authenticated `GET /api/inventory` returned `SKU-001`, `SKU-002`, and `SKU-003`.

Code paths that should drive those rendered states:

- `order-ui/src/app/pages/dashboard/dashboard.component.ts:64` renders the `placing` button text, `:77` renders `orders`, and `:95` renders the empty-orders message.
- `order-ui/src/app/pages/dashboard/dashboard.component.ts:140` assigns `orders` from the initial HTTP list, `:146` subscribes to WebSocket messages, `:147` sets `connected = true`, and `:188` prepends the placed order after the successful POST.
- `inventory-ui/src/app/pages/dashboard/dashboard.component.ts:35` binds the table to `products`, `:61` renders WebSocket connection state, and `:80` renders the empty reservation feed.
- `inventory-ui/src/app/pages/dashboard/dashboard.component.ts:104` calls `loadProducts()`, `:107` connects WebSocket, `:109` mutates `reservations`, `:110` sets `connected = true`, and `:117` assigns `products` from the HTTP response.

This is not a backend or Kafka failure based on the API/STOMP evidence. The likely fix area is Angular state/change-detection propagation around the dashboard subscriptions and Material table/list rendering, especially after the Angular 22 migration. Do not close L-3 until the real browser flow shows the order status transition live and the inventory table/feed update without a page reload.

### P2: Dependency audit remains non-clean in both Angular apps

`npm audit --json` was run in both `order-ui` and `inventory-ui`.

- `order-ui`: 10 vulnerabilities total, `4 high`, `3 moderate`, `3 low`, `0 critical`.
- `inventory-ui`: 10 vulnerabilities total, `4 high`, `3 moderate`, `3 low`, `0 critical`.
- The high-severity path is still through Angular dev/build tooling: `@angular-devkit/build-angular` via `@angular/build`, `piscina`, and `http-proxy-middleware`.
- `npm audit` reports a fix path to `@angular-devkit/build-angular@21.2.17`, which is not a clean forward patch for the current Angular 22 stack.

This does not explain the L-3 runtime failure, but the dependency posture is still not clean and should remain tracked explicitly.

### P3: No automated browser regression guard exists for the failure that keeps recurring

Sprint 8 still depends on manual browser verification for the core acceptance path. The current failure would be caught by an e2e smoke test that asserts:

- inventory dashboard renders seeded `SKU-001` stock after login;
- order placement changes the button back from `Placing...`;
- order list displays the created order and reaches `CONFIRMED` without reload;
- inventory feed displays the reservation event and `SKU-001` decrements.

Without this, future sprints can keep passing build/API checks while missing the actual browser contract again.

## Console And Environment Notes

- Browser runtime: Playwright against local Microsoft Edge.
- Backend runtime used for final evidence: Kafka/Zookeeper in Podman, Spring services on host with JDK 21, Angular dev servers on host.
- I first tried the fully containerized backend stack, but local Podman stopped mid-run; I reran the smoke against the host Spring services to avoid treating environment instability as an application failure.
- No `ReferenceError: global is not defined` occurred in either UI during the browser smoke test.
- Non-feature browser noise observed: blocked Google font requests (`net::ERR_NETWORK_ACCESS_DENIED`) and a favicon `404`.
- Angular dev-server logs still show the existing `@stomp/stompjs` CommonJS optimization warning.

## Verification Commands Run

```powershell
git -c safe.directory=C:/projects/pub-rec-opencode-deepseek -C C:\projects\pub-rec-opencode-deepseek status --short
rg -n "sockjs-client|@types/sockjs-client|SockJS|global\.crypto|/login/\*\*|/login/oauth2" C:\projects\pub-rec-opencode-deepseek\order-ui C:\projects\pub-rec-opencode-deepseek\inventory-ui C:\projects\pub-rec-opencode-deepseek\order-service C:\projects\pub-rec-opencode-deepseek\inventory-service
npm.cmd audit --json   # in order-ui
npm.cmd audit --json   # in inventory-ui
npm.cmd outdated --json # in order-ui
npm.cmd outdated --json # in inventory-ui
```

Browser smoke screenshots captured during the failing state:

- `C:\Users\dimit\AppData\Local\Temp\pubrec-sprint8-inventory-no-row.png`
- `C:\Users\dimit\AppData\Local\Temp\pubrec-sprint8-order-continuation.png`
- `C:\Users\dimit\AppData\Local\Temp\pubrec-sprint8-inventory-continuation.png`

