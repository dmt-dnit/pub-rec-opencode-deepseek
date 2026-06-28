import { test, expect, BrowserContext, Page } from '@playwright/test';

const ORDER_UI = 'http://localhost:4200';
const INVENTORY_UI = 'http://localhost:4201';

const CUSTOMER = { email: 'customer1@example.test', password: 'customer123' };
const WAREHOUSE = { email: 'warehouse1@example.test', password: 'warehouse123' };

function attachDiagnosticListeners(page: Page, label: string, errors: string[]) {
  page.on('console', msg => {
    if (msg.type() === 'error') errors.push(`[${label}] console.error: ${msg.text()}`);
  });
  page.on('pageerror', err => {
    errors.push(`[${label}] pageerror: ${err.message}`);
  });
  page.on('requestfailed', req => {
    const url = req.url();
    // Ignore known environmental noise
    if (url.includes('fonts.googleapis.com') || url.includes('favicon')) return;
    errors.push(`[${label}] requestfailed: ${req.failure()?.errorText} — ${url}`);
  });
}

/**
 * Log in to one of the Angular UIs.
 *
 * Both UIs share the same login template:
 *   input[name="email"], input[name="password"], button[type="submit"]
 * On success the Angular router navigates to /dashboard.
 */
async function login(
  page: Page,
  baseUrl: string,
  email: string,
  password: string,
): Promise<void> {
  await page.goto(baseUrl + '/login');
  await page.fill('input[name="email"]', email);
  await page.fill('input[name="password"]', password);
  await page.click('button[type="submit"]');
  await page.waitForURL('**/dashboard', { timeout: 15_000 });
}

test('smoke: order placement saga updates both dashboards', async ({ browser }, testInfo) => {
  // Two independent browser sessions — one per UI/user
  const orderCtx: BrowserContext = await browser.newContext();
  const inventoryCtx: BrowserContext = await browser.newContext();
  const orderPage: Page = await orderCtx.newPage();
  const inventoryPage: Page = await inventoryCtx.newPage();

  const diagnosticErrors: string[] = [];
  attachDiagnosticListeners(orderPage, 'order-ui', diagnosticErrors);
  attachDiagnosticListeners(inventoryPage, 'inventory-ui', diagnosticErrors);

  try {
    // ---------------------------------------------------------------
    // 1. Login
    // ---------------------------------------------------------------
    await login(orderPage, ORDER_UI, CUSTOMER.email, CUSTOMER.password);
    await login(inventoryPage, INVENTORY_UI, WAREHOUSE.email, WAREHOUSE.password);

    // ---------------------------------------------------------------
    // 2. Inventory table shows SKU-001 on initial load (no reload)
    //
    // Angular Material mat-table renders data cells as <td mat-cell>.
    // The attribute selector td[mat-cell] is more stable than class-
    // based selectors across Material major versions.
    // ---------------------------------------------------------------
    const sku001Cell = inventoryPage.locator('td[mat-cell]', { hasText: 'SKU-001' });
    await expect(sku001Cell).toBeVisible({ timeout: 10_000 });

    // Snapshot the initial quantity.
    // Columns defined in DashboardComponent: ['sku', 'name', 'quantityOnHand']
    // Index 0 = SKU, 1 = Name, 2 = Qty on Hand
    const sku001Row = inventoryPage.locator('tr[mat-row]', {
      has: inventoryPage.locator('td', { hasText: 'SKU-001' }),
    });
    const qtyCell = sku001Row.locator('td[mat-cell]').nth(2);
    const initialQtyText = await qtyCell.innerText();
    const initialQty = parseInt(initialQtyText.trim(), 10);

    // Capture current order card count before placing so we can wait for exactly +1
    const orderCards = orderPage
      .locator('mat-card')
      .filter({ has: orderPage.locator('.order-header') });
    const initialOrderCount = await orderCards.count();

    // ---------------------------------------------------------------
    // 3. Place order: increment SKU-001 to qty 1
    //
    // Each .sku-row has two type="button" icon buttons: remove (first)
    // and add (last).  We narrow by row text to avoid touching SKU-002
    // or SKU-003.
    // ---------------------------------------------------------------
    await orderPage
      .locator('.sku-row', { hasText: 'SKU-001' })
      .locator('button[type="button"]')
      .last()
      .click();

    // Assert the qty input updated — no waitForTimeout
    await expect(orderPage.locator('input[name="sku001Qty"]')).toHaveValue('1', { timeout: 3_000 });

    // Submit the order form
    await orderPage.locator('button[type="submit"]').click();

    // ---------------------------------------------------------------
    // 4 & 5. Button reverts from "Placing..." to "Place Order"
    //
    // "Placing..." is a transient state; the HTTP POST may complete
    // before the assertion fires on a fast local network.  The hard
    // requirement is that the button text reverts — not that we catch
    // the intermediate state.
    // ---------------------------------------------------------------
    try {
      await expect(orderPage.locator('button[type="submit"]'))
        .toContainText('Placing...', { timeout: 3_000 });
    } catch {
      // Acceptable: HTTP response arrived before this assertion ran
    }
    await expect(orderPage.locator('button[type="submit"]'))
      .toContainText('Place Order', { timeout: 15_000 });

    // ---------------------------------------------------------------
    // 6. New order card appears in the orders list
    //
    // DashboardComponent adds the new order at the top of this.orders.
    // Each order renders inside a mat-card with an .order-header child.
    // ---------------------------------------------------------------
    // Wait for exactly one new order card to appear (count increases by 1),
    // then pin newestOrderCard to the first card in the list (newest = prepended to top).
    await expect(orderCards).toHaveCount(initialOrderCount + 1, { timeout: 15_000 });
    const newestOrderCard = orderCards.first();
    await expect(newestOrderCard.locator('.order-header')).toBeVisible();

    // ---------------------------------------------------------------
    // 7. Order reaches CONFIRMED status (saga closes via WebSocket)
    //
    // order-service consumes inventory-events, updates order status,
    // and pushes the updated Order over STOMP to order-ui.
    // The Angular template applies .badge-confirmed when status is CONFIRMED.
    // ---------------------------------------------------------------
    await expect(newestOrderCard.locator('.badge-confirmed')).toBeVisible({ timeout: 20_000 });

    // ---------------------------------------------------------------
    // 8. Inventory reservation feed shows a RESERVED chip
    //
    // inventory-service publishes InventoryReservationEvent; inventory-ui
    // receives it over STOMP and prepends a mat-list-item to the feed.
    // The mat-chip inside the item contains the status text.
    // ---------------------------------------------------------------
    await expect(inventoryPage.locator('mat-list-item')).toBeVisible({ timeout: 20_000 });
    await expect(inventoryPage.locator('mat-chip', { hasText: 'RESERVED' }))
      .toBeVisible({ timeout: 5_000 });

    // ---------------------------------------------------------------
    // 9. SKU-001 quantity decremented by 1
    //
    // inventory-ui calls loadProducts() when a reservation event arrives,
    // so the mat-table row should reflect the updated stock level.
    // ---------------------------------------------------------------
    await expect(qtyCell).toHaveText(String(initialQty - 1), { timeout: 10_000 });

  } finally {
    if (diagnosticErrors.length > 0) {
      await testInfo.attach('browser-diagnostics', {
        body: diagnosticErrors.join('\n'),
        contentType: 'text/plain'
      });
    }
    await orderCtx.close();
    await inventoryCtx.close();
  }
});
