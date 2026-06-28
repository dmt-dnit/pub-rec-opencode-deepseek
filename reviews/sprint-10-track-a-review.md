# Sprint 10 Track A Review

Review date: 2026-06-28

Verdict: FAIL / do not close Track A yet.

Sprint 10 fixed part of the Angular rendering problem: order-ui now renders existing orders after login. The Track A browser gate still fails because inventory-ui does not render seeded stock rows even though `/api/inventory` returns them. There is also a lockfile regression: both committed UI lockfiles still contain `sockjs-client` and `@types/sockjs-client` entries even though the packages are absent from `package.json`.

## Scorecard

| Task | Result | Evidence |
|---|---|---|
| N-1 Add `ChangeDetectionStrategy.Eager` to both dashboards | PARTIAL | `order-ui` and `inventory-ui` dashboards both declare `ChangeDetectionStrategy.Eager`, and order-ui now renders the existing confirmed order. Inventory still renders an empty Material table after a successful inventory API response, so the browser acceptance path still fails. |
| N-2 Playwright error listeners | PASS | `e2e/smoke.spec.ts` attaches `console`, `pageerror`, and `requestfailed` listeners to both pages before navigation. `tsc --noEmit` and `playwright test --list` pass. No browser diagnostic artifact was attached in the failing run because no matching errors were collected. |
| N-3 Lockfile alignment | FAIL | Angular package pins are aligned to `22.0.4`, but both committed lockfiles still contain stale `sockjs-client` and `@types/sockjs-client` root/package entries. A local `npm install` removed those stale entries, proving the committed locks are not cleanly regenerated from the current package manifests. |

## Must Fix

- The committed smoke test still fails at [e2e/smoke.spec.ts](C:/projects/pub-rec-opencode-deepseek/e2e/smoke.spec.ts:70), waiting for the inventory `SKU-001` table cell. Playwright output:

```text
Error: expect(locator).toBeVisible() failed
Locator: locator('td[mat-cell]').filter({ hasText: 'SKU-001' })
Expected: visible
Timeout: 10000ms
```

The failure snapshot shows `Inventory UI warehouse1@example.test`, the `Stock Dashboard` table header, and an empty table body. A focused diagnostic confirmed `GET /api/inventory` returned `200` with `SKU-001`, `SKU-002`, and `SKU-003`, while the DOM still had `rows: []` and `cells: []`. The affected code is the inventory dashboard load path at [dashboard.component.ts](C:/projects/pub-rec-opencode-deepseek/inventory-ui/src/app/pages/dashboard/dashboard.component.ts:105), [dashboard.component.ts](C:/projects/pub-rec-opencode-deepseek/inventory-ui/src/app/pages/dashboard/dashboard.component.ts:116), and [dashboard.component.ts](C:/projects/pub-rec-opencode-deepseek/inventory-ui/src/app/pages/dashboard/dashboard.component.ts:118).

- N-1 is not sufficient for inventory-ui. The decorator is now `changeDetection: ChangeDetectionStrategy.Eager` at [dashboard.component.ts](C:/projects/pub-rec-opencode-deepseek/inventory-ui/src/app/pages/dashboard/dashboard.component.ts:20), but the Material table still does not paint the assigned data. Order-ui did improve: a diagnostic after login showed the existing `da66fbb4...` order rendered with `CONFIRMED`, so the remaining blocker appears inventory-table specific. Do not close this until the committed `playwright test` passes end-to-end.

- Both committed UI lockfiles still include removed SockJS dependencies. In `HEAD`, [order-ui/package-lock.json](C:/projects/pub-rec-opencode-deepseek/order-ui/package-lock.json:23) and [inventory-ui/package-lock.json](C:/projects/pub-rec-opencode-deepseek/inventory-ui/package-lock.json:23) still list `sockjs-client`; [order-ui/package-lock.json](C:/projects/pub-rec-opencode-deepseek/order-ui/package-lock.json:31) and [inventory-ui/package-lock.json](C:/projects/pub-rec-opencode-deepseek/inventory-ui/package-lock.json:31) still list `@types/sockjs-client`. Neither package exists in either `package.json`. Running `npm install` locally removed those entries from both locks, so commit `df998a5` did not fully regenerate the lockfiles from the current manifests.

## Should Fix

- Full `npm audit` is still not clean. `npm audit --omit=dev` returns `found 0 vulnerabilities` in both UIs, but full audit reports `8` dev-tooling advisories per UI (`2 high`, `3 moderate`, `3 low`). This is improved from Sprint 9, but it is not "all dependencies have no vulnerabilities"; keep documenting it as dev-tooling exposure until Angular publishes a forward fix.

- `npm install` emitted deprecation warnings for `@angular/platform-browser-dynamic@22.0.4` and `@angular/animations@22.0.4`. This is not blocking Track A, but it is dependency hygiene to track for a later Angular modernization pass.

## Verification Run

Commands run:

```powershell
npm.cmd install                  # order-ui, synced local node_modules from lock
npm.cmd install                  # inventory-ui, synced local node_modules from lock
npm.cmd ls @angular/core @angular/compiler-cli @angular-devkit/build-angular @angular/cli
npm.cmd audit --omit=dev         # both UIs: found 0 vulnerabilities
npm.cmd audit --json             # both UIs: 8 full-tree advisories
npm.cmd run build                # order-ui, exit 0
npm.cmd run build                # inventory-ui, exit 0
.\node_modules\.bin\tsc.cmd --noEmit
.\node_modules\.bin\playwright.cmd test --list
.\node_modules\.bin\playwright.cmd install chromium
.\node_modules\.bin\playwright.cmd test
```

Build results:

- `order-ui`: `Application bundle generation complete`, exit 0.
- `inventory-ui`: `Application bundle generation complete`, exit 0.

Runtime stack:

- `auth-server`: `http://localhost:9000/oauth2/jwks` returned 200.
- `order-service`: `http://localhost:8080/actuator/health` returned `{"status":"UP"}`.
- `inventory-service`: `http://localhost:8081/actuator/health` returned `{"status":"UP"}`.
- Kafka/Zookeeper reachable on `127.0.0.1:9092` and `127.0.0.1:2181`.
- `order-ui` and `inventory-ui` dev servers were restarted before the Playwright run and served Sprint 10 dashboard chunks.

Cleanup:

- Generated `e2e/test-results/` was removed after review.
- Local package-lock changes caused by my verification `npm install` were restored. Repo status after cleanup was only the existing untracked `.claude/` before this review file was added.

