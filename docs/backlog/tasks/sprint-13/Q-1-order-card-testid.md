# Q-1 — Add `data-testid="order-card"` to individual order card; update smoke test locator

**Sprint:** 13  
**Priority:** Must fix — Track A gate  
**Files to change:** `order-ui/src/app/pages/dashboard/dashboard.component.ts` AND `e2e/smoke.spec.ts`

## Problem

`e2e/smoke.spec.ts` uses:

```typescript
const orderCards = orderPage
  .locator('mat-card')
  .filter({ has: orderPage.locator('.order-header') });
```

Playwright's `filter({ has })` matches on *any* descendant at any depth. The order-ui dashboard has this nesting:

```html
<!-- outer "Orders" mat-card — contains ALL .order-header descendants -->
<mat-card style="margin-top:16px">
  <mat-card-header>Orders</mat-card-header>
  <mat-card-content>
    <!-- individual order mat-card — contains exactly ONE .order-header -->
    <mat-card *ngFor="let order of orders" style="margin-bottom:12px">
      <mat-card-content>
        <div class="order-header">...</div>
      </mat-card-content>
    </mat-card>
  </mat-card-content>
</mat-card>
```

The outer card matches `filter({ has: .order-header })` because it has `.order-header` descendants. It appears first in the DOM, so `orderCards.first()` returns the outer container, not the newest order card. The scoped `.order-header` locator then matches all three order headers inside, re-triggering a strict-mode violation.

## Fix

### Change 1 — `order-ui/src/app/pages/dashboard/dashboard.component.ts` line 78

Add `data-testid="order-card"` to the individual order `mat-card`:

Before:
```html
<mat-card *ngFor="let order of orders" style="margin-bottom:12px">
```

After:
```html
<mat-card *ngFor="let order of orders" style="margin-bottom:12px" data-testid="order-card">
```

This is the only change to the Angular template. Do not touch any other line.

### Change 2 — `e2e/smoke.spec.ts` lines 82–86

Replace the `orderCards` locator (the 5 lines that define `orderCards` and `initialOrderCount`):

Before:
```typescript
    // Capture current order card count before placing so we can wait for exactly +1
    const orderCards = orderPage
      .locator('mat-card')
      .filter({ has: orderPage.locator('.order-header') });
    const initialOrderCount = await orderCards.count();
```

After:
```typescript
    // Capture current order card count before placing so we can wait for exactly +1
    // data-testid="order-card" targets individual order cards only — avoids the
    // descendant-matching trap where the outer Orders mat-card also satisfies
    // filter({ has: .order-header }) because it wraps all order cards.
    const orderCards = orderPage.locator('[data-testid="order-card"]');
    const initialOrderCount = await orderCards.count();
```

The rest of the test is unchanged: `orderCards.first()` correctly returns the newest individual order card (newest is prepended to `this.orders`, so it renders first in the `*ngFor`). The `toHaveCount`, `newestOrderCard.locator('.order-header')`, and `newestOrderCard.locator('.badge-confirmed')` assertions all stay as-is.

## Acceptance criteria

1. `npx tsc --noEmit` in `e2e/` exits 0 — show the output.
2. `npx playwright test --list` in `e2e/` shows exactly 1 test — show the output.
3. No other lines are changed in either file.
4. State explicitly: "I cannot run the full Playwright test without the live stack."

## Why `data-testid` is correct

`data-testid` is the standard pattern for marking DOM elements as test targets. It is:
- Semantically neutral (no effect on styling or behaviour)
- Immune to the descendant-matching ambiguity that broke `filter({ has })`
- Stable across Angular Material version changes (unlike structural CSS selectors)

The attribute goes on the *individual* order `mat-card` (`*ngFor` line), not the outer "Orders" container card.
