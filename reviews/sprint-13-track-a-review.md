# Sprint 13 Codex Review

Date: 2026-06-28
Reviewer: Codex
Repo: `C:\projects\pub-rec-opencode-deepseek`

## Verdict

**Approved / Track A browser gate cleared.**

No blocking findings.

Sprint 13 fixes the Sprint 12 selector bug correctly. The repeated order card now has `data-testid="order-card"` on the individual `*ngFor` card only, and the Playwright test uses `[data-testid="order-card"]` for the count-based wait and newest-card assertions. The full Playwright smoke test passes against the existing non-empty local database.

## Findings

None.

## Scorecard

| Item | Result | Evidence |
| --- | --- | --- |
| Q-1 add stable order-card selector | PASS | `order-ui/src/app/pages/dashboard/dashboard.component.ts:78` adds `data-testid="order-card"` only to the repeated order card. |
| Q-1 update Playwright locator | PASS | `e2e/smoke.spec.ts:86` uses `orderPage.locator('[data-testid="order-card"]')`; the previous descendant `filter({ has: .order-header })` locator is gone. |
| Full browser smoke | PASS | `e2e\.\node_modules\.bin\playwright.cmd test` passed: 1 test, 1 passed. |
| TypeScript compile | PASS | `e2e\.\node_modules\.bin\tsc.cmd --noEmit` exited 0. |
| Test discovery | PASS | `e2e\.\node_modules\.bin\playwright.cmd test --list` found exactly 1 test. |
| UI builds | PASS | `npm.cmd run build` passed in both `order-ui` and `inventory-ui`. |
| Production dependency audit | PASS | `npm.cmd audit --omit=dev` reported `found 0 vulnerabilities` in both UIs. |
| Full dependency audit | CAVEAT | Full audit still reports 8 dev-tooling advisories per UI: `@angular-devkit/build-angular`, `@angular/build`, `@babel/core`, `esbuild`, `http-proxy-middleware`, `sockjs`, `uuid`, `webpack-dev-server`. |
| Dependency currency | CAVEAT | Angular packages remain at `wanted 22.0.4`; `npm outdated` still reports lower `latest` dist-tags for some Angular packages, matching the prior review state. |

## Verification Run

Commands/results:

- `e2e\.\node_modules\.bin\tsc.cmd --noEmit`: PASS.
- `e2e\.\node_modules\.bin\playwright.cmd test --list`: PASS, 1 test.
- `npm.cmd run build` in `order-ui`: PASS.
- `npm.cmd run build` in `inventory-ui`: PASS.
- `npm.cmd audit --omit=dev` in both UIs: PASS, 0 production vulnerabilities.
- Full `npm.cmd audit --json` in both UIs: CAVEAT, 8 dev-tooling advisories each, 0 critical.
- `npm.cmd outdated --json` in both UIs: CAVEAT, no `wanted` gaps; current Angular packages are `22.0.4`.
- Live stack health before browser run: auth `9000` 200, order-service `8080` UP, inventory-service `8081` UP, Kafka `9092` reachable, Zookeeper `2181` reachable.
- Restarted Angular dev servers on `4200` and `4201`: PASS.
- `e2e\.\node_modules\.bin\playwright.cmd test`: PASS, 1 passed.

## Cleanup

Generated Playwright `e2e/test-results` artifacts were removed after recording the pass evidence.
