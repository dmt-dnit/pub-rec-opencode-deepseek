# Task L-1: Drop sockjs-client; use native WebSocket for STOMP transport

**Resolves:** Must-fix blocker in `reviews/sprint-7-track-a-review.md` — `ReferenceError: global is not defined` crashing both Angular 22 UIs on dashboard load.

## Context

Both `order-ui` and `inventory-ui` connect to the Spring STOMP endpoint via `sockjs-client`. In Angular 22 (esbuild bundler), `sockjs-client` accesses `global.crypto` at bundle evaluation time. `global` is a Node.js global that esbuild does not polyfill in browser targets, so both apps throw:

```
ReferenceError: global is not defined
```

The crash fires from the lazy-loaded dashboard chunk immediately after login, preventing the dashboard from ever rendering. The Codex review confirmed this is reproducible and blocks K-2 entirely.

The fix is to drop the SockJS transport layer in both UIs **and** in both Spring backends, replacing it with plain native WebSocket. `@stomp/stompjs` supports native WebSocket natively via its `brokerURL` option — no `sockjs-client` needed.

## Changes required

### 1. Spring backends — remove SockJS from the STOMP endpoint (same change in both services)

**Files:**
- `order-service/src/main/java/com/example/orderservice/config/WebSocketConfig.java`
- `inventory-service/src/main/java/com/example/inventoryservice/config/WebSocketConfig.java`

In `registerStompEndpoints`, remove `.withSockJS()`:

```java
// Before
registry.addEndpoint("/ws")
        .setAllowedOriginPatterns("*")
        .withSockJS();

// After
registry.addEndpoint("/ws")
        .setAllowedOriginPatterns("*");
```

Without `.withSockJS()` the endpoint speaks plain WebSocket + STOMP, which the Angular client will use directly.

### 2. Angular UIs — drop SockJS import, use brokerURL (same change in both UIs)

**Files:**
- `order-ui/src/app/services/websocket.service.ts`
- `inventory-ui/src/app/services/websocket.service.ts`

Replace the `webSocketFactory: () => new SockJS('/ws')` approach with `brokerURL` pointing to the native WebSocket path. The dev proxy already maps `/ws/**` → backend with `ws: true`.

For `order-ui/src/app/services/websocket.service.ts`:
```typescript
// Remove: import SockJS from 'sockjs-client';

constructor() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  this.client = new Client({
    brokerURL: `${protocol}//${window.location.host}/ws`,
    reconnectDelay: 5000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
    onConnect: () => {
      this.client.subscribe('/topic/messages', (msg: Message) => {
        const order: Order = JSON.parse(msg.body);
        this.messageSubject.next(order);
      });
    }
  });
}
```

Apply the identical change to `inventory-ui/src/app/services/websocket.service.ts` (only the type in `JSON.parse` differs — it stays `InventoryReservation`).

### 3. Remove sockjs-client from package.json (both UIs)

**Files:**
- `order-ui/package.json`
- `inventory-ui/package.json`

Remove from `dependencies`:
```
"sockjs-client": "^1.6.1",
```

Remove from `devDependencies`:
```
"@types/sockjs-client": "^1.5.4",
```

Then run `npm install` in each UI directory to regenerate the lockfile.

## Verification

1. `npm run build` must pass in both `order-ui` and `inventory-ui` with no TypeScript errors. (A CommonJS warning for `@stomp/stompjs` is informational — acceptable.)
2. `npm ls sockjs-client` in each UI must show `(empty)` — the package must not appear anywhere in the resolved dependency tree.
3. The built chunk files for the dashboard must not contain any reference to `global.crypto` or the string `sockjs`. (Use `grep -r 'sockjs\|global\.crypto' order-ui/dist/` to verify.)

## Out of scope
- Do not attempt a live browser smoke test — that is L-3.
- Do not change anything in `auth-server` or `shared-model`.
- Do not change the proxy configs — that is L-2.
