# Sprint 7 Track A Review

## Must fix

- `K-2` still fails in a real browser session. I started from `/`, not a proxied `/login` URL, and logged in with the seeded accounts from `auth-server/src/main/java/com/example/authserver/DataSeeder.java:33-49` (`customer1@example.test / customer123`, `warehouse1@example.test / warehouse123`). In both apps, auth succeeds but the UI never leaves `/login`; the button stays on `Signing in...` and the dashboard never renders, so the required order placement and live inventory verification from `docs/backlog/tasks/sprint-7/K-2-actual-browser-smoke-test.md:25-35` cannot start. The shared post-login path eagerly activates the WebSocket client in `order-ui/src/app/pages/dashboard/dashboard.component.ts:145-146`, `inventory-ui/src/app/pages/dashboard/dashboard.component.ts:107-108`, `order-ui/src/app/services/websocket.service.ts:14-25`, and `inventory-ui/src/app/services/websocket.service.ts:14-25`.
- The browser failure is reproducible and points at the shared SockJS boundary, not an environment-only glitch. During the real login flow, both apps throw `ReferenceError: global is not defined` from the emitted dashboard chunk (`http://localhost:4200/chunk-uv9N0ngM.js:8:17063`, `http://localhost:4201/chunk-BH4BMSTe.js:8:17087`). The bundled code at that site resolves to a `global.crypto` access, and the installed dependency still dereferences browser globals directly in `order-ui/node_modules/sockjs-client/lib/utils/browser-crypto.js:3-6` and `order-ui/node_modules/sockjs-client/lib/main.js:203-205` (same files under `inventory-ui/node_modules/...`). Until that dependency is replaced, aliased, or polyfilled in a way Angular 22/Vite accepts, Sprint 7 cannot claim the live WebSocket order-status flow works.

## Should fix

- The frontend dependency posture is still not clean. `npm audit --json` on both `order-ui` and `inventory-ui` reports the same `10` advisories: `4 high`, `3 moderate`, `3 low`. The highs still include `piscina` (`GHSA-x9g3-xrwr-cwfg`) and `http-proxy-middleware` (`GHSA-gcq2-9pq2-cxqm`) through `@angular-devkit/build-angular@22.0.3`. That does not explain the browser crash, but it means the earlier requirement to ensure dependencies have no known vulnerabilities is still unmet.
- Direct `/login` requests are still swallowed by the dev proxy in `order-ui/proxy.conf.json:12-16` and `inventory-ui/proxy.conf.json:12-16`, which forwards them to the auth server instead of the SPA route. Root `/` navigation still reaches the Angular app, so this is not the K-2 blocker, but it is a misleading dev-mode trap and it obscures smoke-test setup if someone pastes `/login` manually.

## Nice to have

- Once the SockJS/global issue is fixed, add a browser-level smoke script for the exact K-2 sequence so this stops regressing across sprints. This requirement has already slipped multiple rounds because route checks and build success were treated as substitutes for the real flow.

## Tests/checks missing

- No successful order placement could be completed, so the seeded in-stock SKU path from `inventory-service/src/main/java/com/example/inventoryservice/config/InventoryDataSeeder.java:21-23` (`SKU-001` quantity `50`, `SKU-002` quantity `5`) is still unverified in the UI.
- No `PENDING -> CONFIRMED` live status transition or inventory feed decrement was observed, because both dashboards crash before rendering.
- I did not clear runtime performance. The apps answer on `4200/4201`, but the actual interactive path fails before a stable dashboard state exists, so there is no basis to sign off performance beyond "startup responds".

## Verdict

Reject.
