# P-1 — Scope Playwright smoke test order assertions to newly placed order

**Sprint:** 12  
**Priority:** Must fix — Track A gate  
**File to change:** `e2e/smoke.spec.ts` only

## Problem

`e2e/smoke.spec.ts:124` uses `orderPage.locator('.order-header')` without scoping. In Playwright strict mode, when multiple elements match a locator, the assertion throws:

```
Error: strict mode violation: locator('.order-header') resolved to 2 elements
```

The H2 database (`auth-server` is in-memory but `order-service` uses H2 which may persist sessions) accumulates orders across smoke runs. `e2e/smoke.spec.ts:133` has the same problem for `.badge-confirmed` — an old CONFIRMED order can satisfy the assertion without the saga having run at all.

## Root cause

The test checks that *an* order appears and *an* order becomes CONFIRMED. It should check that the *newly placed* order does those things. The dashboard prepends new orders (`this.orders = [newOrder, ...this.orders]`), so the newest card is always `.first()` in the DOM.

## Exact changes to make in `e2e/smoke.spec.ts`

### 1. Capture order card count before placing the order

Insert this block immediately **before** the comment `// 3. Place order: increment SKU-001 to qty 1` (currently around line 83):

```typescript
    // Capture current order card count before placing so we can wait for exactly +1
    const orderCards = orderPage
      .locator('mat-card')
      .filter({ has: orderPage.locator('.order-header') });
    const initialOrderCount = await orderCards.count();
```

### 2. Replace the global `.order-header` assertion (currently line 124)

Remove:
```typescript
    await expect(orderPage.locator('.order-header')).toBeVisible({ timeout: 15_000 });
```

Replace with:
```typescript
    // Wait for exactly one new order card to appear (count increases by 1),
    // then pin newestOrderCard to the first card in the list (newest = prepended to top).
    await expect(orderCards).toHaveCount(initialOrderCount + 1, { timeout: 15_000 });
    const newestOrderCard = orderCards.first();
    await expect(newestOrderCard.locator('.order-header')).toBeVisible();
```

### 3. Replace the global `.badge-confirmed` assertion (currently line 133)

Remove:
```typescript
    await expect(orderPage.locator('.badge-confirmed')).toBeVisible({ timeout: 20_000 });
```

Replace with:
```typescript
    await expect(newestOrderCard.locator('.badge-confirmed')).toBeVisible({ timeout: 20_000 });
```

`newestOrderCard` was defined in step 2 above and remains in scope — no change needed to the surrounding code.

## Acceptance criteria

1. `npx tsc --noEmit` in `e2e/` exits 0 — show the output.
2. `npx playwright test --list` discovers exactly 1 test — show the output.
3. No other lines in `e2e/smoke.spec.ts` are changed. Do not touch `inventory-ui`, `order-ui`, or any backend files.
4. State explicitly: "I cannot run the full Playwright test without the live stack — the TypeScript compile and test discovery checks are the limit of what I can verify locally."

## Why `.first()` is correct here

The order-ui `DashboardComponent` prepends new orders to the top of the list: `this.orders = [newOrder, ...this.orders]`. Angular renders `*ngFor` in array order, so the newest `mat-card` is always the first one in the DOM. After waiting for `count + 1`, `.first()` is definitively the just-placed order.

## WSL limitation note

`npx playwright test` requires the full running stack (Kafka, all 4 Spring services, both ng-serve dev servers). This cannot be run in WSL; it can only be verified by Codex from Windows. Show `tsc --noEmit` and `--list` outputs as the WSL-reachable verification.
