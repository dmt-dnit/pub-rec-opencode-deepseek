# Sprint 10 — Track A final close-out, round 8

## Why this sprint exists

Codex rejected Sprint 9 (see `reviews/sprint-9-track-a-review.md`). Root cause of the must-fix:

**Angular 22 changed the default CD strategy to OnPush.** The internal logic in `node_modules/@angular/core/fesm2022/_debug_node-chunk.mjs:10044` is:
```
onPush: componentDefinition.changeDetection !== ChangeDetectionStrategy.Eager
```
When `changeDetection` is absent (undefined), `undefined !== 1` evaluates to `true` → `onPush: true`. Sprint 9's M-1 correctly added `provideZoneChangeDetection()` (zone scheduling) and `NgZone.run()` (STOMP callbacks) — but incorrectly removed `ChangeDetectionStrategy.Eager` from order-ui and never added it to inventory-ui. Zone ticks fired but Angular skipped re-rendering both OnPush components because they were never marked dirty.

Secondary must-fix: Playwright smoke test needs browser console/error listeners (Codex's explicit requirement).

Should-fix: Lockfiles are behind installed packages — `@angular/core` etc. still pinned at 22.0.2 in the locks while installed packages are 22.0.4. Needs a clean `npm install` from Windows PowerShell to realign.

## Task list

| ID  | Title                                                     | Priority  | Depends on |
|-----|-----------------------------------------------------------|-----------|------------|
| N-1 | Add `ChangeDetectionStrategy.Eager` to both dashboards    | Critical  | — (done)   |
| N-2 | Add error listeners to Playwright smoke test              | Must fix  | —          |
| N-3 | Align lockfiles to Angular 22.0.4 from clean install      | Should fix| —          |

N-1 was implemented directly by the coordinator (2-line change, commit `<see below>`).

## Acceptance gate

Sprint 10 closes when Codex confirms `npx playwright test` passes end-to-end — button reverts, order appears, CONFIRMED badge visible, inventory table populated, reservation feed shows RESERVED. That one green run closes Track A.
