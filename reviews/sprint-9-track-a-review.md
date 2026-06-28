# Sprint 9 Track A Review

Review date: 2026-06-27

Verdict: FAIL / do not close Track A yet.

Sprint 9 added the right kind of regression gate, but the gate fails against the live app. Both Angular builds pass and production-only npm audits are clean. The actual browser smoke test still fails at the first dashboard data assertion: inventory rows are returned by the API but not rendered. A focused order-ui diagnostic shows the same stale-DOM pattern for orders.

## Scorecard

| Task | Result | Evidence |
|---|---|---|
| M-1 Fix Angular change detection | FAIL | `provideZoneChangeDetection()` is present in both app configs and STOMP callbacks now use `NgZone.run()`, but the dashboards still do not render HTTP-loaded state. Angular 22 local typings say `OnPush is enabled by default`; the dashboards have subscription-backed mutable fields and no `Eager`, signal, async-pipe, or `markForCheck()` path. |
| M-2 npm audit clean pass | PARTIAL | `npm audit --omit=dev` is clean in both UIs. Full `npm audit --json` still reports 10 dev-tooling advisories per UI. Also, the committed locks are not fully current within Angular 22: runtime/compiler packages remain pinned at 22.0.2 while `npm outdated` wants 22.0.4. |
| M-3 Playwright smoke test | FAIL | `npm install`, `tsc --noEmit`, and `playwright test --list` pass, but full `playwright test` fails at `e2e/smoke.spec.ts:51` because the inventory table never renders `SKU-001`. |

## Must Fix

- `inventory-ui` still does not render the initial stock table after login. The committed Playwright test fails at [e2e/smoke.spec.ts](C:/projects/pub-rec-opencode-deepseek/e2e/smoke.spec.ts:51), waiting for `td[mat-cell]` containing `SKU-001`. The failure accessibility snapshot shows the user is logged in as `warehouse1@example.test`, the `Stock Dashboard` table header is visible, but the table body rowgroup is empty. A focused diagnostic confirmed `/api/inventory` returned `200` with `SKU-001`, `SKU-002`, and `SKU-003`, while the DOM had `renderedRows: []` and `cells: []`.

- `order-ui` has the same stale-rendering problem. A focused diagnostic after customer login saw `/api/orders` return `200` with existing confirmed order `da66fbb4-e0b9-4dc7-a457-ecf9044ef9de`, but the dashboard rendered `No orders yet` and `.order-header` count was zero. The affected assignments are in [dashboard.component.ts](C:/projects/pub-rec-opencode-deepseek/order-ui/src/app/pages/dashboard/dashboard.component.ts:139) for initial orders, [dashboard.component.ts](C:/projects/pub-rec-opencode-deepseek/order-ui/src/app/pages/dashboard/dashboard.component.ts:145) for WebSocket messages, and [dashboard.component.ts](C:/projects/pub-rec-opencode-deepseek/order-ui/src/app/pages/dashboard/dashboard.component.ts:180) for order placement.

- The M-1 implementation fixed zone scheduling but not dirty marking for dashboard components. Angular's installed typings state `OnPush is enabled by default`, while `Eager = 1` and `Default = 1` in `order-ui/node_modules/@angular/core/types/_debug_node-chunk.d.ts:4807`, `:4815`, and `:4820`. `order-ui` removed dashboard `ChangeDetectionStrategy.Eager`, and `inventory-ui` never had a dashboard strategy. The subscription assignments at [inventory dashboard](C:/projects/pub-rec-opencode-deepseek/inventory-ui/src/app/pages/dashboard/dashboard.component.ts:106), [inventory dashboard](C:/projects/pub-rec-opencode-deepseek/inventory-ui/src/app/pages/dashboard/dashboard.component.ts:108), and [inventory dashboard](C:/projects/pub-rec-opencode-deepseek/inventory-ui/src/app/pages/dashboard/dashboard.component.ts:117) are not enough under default OnPush unless the component is marked dirty or state is signal/async-pipe driven.

Recommended fix path: either explicitly set both dashboard components to `ChangeDetectionStrategy.Eager`, or convert dashboard state to signals, or inject `ChangeDetectorRef` and call `markForCheck()` after HTTP/STOMP state updates. Signals are the cleaner Angular 22 direction; `Eager` is the smallest demo-preserving fix. Whichever route is chosen, rerun the committed Playwright test and require it to pass.

## Should Fix

- Dependency posture is cleaner for runtime but not fully current. `npm audit --omit=dev` returns `found 0 vulnerabilities` in both UIs, but full audit still reports `10` total findings per UI (`4 high`, `3 moderate`, `3 low`) through dev tooling. This is documented, but it is not "no vulnerabilities" in the full dependency tree.

- The committed UI lockfiles are behind the installed local packages. In both UI lockfiles, `node_modules/@angular/core` and `node_modules/@angular/compiler-cli` are still `22.0.2`, and `node_modules/@angular-devkit/build-angular` is still `22.0.3`; examples are in [order-ui/package-lock.json](C:/projects/pub-rec-opencode-deepseek/order-ui/package-lock.json:275), [order-ui/package-lock.json](C:/projects/pub-rec-opencode-deepseek/order-ui/package-lock.json:755), and [order-ui/package-lock.json](C:/projects/pub-rec-opencode-deepseek/order-ui/package-lock.json:788). Local `npm ls` showed `node_modules` had newer `22.0.3/22.0.4` packages, so the build I ran is not a clean `npm ci` proof from committed locks. Align the locks intentionally and validate from a clean install.

- The new Playwright smoke test does not capture browser console errors, page errors, or failed requests. Prior Track A acceptance explicitly required reporting browser console errors. Add listeners for `console`, `pageerror`, and `requestfailed`, then fail or attach diagnostics on unexpected entries.

## Verification Run

Commands run:

```powershell
npm.cmd run build                 # order-ui, exit 0
npm.cmd run build                 # inventory-ui, exit 0
npm.cmd audit --omit=dev          # order-ui, found 0 vulnerabilities
npm.cmd audit --omit=dev          # inventory-ui, found 0 vulnerabilities
npm.cmd audit --json              # order-ui, 10 dev-tooling findings
npm.cmd audit --json              # inventory-ui, 10 dev-tooling findings
npm.cmd install                   # e2e, exit 0, found 0 vulnerabilities
.\node_modules\.bin\tsc.cmd --noEmit
.\node_modules\.bin\playwright.cmd test --list
.\node_modules\.bin\playwright.cmd install chromium
.\node_modules\.bin\playwright.cmd test
```

Key output:

```text
Listing tests:
  [chromium] > smoke.spec.ts:29:5 > smoke: order placement saga updates both dashboards
Total: 1 test in 1 file

Running 1 test using 1 worker

1) [chromium] > smoke.spec.ts:29:5 > smoke: order placement saga updates both dashboards

Error: expect(locator).toBeVisible() failed
Locator: locator('td[mat-cell]').filter({ hasText: 'SKU-001' })
Expected: visible
Timeout: 10000ms
```

Environment notes:

- Backend for the failing e2e run: existing local host Spring services on `9000`, `8080`, and `8081`, with Kafka/Zookeeper reachable on `127.0.0.1:9092/2181`.
- Frontends were restarted before the e2e run, so ports `4200` and `4201` served current Sprint 9 code.
- `npm run build` for both UIs produced no warnings after the `allowedCommonJsDependencies` change.
- No generated Playwright `test-results/` directory was left in the repo after review cleanup.

