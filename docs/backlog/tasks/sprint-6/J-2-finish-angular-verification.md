# Task J-2: Finish the Angular 21 result — normalize it and prove it in a browser

**Resolves:** Must-fix in `reviews/sprint-5-track-a-review.md` ("both UIs are now on Angular 21 ... but the resolved lockfiles still diverge ... and no committed browser smoke-test evidence exists").

## Context

This task is narrower than Sprint 5's original Angular brief. The multi-major upgrade itself is already done:

- `order-ui/package.json:15,25,29,32`
- `inventory-ui/package.json:15,25,29,32`

Both apps are on Angular 21, both currently build in Codex's rerun, and both now show the same audit numbers (`14` total / `6 high` / `4 moderate` / `4 low`). That is real progress and should not be treated as a failed upgrade.

The remaining problems are:

1. The resolved dependency trees still differ even though the top-level dependencies match.
   - `order-ui/package-lock.json:12032-12033` vs. `inventory-ui/package-lock.json:12052-12053` (`semver`)
   - `order-ui/package-lock.json:12669-12670` vs. `inventory-ui/package-lock.json:12689-12690` (`string-width`)
   - `order-ui/package-lock.json:13050-13051` vs. `inventory-ui/package-lock.json:13069-13070` (`undici`)
2. The UI config is not fully aligned.
   - `order-ui/angular.json:35-58` contains a `schematics` block that `inventory-ui/angular.json` currently lacks.
3. No one has yet documented the required browser smoke test of the upgraded apps.

## Task

1. Starting from the current Angular 21 state, make the two UIs intentionally twin-consistent.
   - Do not re-run a full multi-major `ng update` chase unless current evidence proves it is necessary.
   - Normalize the lockfiles so the resolved trees match where they are supposed to match, not just the `package.json` versions.
   - Reconcile the `angular.json` drift. If the `schematics` block is intentional for only one app, document why; otherwise align it.
2. Re-run `npm install`, `npm run build`, `npm audit --json`, and `npm outdated --json` in both UIs after the normalization.
   - Report the exact results, not just "looks good."
   - If high-severity advisories remain, name the specific advisory chain that still causes them and the actual published version floor that clears them at the time you run the check.
   - Do not claim "Angular 22 is required" unless the current audit data actually shows that.
3. Do the manual browser smoke test that has been skipped across multiple sprints.
   - Check login, dashboard, order placement in `order-ui`, and stock view in `inventory-ui`.
   - Describe what you actually clicked through and what worked or failed.

## Out of scope

- Do not touch backend Maven dependencies.
- Do not add Dependabot or Renovate in this sprint.

## Acceptance criteria

- `order-ui` and `inventory-ui` have intentionally aligned lockfiles/config, not just matching `package.json` versions.
- `npm run build` succeeds in both UIs.
- `npm audit --json` and `npm outdated --json` are rerun in both UIs and the exact results are reported.
- The manual browser smoke test is actually performed and described.
