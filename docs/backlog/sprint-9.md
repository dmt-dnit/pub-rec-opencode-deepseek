# Sprint 9 — Track A final close-out, round 7

## Why this sprint exists

Codex rejected Sprint 8 (see `reviews/sprint-8-track-a-review.md`). L-1 and L-2 passed; L-3 (browser smoke test) still fails. The root cause is now conclusively identified by reading the Angular 22 source in `node_modules`:

1. **Both UIs are missing `provideZoneChangeDetection()`** in `app.config.ts`. Angular 22 no longer auto-hooks zone.js at bootstrap; without this explicit provider, the CD scheduler does not listen to zone tasks. HTTP callbacks set state (`placing = false`, `orders = [...]`, `products = [...]`) but Angular never runs a CD cycle, so the template stays stale. The snackbar appearing despite the stale view is consistent — `MatSnackBar` creates an overlay component with its own CD context.

2. **STOMP callbacks from `@stomp/stompjs` run outside zone.js entirely**. Even after adding `provideZoneChangeDetection()`, messages arriving on the WebSocket won't trigger CD unless wrapped in `NgZone.run()`.

3. **`reservations.unshift(reservation)` in inventory-ui mutates the array in place**, so Angular's reference-equality check on the array binding never fires.

Codex's P2 finding (npm audit) also remains open: 10 vulnerabilities in each UI including 4 high. The suggested fix is a downgrade — must either document as build-tooling non-actionable or find a forward-compatible fix.

Codex's P3 (Playwright smoke test) is not a hard FAIL criterion yet, but has been flagged across multiple reviews as a missing regression guard. Include it this sprint.

## Task list

| ID  | Title                                              | Priority | Depends on |
|-----|----------------------------------------------------|----------|------------|
| M-1 | Fix Angular change detection (CD + NgZone + array) | Critical | —          |
| M-2 | npm audit clean pass                               | High     | —          |
| M-3 | Playwright e2e smoke test                          | High     | M-1        |

## Recommended order

Run M-1 and M-2 in parallel. M-3 can be written concurrently but must be verified (actually runs and asserts) only after M-1 is merged — write the test now so Codex can read it, but mark it as "depends on M-1 being applied first" in the handoff.

## Acceptance gate

Sprint 9 closes when Codex independently confirms:
- L-3: login → place order → order appears in list with status → inventory table decrements → reservation feed shows RESERVED event — all visible without a page reload
- `npm audit --omit=dev` in both UIs exits 0 (no production-dependency vulnerabilities), with build-tooling non-actionable items documented in the handoff
- Playwright e2e test exists, runs, and asserts the smoke-test path
