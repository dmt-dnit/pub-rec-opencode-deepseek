# Sprint 26 — Handoff to Codex

**Coordinator:** Claude Code. **Date:** 2026-07-15.
**Task:** Track C Phase 4 — wire `order-ui`/`inventory-ui` to be deployable as static SPAs on Vercel, pointed at the live `dnit-vps` backend subdomains.
**Implementer:** opencode+DeepSeek, standing default, one isolated worktree per task (V-1/V-2/V-3), all three merged onto `main` in sequence (V-1 → V-2 → V-3).
**Merge-gate tier:** coordinator-supervised, pushed direct to `main` per the Sprint 21 policy.
Review-Target-Commit: 9902156

## Why this sprint exists

Dimitri created the two Vercel projects in the dashboard (`pub-rec-saga-orders-ui.vercel.app`, `pub-rec-saga-inventory-ui.vercel.app`) after a prior session crashed mid-setup. This sprint wires the code side of Phase 4: env-based API base URLs + `vercel.json` SPA fallback (V-1), CORS allow-list for the two Vercel origins on all 3 Spring services (V-2), and `workflow_dispatch`-only GitHub Actions deploy-hook workflows (V-3).

**Sequencing note, logged not hidden:** Sprint 25 (package rename `com.example`→`be.dnit`) was originally scoped as "before Phase 4 resumes," but its Codex review was never requested — Dimitri made an explicit call (2026-07-15) to skip that review and proceed straight to this sprint, given Sprint 25's rename was already coordinator-verified via a full independent rebuild (`docs/backlog/sprint-25-handoff.md`). `scripts/verify-review.sh 25` still returns **UNKNOWN** (no review file exists) — that is expected and intentional, not a process failure to chase.

## What was done (3 commits, `main`)

- **V-1** (`b7324de`): `order-ui`/`inventory-ui` each get `src/environments/environment.ts` (dev, empty strings — proxy-relative behavior under `ng serve` unchanged) and `environment.prod.ts` (prod origins: `saga-auth.dnit.be`, `saga-orders.dnit.be`/`saga-inventory.dnit.be`). `angular.json`'s `build` target gets a new `production` configuration with `fileReplacements`, set as `defaultConfiguration` so a plain `ng build` (what Vercel runs) picks it up. All 4 API service files (`auth.service.ts` ×2, `order.service.ts`, `inventory.service.ts`) prefix `apiBase` with the environment origin. Both `websocket.service.ts` files use `environment.wsBase` when set, falling back to the existing `window.location`-based construction when empty (dev). `vercel.json` (SPA fallback rewrite) added to both UIs.
- **V-2** (`b3ddf7b`): CORS allow-lists extended — `auth-server` gets both Vercel origins, `order-service` gets only the orders-ui origin, `inventory-service` gets only the inventory-ui origin. Existing `localhost:4200`/`4201` entries untouched.
- **V-3** (`9902156`): two new GitHub Actions workflows, `deploy-order-ui.yml`/`deploy-inventory-ui.yml`, `workflow_dispatch` only (deliberately not push-triggered, matching this repo's existing backend deploy workflows rather than the Pet Giftshop push-trigger reference this pattern was adapted from). Distinct secret names per workflow (`VERCEL_DEPLOY_HOOK_URL_ORDER_UI` / `VERCEL_DEPLOY_HOOK_URL_INVENTORY_UI`).

## A real environment detour worth flagging to the reviewer

Getting V-1's build verification to actually run hit a chain of WSL `/mnt/c` filesystem issues — none of them are code defects, but they're worth knowing about if this comes up again:
1. Coordinator error: a backgrounded `cp` of `node_modules` into the V-1 worktree raced against a manual fix-attempt, corrupting the copy.
2. Independently, the **main repo's own `inventory-ui/node_modules` was already a stale partial install** (4 packages, dated days before this session) — unrelated to this sprint, likely a leftover from the earlier session crash. Discovered by cross-checking package count against `order-ui`'s (489), not assumed.
3. `npm install` on `/mnt/c` hit transient `EACCES`/`ENOENT` renames (known WSL2 drvfs behavior under many-small-file operations) on two further retries.

Resolution: ran a clean `npm install` in the **main repo** (verified 489 packages, `ng` binary resolves), symlinked the worktree's `inventory-ui/node_modules` to it instead of copying (copying a tree that size on this filesystem is what kept failing), then built there. **A symlinked `node_modules` is not matched by `.gitignore`'s `**/node_modules/` pattern** (trailing slash only matches real directories) — caught via `git status --short` showing it as untracked, removed before committing so it was never at risk of being staged. Separately, one of the interrupted worktree `npm install` attempts deleted `inventory-ui/package-lock.json` (a real tracked file) — caught the same way and restored with `git checkout -- inventory-ui/package-lock.json` before the commit. Neither made it into the commit that's actually being reviewed; noted here so a reviewer isn't puzzled by anything if these turn up in agent transcripts.

## Coordinator verification — full independent rebuild from integrated `main`, not just each worktree in isolation

- `shared-model`: `./mvnw clean install` — BUILD SUCCESS.
- `auth-server`: `./mvnw clean verify` — BUILD SUCCESS (no tests, pre-existing).
- `order-service`: `./mvnw clean verify` — **BUILD SUCCESS, Tests run: 9, Failures: 0, Errors: 0, Skipped: 2** (matches the Sprint 25 baseline exactly — CORS is not exercised by these tests, expected).
- `inventory-service`: `./mvnw clean verify` — **BUILD SUCCESS, Tests run: 9, Failures: 0, Errors: 0, Skipped: 1** (matches baseline).
- `order-ui`: `npx ng build` (default = production config) — **BUILD SUCCESS**. `grep -l "saga-orders.dnit.be" order-ui/dist/browser/*.js` and `grep -l "saga-auth.dnit.be" order-ui/dist/browser/*.js` both match `main.js` — real proof `fileReplacements` fired, not just a green build.
- `inventory-ui`: same — **BUILD SUCCESS**, `grep -l "saga-inventory.dnit.be"` and `grep -l "saga-auth.dnit.be"` both match `main.js`.
- Confirmed Vercel **Output Directory must be `dist/browser`**, not `dist` — this builder (Angular 22, `@angular-devkit/build-angular:application`, no SSR) always nests browser output under `browser/`. Dimitri needs to set this in both Vercel projects' settings before the first real deploy; this sprint didn't touch Vercel dashboard settings itself (see "Out of scope").
- CORS strings re-grepped directly on integrated `main` (not trusted from the per-task diffs): all three `SecurityConfig.java` files show the exact expected origin lists, no typos, no trailing slashes.
- Both new workflow files re-validated as parseable YAML directly on `main`; confirmed `on:` blocks are `workflow_dispatch:` only, no `push:` trigger, in either file.
- `git status --short` clean on `main` after all three cherry-picks; build artifacts (`dist/`, symlinked `node_modules`) never staged.

## Explicitly out of scope this sprint (unchanged from the brief, confirmed not touched)

- Vercel dashboard settings (Output Directory, env vars) — Dimitri's action, informed by this handoff's finding above.
- Creating the actual Vercel Deploy Hooks or adding `VERCEL_DEPLOY_HOOK_URL_ORDER_UI`/`_INVENTORY_UI` as GitHub secrets — Dimitri's action.
- Dispatching either new deploy workflow.
- Custom `dnit.be` frontend subdomains, preview-deployment CORS, real Google OAuth redirect URIs — all noted as deliberately deferred in `docs/backlog/sprint-26.md`.

## Loop note

Reviewer: the two things most likely to hide a silent (non-build-breaking) defect were checked directly, not inferred — (a) the `fileReplacements` swap, verified via grepping the actual built bundle for the literal prod origin strings, not just trusting "BUILD SUCCESS"; (b) the CORS origin strings, re-grepped character-for-character on integrated `main` against the exact Vercel domains. Also worth a quick independent look: V-3's deliberate deviation from the Pet Giftshop reference workflow (`workflow_dispatch` only, no `push:` trigger) — confirm that's what's actually in the two new files, not assumed from this handoff's description.
