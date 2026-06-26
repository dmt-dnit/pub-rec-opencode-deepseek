# Sprint 8 — Track A close-out, round 6

## Why this sprint exists

Sprint 7 was rejected by Codex (`reviews/sprint-7-track-a-review.md`). The single must-fix blocker was a runtime crash in both Angular 22 UIs immediately after login:

```
ReferenceError: global is not defined
  at http://localhost:4200/chunk-uv9N0ngM.js:8:17063
```

The crash fires from `sockjs-client` accessing `global.crypto` at bundle evaluation time. Angular 22's esbuild bundler does not polyfill `global` in browser targets. Both dashboards crash before rendering, so the required end-to-end smoke test (login → order placement → live status update → inventory live feed) is still unverified.

The sprint also carries a should-fix (the `/login/**` proxy rule swallowing SPA routes) from the same review.

## Tasks

| ID  | Title                              | Status |
|-----|------------------------------------|--------|
| L-1 | Drop sockjs-client; use native WebSocket | open |
| L-2 | Fix /login proxy rule              | open   |
| L-3 | Browser smoke test                 | open   |

**Recommended implementation order:** L-1 → L-2 → L-3. L-3 depends on L-1 being in place (the crash is the blocker); L-2 is independent but should be done before the smoke test to avoid dev-mode confusion.

## What changes

- **L-1** (`docs/backlog/tasks/sprint-8/L-1-drop-sockjs-use-native-websocket.md`): Remove `.withSockJS()` from both Spring `WebSocketConfig.java` files; rewrite both Angular `websocket.service.ts` files to use `brokerURL` (native WebSocket); remove `sockjs-client` + `@types/sockjs-client` from both `package.json` files; regenerate lockfiles.
- **L-2** (`docs/backlog/tasks/sprint-8/L-2-fix-login-proxy-rule.md`): Narrow `/login/**` → `/login/oauth2/**` in both `proxy.conf.json` files.
- **L-3** (`docs/backlog/tasks/sprint-8/L-3-browser-smoke-test.md`): Start the full local stack and execute the real browser smoke test: login → order → live PENDING→CONFIRMED transition → inventory feed decrement. Document the exact account, SKU, outcome, and any console errors.

## Track B gate

Track B hardening (`docs/backlog/sprint-1.md`) remains gated. It does not start until Sprint 8 L-3 is verified and this sprint is accepted by Codex.
