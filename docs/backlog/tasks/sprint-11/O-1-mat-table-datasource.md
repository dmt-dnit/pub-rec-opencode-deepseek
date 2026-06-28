# O-1 — Fix inventory-ui `mat-table` rendering (use `MatTableDataSource`)

**Sprint:** 11  
**Priority:** Must fix — Track A gate

## Problem

After all previous Angular CD fixes (`ChangeDetectionStrategy.Eager`, `provideZoneChangeDetection`, `NgZone.run` in WebSocket service, `withXhr()`), inventory-ui's `mat-table` still renders empty rows even though `GET /api/inventory` returns SKU-001/002/003 (200) and the dashboard component has `Eager` (CheckAlways) CD.

Codex confirmed via DOM diagnostic: `rows: []`, `cells: []` after a successful HTTP response (Sprint 10 review).

## Root cause

The `[dataSource]="products"` binding passed a plain `Product[]` to `mat-table`. When Angular Material's `CdkTable` receives a plain array as `dataSource`, it wraps it in `of(array)` — an Observable that emits once and completes immediately. The subscription fires synchronously during `ngOnChanges`, but the timing with Angular's CD cycle means the rows are registered and immediately "completed" before the render phase, leaving the table with no persistent data stream to draw from.

`MatTableDataSource` solves this by maintaining a persistent `BehaviorSubject` internally. When `productsSource.data = products` fires, `MatTableDataSource` calls `this._data.next(products)`, and `CdkTable`'s existing subscription receives the emission and calls `renderRows()` followed by `markForCheck()`. This bypasses the fragile synchronous-subscribe-then-complete race entirely.

## What was changed

**`inventory-ui/src/app/pages/dashboard/dashboard.component.ts`**:

1. Import — added `MatTableDataSource` to the `@angular/material/table` import:
   ```typescript
   import { MatTableModule, MatTableDataSource } from '@angular/material/table';
   ```

2. Class field — replaced plain array with `MatTableDataSource`:
   ```typescript
   // before
   products: Product[] = [];
   // after
   productsSource = new MatTableDataSource<Product>([]);
   ```

3. `loadProducts()` — assigns to `.data` instead of reassigning the field:
   ```typescript
   next: products => { this.productsSource.data = products; },
   ```

4. Template — `[dataSource]` now binds to `productsSource`:
   ```html
   <table mat-table [dataSource]="productsSource" class="full-width">
   ```

## Acceptance criteria

1. `npm run build` in `inventory-ui/` exits 0 — no TypeScript errors.
2. After login as `warehouse1@example.test` (password `warehouse123`) on `http://localhost:4201`, the Stock Dashboard table renders SKU-001, SKU-002, SKU-003 without a page reload.
3. The Playwright smoke test passes assertion at `e2e/smoke.spec.ts:70` — `td[mat-cell]` with text `SKU-001` is visible within 10 seconds of login.
4. `ChangeDetectionStrategy.Eager` remains on the component decorator (do not remove — see Sprint 10 N-1 for why it is required).

## WSL build limitation

`npm run build` invokes esbuild via a Windows binary (`node_modules/.bin/esbuild` is a `.exe`). It cannot run in WSL. Build verification must be done from Windows PowerShell (`npm.cmd run build` from `inventory-ui\`). If Codex runs it as part of the review, it will confirm or fail the build. The coordinator cannot run it locally.
