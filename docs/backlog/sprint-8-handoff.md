# Sprint 8 → Codex: verify L-1/L-2, execute L-3 (browser smoke test)

Sprint 8 has two closed tasks (L-1, L-2) and one open task (L-3, the browser smoke test). L-3 is the only thing standing between Track A and closed.

## What was done

### L-1 — Drop sockjs-client, use native WebSocket (commit `c3af72d`)

**Blocker fixed:** `ReferenceError: global is not defined` from `sockjs-client/lib/utils/browser-crypto.js` in Angular 22/esbuild. Crashes the dashboard chunk immediately after login — the root cause of every Sprint 7 K-2 failure.

**Changes (all on `main`, merged via `d877a49`):**

| File | Change |
|---|---|
| `order-service/src/main/java/.../config/WebSocketConfig.java` | Removed `.withSockJS()` — endpoint now speaks plain STOMP/WebSocket |
| `inventory-service/src/main/java/.../config/WebSocketConfig.java` | Same |
| `order-ui/src/app/services/websocket.service.ts` | Removed `import SockJS`; replaced `webSocketFactory: () => new SockJS('/ws')` with `brokerURL: \`\${protocol}//\${window.location.host}/ws\`` |
| `inventory-ui/src/app/services/websocket.service.ts` | Same |
| `order-ui/package.json` | Removed `sockjs-client` (dep) and `@types/sockjs-client` (devDep) |
| `inventory-ui/package.json` | Same |
| `order-ui/package-lock.json` | Regenerated — sockjs-client subtree removed |
| `inventory-ui/package-lock.json` | Same |

**Verification evidence (run against `main` HEAD `d877a49`):**

```
# sockjs absent from dependency tree
$ npm ls sockjs-client --prefix order-ui
order-ui@0.0.0 → └── (empty)

$ npm ls sockjs-client --prefix inventory-ui
inventory-ui@0.0.0 → └── (empty)

# sockjs/global.crypto absent from built output
$ grep -r 'sockjs\|global\.crypto' order-ui/dist/    → 0 matches
$ grep -r 'sockjs\|global\.crypto' inventory-ui/dist/ → 0 matches

# ng build: clean in both (one CommonJS warning for @stomp/stompjs — informational, not a build error)
order-ui:    Application bundle generation complete.
inventory-ui: Application bundle generation complete.
```

### L-2 — Narrow /login proxy rule (commit `bbc823b`)

**Changes:**

| File | Change |
|---|---|
| `order-ui/proxy.conf.json` | `"/login/**"` → `"/login/oauth2/**"` |
| `inventory-ui/proxy.conf.json` | Same |

Navigating to `http://localhost:4200/login` now serves the Angular SPA route instead of being proxied to auth-server's Spring Security login form. The Google OAuth2 callback path (`/login/oauth2/code/google`) still reaches auth-server.

**Verification:**
```
$ grep '"/login/\*\*"' order-ui/proxy.conf.json inventory-ui/proxy.conf.json
(no output — broad rule gone)

$ grep '"/login/oauth2/\*\*"' order-ui/proxy.conf.json inventory-ui/proxy.conf.json
order-ui/proxy.conf.json:  "/login/oauth2/**": {
inventory-ui/proxy.conf.json:  "/login/oauth2/**": {

$ node -e "JSON.parse(require('fs').readFileSync('order-ui/proxy.conf.json','utf8'))"
(exits 0 — valid JSON)
$ node -e "JSON.parse(require('fs').readFileSync('inventory-ui/proxy.conf.json','utf8'))"
(exits 0 — valid JSON)
```

---

## What's open: L-3 — Browser smoke test

This is the unmet must-fix from Sprint 7 (and every sprint before it). L-1 removes the crash; L-3 is the actual test. Brief: `docs/backlog/tasks/sprint-8/L-3-browser-smoke-test.md`.

### Stack startup

```bash
# Backend — containerized (all 5 containers: zookeeper, kafka, auth-server, order-service, inventory-service):
bash scripts/startup-all.sh

# Frontends (host):
cd order-ui    && npm start   # http://localhost:4200
cd inventory-ui && npm start  # http://localhost:4201
```

### Required flow

1. `http://localhost:4200` — log in as a CUSTOMER (`customer1@example.test / customer123` per `auth-server/DataSeeder.java`), confirm dashboard renders with no console errors, place an order for `SKU-001` (50 in stock per `inventory-service/InventoryDataSeeder.java`), watch status go `PENDING → CONFIRMED` live without a page reload.
2. `http://localhost:4201` — log in as WAREHOUSE_STAFF (`warehouse1@example.test / warehouse123`), confirm stock table loads, confirm live reservation feed shows the event from step 1 and `SKU-001` quantity decremented.
3. Check browser console in both apps for errors (including non-fatal ones).

### Acceptance

- Real flow described step by step: specific account, SKU, outcome — not "tested and works."
- Status transition live (no reload) via WebSocket.
- Zero `ReferenceError: global is not defined` in either console.
- Any console errors reported even if flow completed.
- Failed steps reported plainly rather than omitted.

---

## npm audit posture (unchanged from Sprint 7)

Both UIs still report 10 vulnerabilities (4 high, 3 moderate, 3 low). All 4 highs are `piscina` (`GHSA-x9g3-xrwr-cwfg`) transitive under `@angular-devkit/build-angular@22.0.3`. No clean upstream fix exists at the current Angular 22 floor — `npm audit fix --force` would regress the builder. This was accepted by Codex in Sprint 7's review ("does not explain the browser crash") and remains unchanged. The `sockjs-client` removal did not add or remove advisory entries.
