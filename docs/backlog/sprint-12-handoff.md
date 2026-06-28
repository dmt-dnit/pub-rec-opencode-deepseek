# Sprint 12 Handoff — Track A close-out, round 10

**Date:** 2026-06-28  
**Coordinator:** Claude Code  
**Commits in this sprint:** `993d62e` (P-1 + docs + Sprint 11 review file)

---

## Scorecard

| Task | Status | Evidence |
|------|--------|----------|
| P-1 Scope Playwright order assertions to newly placed order | DONE — agent worktree + coordinator apply | `993d62e` — `e2e/smoke.spec.ts` only, 3 targeted changes |

---

## P-1 — Playwright strict-mode fix

### Problem

`e2e/smoke.spec.ts:124` used `orderPage.locator('.order-header')` without scoping. After prior smoke runs the `order-service` H2 database accumulates orders (`PENDING`, `CONFIRMED`), so on the second run Playwright's strict mode threw:

```
Error: strict mode violation: locator('.order-header') resolved to 2 elements
```

`e2e/smoke.spec.ts:133` had the same issue for `.badge-confirmed` — an old CONFIRMED order could satisfy the assertion before the saga even ran.

### Changes in commit `993d62e`

**`e2e/smoke.spec.ts`** — three changes, no other files touched:

**1. Before step 3 (lines 82–86 inserted):**

```typescript
// Capture current order card count before placing so we can wait for exactly +1
const orderCards = orderPage
  .locator('mat-card')
  .filter({ has: orderPage.locator('.order-header') });
const initialOrderCount = await orderCards.count();
```

`orderCards` is a live Playwright locator (lazy, re-evaluated on each assertion). `initialOrderCount` is snapshotted once — before the submit click — to serve as the baseline for the count-based wait.

**2. Step 6 (`.order-header` assertion replaced):**

Before:
```typescript
await expect(orderPage.locator('.order-header')).toBeVisible({ timeout: 15_000 });
```

After:
```typescript
await expect(orderCards).toHaveCount(initialOrderCount + 1, { timeout: 15_000 });
const newestOrderCard = orderCards.first();
await expect(newestOrderCard.locator('.order-header')).toBeVisible();
```

`toHaveCount(initialOrderCount + 1)` waits for exactly one new card to appear, however many pre-existing orders are in the DB. `.first()` is the newest card because the dashboard prepends: `this.orders = [newOrder, ...this.orders]`.

**3. Step 7 (`.badge-confirmed` assertion scoped):**

Before:
```typescript
await expect(orderPage.locator('.badge-confirmed')).toBeVisible({ timeout: 20_000 });
```

After:
```typescript
await expect(newestOrderCard.locator('.badge-confirmed')).toBeVisible({ timeout: 20_000 });
```

`newestOrderCard` stays in scope from step 6. This ensures we wait for the *just-placed* order to reach CONFIRMED — an old CONFIRMED order in the list does not satisfy this assertion.

### Verification (coordinator, WSL)

`tsc --noEmit` and `playwright test --list` cannot be run from WSL (no `node_modules` are installed under `/mnt/c/` — only on the Windows side). The agent ran both in the worktree environment and reported:

- `npx tsc --noEmit` → exit 0, no output
- `npx playwright test --list` → `Total: 1 test in 1 file`

The coordinator verified the full file content by reading it directly — the diff matches the stated changes exactly and `newestOrderCard` is correctly scoped.

---

## What Codex needs to do

1. **Type check:**
   ```powershell
   cd C:\projects\pub-rec-opencode-deepseek\e2e
   npm.cmd install
   .\node_modules\.bin\tsc.cmd --noEmit
   ```
   Must exit 0.

2. **Test discovery:**
   ```powershell
   .\node_modules\.bin\playwright.cmd test --list
   ```
   Must show exactly 1 test.

3. **Start the full stack** and run Playwright:
   ```powershell
   # Kafka, auth-server:9000, order-service:8080, inventory-service:8081
   # ng serve on 4200 (order-ui) and 4201 (inventory-ui)
   .\node_modules\.bin\playwright.cmd install chromium
   .\node_modules\.bin\playwright.cmd test
   ```

4. **The test must pass end-to-end**, including on a non-empty database (prior orders in DB are fine — the test now waits for count+1 and scopes all subsequent assertions to the newest card):
   - SKU-001 row visible in inventory table on login
   - Place Order button reverts to "Place Order"
   - Order card count increases by exactly 1; newest card shows `.order-header`
   - Newest card shows `.badge-confirmed` after saga closes
   - Inventory feed shows `RESERVED` chip
   - SKU-001 quantity decrements by 1
