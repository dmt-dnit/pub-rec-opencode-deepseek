# M-3 — Playwright e2e smoke test

**Sprint:** 9  
**Priority:** High  
**Depends on:** M-1 must be applied before the test can pass; write the test now, verify after M-1 is merged

## Background

Codex P3 (Sprint 8): the browser smoke test relies on manual execution. Every sprint, the CD issue hid behind build/API-level checks. An automated e2e test that asserts the full browser contract would have caught the Angular CD regression at code-review time, not after a full Codex round.

Codex runs Playwright against Microsoft Edge. The test must be self-contained, cover the core saga, and be runnable on Windows with `npx playwright test`.

## What to implement

Create an `e2e/` directory at the repo root with a Playwright test that exercises the full demo flow.

### Directory structure

```
e2e/
  playwright.config.ts
  smoke.spec.ts
  package.json          # minimal: @playwright/test only
```

### `e2e/package.json`

```json
{
  "name": "pub-rec-e2e",
  "version": "0.0.0",
  "private": true,
  "devDependencies": {
    "@playwright/test": "^1.45.0"
  },
  "scripts": {
    "test": "playwright test",
    "test:headed": "playwright test --headed"
  }
}
```

### `e2e/playwright.config.ts`

```typescript
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: '.',
  timeout: 60_000,
  retries: 0,
  use: {
    headless: true,
    baseURL: 'http://localhost:4200',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
```

### `e2e/smoke.spec.ts`

The test must assert each step. Use `page.waitForSelector` or `expect(locator).toBeVisible()` with appropriate timeouts — do NOT add `page.waitForTimeout(N)` as a substitute for real assertions.

```typescript
import { test, expect, Browser, BrowserContext, Page } from '@playwright/test';

const ORDER_UI = 'http://localhost:4200';
const INVENTORY_UI = 'http://localhost:4201';

const CUSTOMER = { email: 'customer1@example.test', password: 'customer123' };
const WAREHOUSE = { email: 'warehouse1@example.test', password: 'warehouse123' };

async function login(page: Page, email: string, password: string) {
  await page.fill('input[name="email"], input[type="email"]', email);
  await page.fill('input[name="password"], input[type="password"]', password);
  await page.click('button[type="submit"]');
  // Wait for redirect to dashboard
  await page.waitForURL('**/dashboard', { timeout: 10_000 });
}

test('smoke: order placement saga updates both dashboards', async ({ browser }) => {
  // Open two browser contexts (separate sessions)
  const orderCtx: BrowserContext = await browser.newContext();
  const inventoryCtx: BrowserContext = await browser.newContext();
  const orderPage: Page = await orderCtx.newPage();
  const inventoryPage: Page = await inventoryCtx.newPage();

  try {
    // --- Login to both UIs ---
    await orderPage.goto(ORDER_UI + '/login');
    await login(orderPage, CUSTOMER.email, CUSTOMER.password);

    await inventoryPage.goto(INVENTORY_UI + '/login');
    await login(inventoryPage, WAREHOUSE.email, WAREHOUSE.password);

    // --- Inventory dashboard shows seeded stock without reload ---
    await expect(inventoryPage.locator('td.mat-cell', { hasText: 'SKU-001' }))
      .toBeVisible({ timeout: 10_000 });

    // Read initial quantity for SKU-001
    const qtyCell = inventoryPage.locator('tr', { has: inventoryPage.locator('td', { hasText: 'SKU-001' }) })
      .locator('td').nth(2);
    const initialQtyText = await qtyCell.innerText();
    const initialQty = parseInt(initialQtyText.trim(), 10);

    // --- Place an order from order-ui ---
    // Set SKU-001 quantity to 1 using the + button
    await orderPage.click('button[aria-label*="add"], button:has(mat-icon:text("add"))');
    // Wait a moment for the quantity binding to update
    await orderPage.waitForTimeout(200);

    // Click Place Order
    await orderPage.click('button[type="submit"]:has-text("Place Order")');

    // Button should show Placing... momentarily then revert
    await expect(orderPage.locator('button[type="submit"]')).toContainText('Placing...', { timeout: 5_000 });
    await expect(orderPage.locator('button[type="submit"]')).toContainText('Place Order', { timeout: 15_000 });

    // --- Order appears in the order list ---
    await expect(orderPage.locator('mat-card-content mat-card')).toBeVisible({ timeout: 15_000 });

    // --- Order reaches CONFIRMED status (saga completes) ---
    await expect(orderPage.locator('.badge-confirmed')).toBeVisible({ timeout: 20_000 });

    // --- Inventory feed shows RESERVED event ---
    await expect(inventoryPage.locator('mat-list-item')).toBeVisible({ timeout: 20_000 });
    await expect(inventoryPage.locator('mat-chip', { hasText: 'RESERVED' })).toBeVisible({ timeout: 5_000 });

    // --- Inventory quantity decremented ---
    const finalQtyText = await qtyCell.innerText();
    const finalQty = parseInt(finalQtyText.trim(), 10);
    expect(finalQty).toBe(initialQty - 1);

  } finally {
    await orderCtx.close();
    await inventoryCtx.close();
  }
});
```

## Setup instructions for the reviewer

After committing `e2e/`:

```bash
cd e2e
npm install
npx playwright install chromium   # or --with-deps on Linux
# Start the full stack first (Kafka, auth-server, order-service, inventory-service, both UIs)
npx playwright test
```

## Acceptance criteria

1. `e2e/` directory exists with `package.json`, `playwright.config.ts`, `smoke.spec.ts`.
2. `cd e2e && npm install` runs without error (only `@playwright/test` as dep — no other packages).
3. Show the full `npx playwright test` output with the test passing. If the full stack is not available in your environment, show `npx playwright test --list` (confirming the test is found) and state explicitly that stack is not available — do not assert "Pass" for the test run itself.
4. The test file must be syntactically valid TypeScript (compile with `npx tsc --noEmit`). Show that output.
5. The test assertions match the observable behavior described in Codex's Sprint 8 P1 finding — button reverts, order appears in list, CONFIRMED badge appears, reservation RESERVED chip appears, qty decrements.

**Do not report the e2e test as "Pass" if you cannot run the full stack. Show actual `playwright test` output or state the limitation.**
