# Sprint 11 Codex Review

Date: 2026-06-28
Reviewer: Codex
Repo: `C:\projects\pub-rec-opencode-deepseek`

## Verdict

**Not cleared for Track A yet.**

The Sprint 11 implementation fixes are correct for the stated briefs:

- **O-1 PASS**: `inventory-ui` now uses a persistent `MatTableDataSource<Product>` for the Material table.
- **O-2 PASS**: direct `sockjs-client` / `@types/sockjs-client` lockfile entries are gone from both UIs, while transitive server-side `sockjs` remains.

The blocker is the browser verification gate: the current Playwright smoke test fails in a non-empty local database because it uses global strict locators for order cards.

## Findings

### P1 - Playwright smoke test is not idempotent with persisted orders

Evidence:

- `e2e/smoke.spec.ts:124` asserts `expect(orderPage.locator('.order-header')).toBeVisible()`.
- In my run, that locator resolved to two persisted order headers:
  - `62982c15PENDING`
  - `da66fbb4CONFIRMED`
- Playwright strict mode therefore failed before the saga verification reached the inventory decrement assertion.
- `e2e/smoke.spec.ts:133` has the same broad-selector problem for `.badge-confirmed`; even after fixing line 124, an old confirmed order can satisfy that assertion unless it is scoped to the newly created order.

Observed failure:

```text
Error: expect(locator).toBeVisible() failed
Locator: locator('.order-header')
Error: strict mode violation: locator('.order-header') resolved to 2 elements
```

Why this matters:

The E2E gate should be repeatable on a developer machine after prior smoke runs. Right now, a successful previous run leaves data that can break the next run or create false positives.

Required fix:

- Scope the assertions to the newly created order, not all orders.
- Prefer capturing the placed order id from the POST response or snackbar text and locating the matching `mat-card`.
- At minimum, use the newest order card consistently and scope status checks inside it:

```ts
const newestOrderCard = orderPage.locator('mat-card', {
  has: orderPage.locator('.order-header'),
}).first();

await expect(newestOrderCard.locator('.order-header')).toBeVisible({ timeout: 15_000 });
await expect(newestOrderCard.locator('.badge-confirmed')).toBeVisible({ timeout: 20_000 });
```

This is still weaker than matching the created order id, but it removes the strict-mode failure and the stale `.badge-confirmed` false positive.

## Scorecard

| Item | Result | Evidence |
| --- | --- | --- |
| O-1 inventory table fix | PASS | `inventory-ui/src/app/pages/dashboard/dashboard.component.ts:8`, `:36`, `:91`, `:118` use `MatTableDataSource` and assign `.data`. The Playwright run reached past `SKU-001` table visibility before failing later on order selectors. |
| O-2 lockfile cleanup | PASS | `rg "sockjs-client|@types/sockjs-client"` returned no matches in both UI package files/lockfiles. `npm ls sockjs-client` returns `(empty)` for both UIs. |
| Browser gate | FAIL | `npx playwright test` equivalent failed at `e2e/smoke.spec.ts:124` due strict-mode multi-match on persisted orders. |
| Production dependency audit | PASS | `npm audit --omit=dev` returns `0 vulnerabilities` in both UIs. |
| Full dependency audit | CAVEAT | Full audit still reports 8 dev-tooling advisories per UI, with no forward Angular 22 fix path identified in this sprint. |

## Verification Run

Commands/results:

- `npm.cmd run build` in `order-ui`: PASS.
- `npm.cmd run build` in `inventory-ui`: PASS.
- `npm.cmd audit --omit=dev` in both UIs: PASS, `0 vulnerabilities`.
- `npm.cmd audit --json` in both UIs: CAVEAT, 8 dev-tooling advisories remain per UI.
- `npm.cmd ls sockjs-client` in both UIs: PASS, `(empty)`.
- `e2e\.\node_modules\.bin\tsc.cmd --noEmit`: PASS.
- `e2e\.\node_modules\.bin\playwright.cmd test --list`: PASS, 1 smoke test discovered.
- Backend health checks on `9000`, `8080`, `8081`: PASS.
- Kafka/Zookeeper local port checks on `9092`, `2181`: PASS.
- Restarted Angular dev servers on `4200` and `4201`: PASS.
- `e2e\.\node_modules\.bin\playwright.cmd test`: FAIL at `e2e/smoke.spec.ts:124`.

## Residual Notes

- The `inventory-ui` table fix itself is validated by the smoke test reaching past the `SKU-001` table visibility assertion.
- Angular dev server emits a webpack builder deprecation warning. This is not a Sprint 11 blocker, but should remain tracked with the Angular upgrade work.
- Generated Playwright artifacts were removed after recording the failure evidence.
