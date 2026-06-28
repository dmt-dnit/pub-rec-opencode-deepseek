# N-2 — Add browser error listeners to Playwright smoke test

**Sprint:** 10  
**Priority:** Must fix (Codex explicitly required this in Sprint 9 review)

## What to change

File: `e2e/smoke.spec.ts`

Add `console`, `pageerror`, and `requestfailed` listeners to both browser pages before any navigation. Collect errors, and at the end of the test attach them as a diagnostic (do not hard-fail on expected noise like blocked Google font requests or favicon 404, which Codex noted in Sprint 8).

### Implementation

Add this helper near the top of the file (after the constants):

```typescript
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
```

Then in the test body, immediately after creating the pages:

```typescript
const diagnosticErrors: string[] = [];
attachDiagnosticListeners(orderPage, 'order-ui', diagnosticErrors);
attachDiagnosticListeners(inventoryPage, 'inventory-ui', diagnosticErrors);
```

And in the `finally` block, attach them to the test info:

```typescript
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
```

The `test` function signature needs to accept `testInfo`:

```typescript
test('smoke: order placement saga updates both dashboards', async ({ browser }, testInfo) => {
```

## Acceptance criteria

1. `npx tsc --noEmit` in `e2e/` exits 0 after the change — show actual output.
2. `npx playwright test --list` still shows 1 test — show actual output.
3. Show the diff of `smoke.spec.ts` — confirm the three event listeners are attached to both pages, `finally` block attaches diagnostics, test signature accepts `testInfo`.
4. Do not hard-fail the test on the filtered noise events (Google fonts, favicon). Only collect them for diagnostics.
