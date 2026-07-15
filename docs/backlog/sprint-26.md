# Sprint 26 — Track C Phase 4: Vercel frontend deploy prep

**Track:** C — go-live. **Theme:** wire `order-ui`/`inventory-ui` to be deployable as
static SPAs on Vercel, pointed at the live `dnit-vps` backend subdomains that Phase 3/3.5
already stood up.

## Why this sprint exists

Dimitri has already created the two Vercel projects in the dashboard (manual step, not
code):

| UI | Vercel domain |
|---|---|
| `order-ui` | `pub-rec-saga-orders-ui.vercel.app` |
| `inventory-ui` | `pub-rec-saga-inventory-ui.vercel.app` |

Both UIs currently only work behind `ng serve`'s dev proxy (`proxy.conf.json`), which
rewrites relative paths (`/api/auth/**`, `/api/orders/**`, `/ws/**`, etc.) to
`localhost:9000`/`8080`/`8081`. A static Vercel deployment has no dev proxy, so the app
needs to call the real backend origins directly — this sprint wires that up.

**Sequencing note:** Sprint 25 (package rename) was scoped as "before Phase 4 resumes"
but has not gone through Codex review — Dimitri made an explicit call (2026-07-15) to
skip that review and proceed straight to this sprint. Logged here, not silently dropped;
see `docs/backlog/sprint-25-handoff.md` for what shipped (rename only, already
independently rebuilt/verified by the coordinator).

## Ground truth this brief is built on (verified directly, not assumed)

- Both UIs' `angular.json` currently has **no `production` configuration and no
  `fileReplacements`** — a bare single `build` target. Adding the standard Angular
  environment-file-swap pattern is new work, not an existing mechanism to hook into.
- Confirmed by an actual `ng build` run in this session (order-ui, Angular 22 /
  `@angular-devkit/build-angular:application` builder, no SSR configured): output lands
  at **`dist/browser/`**, not `dist/`. This matters for the Vercel project's *Output
  Directory* setting (see V-1's acceptance criteria) — a wrong setting here serves a 404
  on every route, so this needs to be checked, not guessed.
- All API calls in both UIs use **relative paths** today: `order-ui/src/app/services/auth.service.ts:9` (`/api/auth`), `order-ui/src/app/services/order.service.ts:8` (`/api/orders`), `inventory-ui/src/app/services/auth.service.ts:9` (`/api/auth`), `inventory-ui/src/app/services/inventory.service.ts:8` (`/api/inventory`). Both `websocket.service.ts` files build the broker URL from `window.location` (`order-ui/src/app/services/websocket.service.ts:13-15`, same in inventory-ui).
- Neither UI has a unit test suite configured (`angular.json` has no `test` architect
  target, no `.spec.ts` files exist) — verification for this sprint is build-output-based
  (grep the built bundle), not test-suite-based.
- CORS origins today (`*/config/SecurityConfig.java`, all three Spring services):
  `auth-server` allows `localhost:4200`+`4201`; `order-service` allows only
  `localhost:4200`; `inventory-service` allows only `localhost:4201`. No existing test
  asserts on these lists (`grep -rl "corsConfigurationSource\|AllowedOrigins"
  */src/test` returns empty), so this is safe to extend without touching tests.
- Reference pattern for the deploy-hook workflow: `dmt-dnit/petgiftshop`'s
  `.github/workflows/vercel-deploy.yml` (fetched live via `gh api`, not reconstructed
  from memory) — a single `curl -fsS -X POST "$VERCEL_DEPLOY_HOOK_URL"` step gated on a
  secret-presence check. This repo's existing backend deploy workflows
  (`deploy-auth.yml`/`deploy-order.yml`/`deploy-inventory.yml`) use `workflow_dispatch`
  only (manual trigger), not push-triggered — V-3 follows that same manual-control
  convention rather than Pet Giftshop's push-on-`master` trigger, since this is a demo
  app where Dimitri wants to control exactly when the public instance changes (same
  reasoning already applied to the backend workflows in Sprint 22).

## Task list

1. **V-1 — Vercel deploy prep for order-ui + inventory-ui** (env-based API base URL +
   `vercel.json` SPA fallback). Frontend-only, apply the same shape to both UIs.
2. **V-2 — CORS allow-list update** on all three Spring services for the two Vercel
   origins. Backend-only.
3. **V-3 — GitHub Actions Vercel deploy-hook workflows** for both UIs, `workflow_dispatch`
   manual trigger, mirroring the existing backend deploy workflow shape.

V-1 and V-2 are independent (frontend vs. backend) and can run in parallel worktrees.
V-3 depends on nothing in this sprint code-wise, but is only useful once V-1 has shipped
a working build — sequence it last.

## Explicitly out of scope this sprint

- Custom `dnit.be` subdomains for the frontends (the roadmap mentions this as a
  possibility but Dimitri hasn't set that up — this sprint targets the `*.vercel.app`
  domains that actually exist).
- Preview-deployment CORS (Vercel's per-branch/PR preview URLs) — only the two
  production `*.vercel.app` origins are whitelisted. Note as a caveat if it comes up,
  not a blocker.
- Real Google OAuth redirect URIs (`auth-server`'s `oauth2Login` success handler is still
  hardcoded to `http://localhost:4200/login?oauth2=success` — that's Phase 6 territory,
  gated separately on the secrets-policy decision).
- Actually triggering a live Vercel deploy or touching Vercel project settings beyond
  what's needed to report the Output Directory finding in V-1 — this sprint produces
  code/config artifacts; Dimitri applies the Vercel dashboard settings and runs the first
  real deploy himself, same artifact/live-apply split used for Phase 3.

## Loop note

Reviewer: the two things most likely to hide a silent (not build-breaking) defect are
(a) whether `fileReplacements` actually swapped the environment file in the production
build — verify by grepping the built JS for the literal prod origin string, don't just
trust "BUILD SUCCESS" — and (b) whether the CORS origin strings exactly match the
Vercel domains character-for-character (a trailing slash or wrong scheme silently
breaks cross-origin requests with no compile-time signal).
