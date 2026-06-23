# Task I-2: Finish the Angular upgrade — both UIs, clear the audit floor, smoke test

**Resolves:** Must-fix in `reviews/sprint-4-track-a-review.md` ("`inventory-ui` is still on Angular 18.2.x... the two UIs do not match. The dependency state is also still red... `npm audit --json` reports `25` vulnerabilities in `order-ui`... and `50` in `inventory-ui`... `npm outdated --json` also shows both apps behind current published Angular releases, with `order-ui` still below the patched 21.x line").

## Context

This is the third sprint in a row this finding has appeared (`F-6`, `G-4`/`H-3`, now `I-2`), and the second sprint where the fix was attempted but left half-done: `order-ui` was bumped from Angular 18.2.x to 19.2.x (real progress — audit findings roughly halved), but `inventory-ui` was never touched at all, and 19.2.x is still below the floor `npm audit` currently reports as patched. Codex's review on 2026-06-22 shows both apps still behind current published releases.

**This task does not start over.** `order-ui` is partway there — continue its upgrade forward, then bring `inventory-ui` to match.

## Task

1. In `order-ui`, re-check current advisory data (`npm audit`) — don't assume 19.2.x was the right stopping point, the prior sprint's own audit run is the evidence it wasn't. Continue the `ng update` chain forward one major at a time:
   ```
   npx ng update @angular/core@20 @angular/cli@20
   npm run build
   npx ng update @angular/core@21 @angular/cli@21   # if published; check what's actually current
   npm run build
   ```
   Stop once `npm audit` no longer reports the high-severity findings tied to `@angular/core`/`@angular/common`/`@angular/compiler` — re-check the advisory's actual patched-version floor as you go, don't guess a target version number.
2. Read `ng update`'s own migration-schematic output at each step rather than blindly continuing. Given this app uses Angular Material and standalone components, pay specific attention to those APIs plus `angular.json`/`proxy.conf.json` build-config schema changes if `ng update` flags any.
3. Once `order-ui` builds clean and clears the audit floor, copy its **exact final** `package.json` dependency block and resolved lockfile versions to `inventory-ui` — do not run `ng update` independently on `inventory-ui`, copy the already-verified result so both apps stay on identical versions (twin-consistency requirement, ADR-0001). Then `npm install` and `npm run build` in `inventory-ui` to confirm it also builds clean on the copied versions.
4. Re-run `npm audit` in **both** apps and report the actual before/after numbers — not a summary claim. Both should show the same vulnerability count, since they're now on identical dependency versions.
5. Manually smoke-test both apps in a browser: login, dashboard, order placement (`order-ui`) / stock view (`inventory-ui`). This has been required by every Angular-upgrade brief since Sprint 4 and has not been done yet by anyone in this chain — do it this time, and describe what you actually clicked through, not just "it works."

## Out of scope
- Don't touch backend (Maven) dependencies.
- Don't add Dependabot/Renovate config.

## Acceptance criteria
- `order-ui` and `inventory-ui` have identical `@angular/*` dependency versions in `package.json` and matching lockfile resolutions.
- `npm audit` in both apps shows the same vulnerability count, materially lower than the Sprint 4 baseline (`order-ui`: 25 total/16 high; `inventory-ui`: 50 total/30 high) — if any high-severity findings remain, name the specific advisory and its actual patched-version requirement, re-checked at the time you run this, not copied from a prior sprint.
- `npm run build` succeeds in both apps.
- A manual browser smoke test of both apps' core flows is reported as actually performed, with what was checked — not just asserted.
