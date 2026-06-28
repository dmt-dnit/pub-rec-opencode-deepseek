# Sprint 11 Handoff — Track A close-out, round 9

**Date:** 2026-06-28  
**Coordinator:** Claude Code  
**Commits in this sprint:** `df75d07` (O-1 + O-2 + docs + Sprint 10 review file)

---

## Scorecard

| Task | Status | Evidence |
|------|--------|----------|
| O-1 Fix inventory-ui `mat-table` — use `MatTableDataSource` | DONE — coordinator direct | `df75d07` — 3 lines changed in dashboard.component.ts |
| O-2 Remove stale `sockjs-client` entries from both lockfiles | DONE — coordinator direct | `df75d07` — 4 node_modules sections + 2 root entries removed from each lockfile |

---

## O-1 — `MatTableDataSource` fix

### Why the plain array binding failed

`[dataSource]="products"` with a plain `Product[]` caused `CdkTable` to call `of(products)` — a cold Observable that emits once and completes immediately. The subscription fires synchronously during `ngOnChanges`, but `renderRows()` in Angular Material 22's `CdkTable` runs in a context where the component's view has already passed its CD check for this cycle. The render registers and completes before Angular processes it, leaving the table with no data.

`MatTableDataSource` eliminates this by maintaining a persistent `BehaviorSubject` internally. Setting `productsSource.data = products` calls `this._data.next(products)` on the subject; the table's existing subscription receives the emission and calls `renderRows()` followed by `this._changeDetectorRef.markForCheck()`. This marks the table (and all its ancestors) dirty for the next CD cycle, which is what actually drives the DOM update. No CD race condition.

### Changes in commit `df75d07`

**`inventory-ui/src/app/pages/dashboard/dashboard.component.ts`**:

| Line | Before | After |
|------|--------|-------|
| 8 | `import { MatTableModule } from '@angular/material/table';` | `import { MatTableModule, MatTableDataSource } from '@angular/material/table';` |
| 36 | `<table mat-table [dataSource]="products"` | `<table mat-table [dataSource]="productsSource"` |
| 91 | `products: Product[] = [];` | `productsSource = new MatTableDataSource<Product>([]);` |
| 118 | `next: products => this.products = products,` | `next: products => { this.productsSource.data = products; },` |

`ChangeDetectionStrategy.Eager` is still on the decorator (line 20) — not removed.

### Build verification

The TypeScript change is minimal and type-safe: `MatTableDataSource<Product>` is the standard Angular Material generic type, `[dataSource]` accepts it directly, and `.data` is the correct setter. `npm run build` must be verified by Codex from Windows PowerShell — WSL cannot invoke the esbuild Windows binary.

---

## O-2 — Lockfile cleanup

### Why the stale entries existed

Sprint 10 N-3 deleted both `package-lock.json` files and ran `npm install --package-lock-only` in WSL. This flag regenerates the lockfile without touching `node_modules`. However, the `node_modules` directories on the Windows-side (`/mnt/c/`) still had `sockjs-client` and `@types/sockjs-client` installed from before Sprint 8's removal. npm's `--package-lock-only` mode mirrors what is actually in `node_modules` (not just what is in `package.json`), so the stale packages were re-written into both lockfiles.

### What was removed

A Node.js script parsed each lockfile as JSON and deleted:

**Root `""` package section:**
- `dependencies["sockjs-client"]` (`"^1.6.1"`)
- `devDependencies["@types/sockjs-client"]` (`"^1.5.4"`)

**`node_modules/` tree sections (4 per lockfile):**
- `node_modules/@types/sockjs-client`
- `node_modules/sockjs-client`
- `node_modules/sockjs-client/node_modules/debug`
- `node_modules/sockjs-client/node_modules/eventsource`

**What was intentionally kept:**
- `node_modules/sockjs` — the server-side WebSocket transport, a transitive dep of `webpack-dev-server` → `@angular-devkit/build-angular`. Still legitimately required.
- `node_modules/@types/sockjs` — TypeScript types for the server-side sockjs. Kept for the same reason.

### Verification (coordinator)

```bash
grep -r "sockjs-client" order-ui/package-lock.json inventory-ui/package-lock.json
# (no output — entries are gone)
```

---

## What Codex needs to do

1. **Build both UIs** from Windows PowerShell:
   ```powershell
   cd C:\projects\pub-rec-opencode-deepseek\order-ui && npm.cmd run build
   cd C:\projects\pub-rec-opencode-deepseek\inventory-ui && npm.cmd run build
   ```
   Both must exit 0. TypeScript must not flag `MatTableDataSource`.

2. **Audit check** (from each UI dir):
   ```powershell
   npm.cmd audit --omit=dev
   ```
   Must still show `found 0 vulnerabilities` in both UIs (O-2 removed only stale direct deps, not any transitive security-relevant package).

3. **Lockfile check:**
   ```powershell
   npm.cmd ls sockjs-client
   ```
   Must show `(empty)` or `-- sockjs-client` with no resolved version. If `npm install` is run after the lockfile update, sockjs-client must not appear in `node_modules`.

4. **Start the full stack** and run the Playwright smoke test:
   ```powershell
   # Start: Kafka, auth-server:9000, order-service:8080, inventory-service:8081
   # Then: npm.cmd start in order-ui (4200) and inventory-ui (4201)
   cd C:\projects\pub-rec-opencode-deepseek\e2e
   npm.cmd install
   npx playwright install chromium
   npx playwright test
   ```

5. The smoke test must pass **end-to-end**:
   - `td[mat-cell]` with text `SKU-001` visible within 10 s of inventory login ← **this is the blocker from Sprint 10**
   - "Place Order" button reverts from "Placing…"
   - `.order-header` card appears in order-ui
   - `.badge-confirmed` visible after saga closes
   - `mat-chip` with text `RESERVED` visible in inventory feed
   - SKU-001 quantity decremented by 1
