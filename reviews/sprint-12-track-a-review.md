# Sprint 12 Codex Review

Date: 2026-06-28
Reviewer: Codex
Repo: `C:\projects\pub-rec-opencode-deepseek`

## Verdict

**Rejected / not cleared for Track A.**

Sprint 12 correctly targeted the previous global `.order-header` / `.badge-confirmed` issue, but the replacement locator still fails against the real DOM. The full Playwright smoke test remains red.

## Findings

### P1 - `orderCards.first()` selects the outer Orders card, not the newest order card

Evidence:

- `e2e/smoke.spec.ts:83-85` builds `orderCards` as:

```ts
const orderCards = orderPage
  .locator('mat-card')
  .filter({ has: orderPage.locator('.order-header') });
```

- `order-ui/src/app/pages/dashboard/dashboard.component.ts:72-95` nests each order `<mat-card>` inside the outer Orders `<mat-card>`.
- Playwright `filter({ has })` matches any descendant. Therefore the outer Orders card also matches because it contains all nested `.order-header` elements.
- The full smoke test fails at `e2e/smoke.spec.ts:134`:

```text
Locator: locator('mat-card').filter({ has: locator('.order-header') }).first().locator('.order-header')
Error: strict mode violation ... resolved to 3 elements:
1) 5ac92b48PENDING
2) da66fbb4CONFIRMED
3) 62982c15CONFIRMED
```

Why this matters:

The previous strict-mode issue is not fixed. The count wait can pass, but `newestOrderCard` is pinned to the container card, so the next scoped `.order-header` / `.badge-confirmed` assertions still see multiple orders.

Required fix:

Base the count on actual order headers, then derive the nearest order card from the newest header. Example:

```ts
const orderHeaders = orderPage.locator('.order-header');
const initialOrderCount = await orderHeaders.count();

await expect(orderHeaders).toHaveCount(initialOrderCount + 1, { timeout: 15_000 });

const newestOrderHeader = orderHeaders.first();
await expect(newestOrderHeader).toBeVisible();

const newestOrderCard = newestOrderHeader.locator('xpath=ancestor::mat-card[1]');
await expect(newestOrderCard.locator('.badge-confirmed')).toBeVisible({ timeout: 20_000 });
```

A cleaner long-term fix is adding a stable class or `data-testid` to the repeated order card in the Angular template, but that would touch `order-ui` as well as the E2E test.

## Scorecard

| Item | Result | Evidence |
| --- | --- | --- |
| P-1 scope order assertions to new order | FAIL | `e2e/smoke.spec.ts:83-85` still selects the outer Orders card via descendant matching; full Playwright fails at `:134`. |
| TypeScript compile | PASS | `e2e\.\node_modules\.bin\tsc.cmd --noEmit` exited 0. |
| Test discovery | PASS | `e2e\.\node_modules\.bin\playwright.cmd test --list` found exactly 1 test. |
| Full browser smoke | FAIL | `e2e\.\node_modules\.bin\playwright.cmd test` failed at `e2e/smoke.spec.ts:134`. |
| UI builds | PASS | `npm.cmd run build` passed in both `order-ui` and `inventory-ui`. |
| Production dependency audit | PASS | `npm.cmd audit --omit=dev` reported `found 0 vulnerabilities` in both UIs. |
| Full dependency audit | CAVEAT | Full audit still reports 8 dev-tooling advisories per UI, unchanged from prior review. |
| Java module tests | PASS WITH CAVEAT | Per-module `.\mvnw.cmd -q test` passed under Java 21. Running under JDK 25 correctly fails the project enforcer. Inventory-service still logs the known Testcontainers/Surefire shutdown warning despite exit 0. |

## Verification Run

Commands/results:

- `e2e\.\node_modules\.bin\tsc.cmd --noEmit`: PASS.
- `e2e\.\node_modules\.bin\playwright.cmd test --list`: PASS, 1 test.
- `npm.cmd run build` in `order-ui`: PASS.
- `npm.cmd run build` in `inventory-ui`: PASS.
- `npm.cmd audit --omit=dev` in both UIs: PASS, 0 production vulnerabilities.
- `npm.cmd audit --json` in both UIs: CAVEAT, 8 dev-tooling advisories each.
- `npm.cmd outdated --json` in both UIs: no `wanted` gaps; Angular packages remain at `22.0.4`, while npm currently reports lower `latest` dist-tags.
- `.\mvnw.cmd -q test` in `shared-model`, `auth-server`, `order-service`, `inventory-service` with `JAVA_HOME=C:\Users\dimit\.jdks\semeru-21.0.6`: PASS.
- Live stack health before browser run: auth `9000` 200, order-service `8080` UP, inventory-service `8081` UP, Kafka `9092` reachable, Zookeeper `2181` reachable.
- Restarted Angular dev servers on `4200` and `4201`: PASS.
- `e2e\.\node_modules\.bin\playwright.cmd test`: FAIL at `e2e/smoke.spec.ts:134`.

## Cleanup

Generated Playwright artifacts were removed after recording the failure evidence.
