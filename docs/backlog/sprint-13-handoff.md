# Sprint 13 Handoff — Track A close-out, round 11

**Date:** 2026-06-28  
**Coordinator:** Claude Code  
**Commits in this sprint:** `ff6e230` (Q-1 + docs + Sprint 12 review file)

---

## Scorecard

| Task | Status | Evidence |
|------|--------|----------|
| Q-1 Add `data-testid="order-card"` + update smoke test locator | DONE — agent worktree + coordinator apply | `ff6e230` — 1 attribute added to template, 4 lines changed in test |

---

## Q-1 — data-testid fix

### Why the previous locator failed

Sprint 12 used `locator('mat-card').filter({ has: locator('.order-header') })`. The order-ui dashboard has this nesting:

```html
<!-- outer "Orders" mat-card — line 72 of dashboard.component.ts -->
<mat-card style="margin-top:16px">
  <mat-card-content>
    <!-- individual order card — line 78 (now has data-testid) -->
    <mat-card *ngFor="let order of orders" data-testid="order-card">
      <div class="order-header">...</div>
    </mat-card>
  </mat-card-content>
</mat-card>
```

Playwright's `filter({ has })` matches on *any* descendant at any depth. The outer "Orders" card contains all `.order-header` elements in its subtree, so it also matched. It appears first in the DOM, so `.first()` returned the container. Scoped `.order-header` on the container resolved to all three orders, re-triggering strict mode.

`data-testid="order-card"` is placed on the `*ngFor` card only — the outer container does not have it. `locator('[data-testid="order-card"]')` is unambiguous regardless of nesting or database state.

### Changes in commit `ff6e230`

**`order-ui/src/app/pages/dashboard/dashboard.component.ts` line 78:**

```html
<!-- before -->
<mat-card *ngFor="let order of orders" style="margin-bottom:12px">
<!-- after -->
<mat-card *ngFor="let order of orders" style="margin-bottom:12px" data-testid="order-card">
```

**`e2e/smoke.spec.ts` lines 82–87:**

```typescript
// before
const orderCards = orderPage
  .locator('mat-card')
  .filter({ has: orderPage.locator('.order-header') });
const initialOrderCount = await orderCards.count();

// after
const orderCards = orderPage.locator('[data-testid="order-card"]');
const initialOrderCount = await orderCards.count();
```

All downstream assertions (`toHaveCount(initialOrderCount + 1)`, `newestOrderCard.locator('.order-header')`, `newestOrderCard.locator('.badge-confirmed')`) are unchanged — they were already correct, the only problem was the initial locator.

### Agent verification output

- `npx tsc --noEmit` → exit 0, no output
- `npx playwright test --list` → `Total: 1 test in 1 file`

Full Playwright cannot run in the agent/WSL environment (requires live stack). Build verification (`npm run build`) must be confirmed by Codex from PowerShell.

---

## What Codex needs to do

1. **Build order-ui** to confirm `data-testid` attribute doesn't break the Angular build:
   ```powershell
   cd C:\projects\pub-rec-opencode-deepseek\order-ui
   npm.cmd run build
   ```
   Must exit 0. (`data-testid` is a plain HTML attribute — no Angular-specific handling needed — but confirm the build is still clean.)

2. **Type check and test discovery:**
   ```powershell
   cd C:\projects\pub-rec-opencode-deepseek\e2e
   npm.cmd install
   .\node_modules\.bin\tsc.cmd --noEmit
   .\node_modules\.bin\playwright.cmd test --list
   ```

3. **Start the full stack** and run Playwright:
   ```powershell
   # Kafka, auth-server:9000, order-service:8080, inventory-service:8081
   # ng serve on 4200 and 4201 (restart after the order-ui build change)
   .\node_modules\.bin\playwright.cmd install chromium
   .\node_modules\.bin\playwright.cmd test
   ```

4. The smoke test must pass end-to-end — including on a non-empty database:
   - SKU-001 row visible in inventory table on login
   - Place Order button reverts to "Place Order"
   - `[data-testid="order-card"]` count increases by exactly 1; newest card shows `.order-header`
   - Newest card shows `.badge-confirmed` after saga closes
   - Inventory feed shows `RESERVED` chip
   - SKU-001 quantity decrements by 1
