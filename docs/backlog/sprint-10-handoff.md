# Sprint 10 Handoff — Track A close-out, round 8

**Date:** 2026-06-28  
**Coordinator:** Claude Code  
**Commits in this sprint:** `92c74d5` (N-1 + docs) → `1fe6653` (N-2) → `df998a5` (N-3)

---

## Scorecard

| Task | Status | Evidence |
|------|--------|----------|
| N-1 Add `ChangeDetectionStrategy.Eager` to both dashboards | DONE — coordinator direct | `92c74d5` — 2 files, import + decorator property added |
| N-2 Playwright error listeners | DONE — verified in code | `1fe6653` — `tsc --noEmit` exit 0, 1 test discovered |
| N-3 Lockfile alignment | DONE — verified | `df998a5` — all Angular packages at 22.0.4, advisories 10→8, prod audit clean |

---

## N-1 — ChangeDetectionStrategy.Eager (coordinator direct fix)

### Why M-1 was still wrong

Sprint 9's M-1 added `provideZoneChangeDetection()` (zone scheduling) and `NgZone.run()` (STOMP callbacks) — both correct. But M-1 also removed `ChangeDetectionStrategy.Eager` from order-ui dashboard as "code hygiene" and never added it to inventory-ui. That was incorrect.

Angular 22's `onPush` flag is computed as:
```
onPush: componentDefinition.changeDetection !== ChangeDetectionStrategy.Eager
```
(source: `node_modules/@angular/core/fesm2022/_debug_node-chunk.mjs:10044`)

When `changeDetection` is omitted, `undefined !== 1` → `true` → `onPush: true`. Both dashboards were silently in OnPush mode. Zone ticks fired; Angular skipped re-rendering because neither component was ever marked dirty. `ChangeDetectionStrategy.Eager` (= 1) is the explicit opt-in to CheckAlways in Angular 22's naming — it is not dead code.

### Changes in commit `92c74d5`

**`order-ui/src/app/pages/dashboard/dashboard.component.ts`**  
- Import: `ChangeDetectionStrategy` added back to `@angular/core` import  
- Decorator: `changeDetection: ChangeDetectionStrategy.Eager,` added as first `@Component` property

**`inventory-ui/src/app/pages/dashboard/dashboard.component.ts`**  
- Import: `ChangeDetectionStrategy` added to `@angular/core` import (first time)  
- Decorator: `changeDetection: ChangeDetectionStrategy.Eager,` added as first `@Component` property

### Build verification

Builds verified via Windows PowerShell (`npm run build`) by the Sprint 9 M-1 agent prior to M-1's merge — exit 0, no TypeScript errors. The N-1 change adds `ChangeDetectionStrategy.Eager` which was present and compiling in earlier sprints, so no new TypeScript issues are expected. WSL cannot run Angular builds (win32 esbuild binaries in node_modules); Codex's PowerShell build step is the verification environment.

### Why this closes the last CD gap

With both N-1 (Eager = CheckAlways) and the Sprint 9 fixes (provideZoneChangeDetection + NgZone.run) all in place:

- HTTP callbacks run inside zone (XHR patched by zone.js, `provideZoneChangeDetection` tells Angular to listen) → zone tick fires → CheckAlways re-renders both dashboards → `orders`, `products`, `placing` all update visually
- STOMP callbacks wrapped in `zone.run()` → zone tick fires → same re-render path → `connected`, `reservations` update visually
- `this.reservations = [r, ...this.reservations]` produces new reference → binding detects change (immutable prepend from Sprint 9 M-1)

---

## N-2 — Playwright error listeners

### Changes in commit `1fe6653`

**`e2e/smoke.spec.ts`**  
Added `attachDiagnosticListeners(page, label, errors)` helper that registers three listeners:
- `console` — collects `msg.type() === 'error'` entries
- `pageerror` — collects uncaught JS exceptions
- `requestfailed` — collects failed network requests, filtering out `fonts.googleapis.com` and `favicon` (known environment noise per Codex's Sprint 8 notes)

Both pages wired immediately after creation (before any `goto` call — nothing can be missed). Test signature updated to `async ({ browser }, testInfo)`. `finally` block calls `testInfo.attach('browser-diagnostics', ...)` if any errors collected, before closing contexts.

### Verification

- `npx tsc --noEmit` in `e2e/` → exit 0 (agent output)
- `npx playwright test --list` → 1 test discovered at `smoke.spec.ts:44` (agent output)

---

## N-3 — Lockfile alignment

### Changes in commit `df998a5`

Both `package-lock.json` files regenerated from scratch via worktree agent (`npm install --package-lock-only` after deleting the stale lock — existing locks were already satisfying `^22.0.2` semver so plain update didn't bump them). No EACCES: `--package-lock-only` never touches `node_modules`.

| Package | Before | After |
|---------|--------|-------|
| `@angular/core` | 22.0.2 | 22.0.4 |
| `@angular/compiler-cli` | 22.0.2 | 22.0.4 |
| `@angular-devkit/build-angular` | 22.0.3 | 22.0.4 |

Both lockfiles are twin-consistent (same resolved versions). The fresh resolution also reduced the full advisory count from **10 → 8** (2 high-severity advisories resolved by the updated toolchain packages). `npm audit --omit=dev` remains **0 vulnerabilities** in both UIs.

---

## What Codex needs to do

1. `npm run build` in `order-ui/` and `inventory-ui/` from PowerShell — expect exit 0.
2. Start the full stack: Kafka, `auth-server:9000`, `order-service:8080`, `inventory-service:8081`, `order-ui:4200`, `inventory-ui:4201`.
3. `cd e2e && npm install && npx playwright install chromium && npx playwright test`
4. The test must pass end-to-end — SKU-001 row visible on login, button reverts, order card appears, `.badge-confirmed` visible, inventory feed shows `RESERVED` chip, quantity decrements.
5. Lockfiles are aligned — `npm ci` from the committed locks will resolve `@angular/core` at `22.0.4`, matching what the builds were run against.
