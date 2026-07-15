# Sprint 27 — Handoff to Codex

**Coordinator:** Claude Code. **Date:** 2026-07-15.
**Task:** fix the Google OAuth login redirect regression Codex found in Sprint 26 (`reviews/sprint-26-track-c-review.md`, P1).
**Implementer:** opencode+DeepSeek, isolated worktree.
**Merge-gate tier:** coordinator-supervised, pushed direct to `main` per the Sprint 21 policy.
Review-Target-Commit: 0846bc5

## Why this sprint exists

Codex's Sprint 26 review (genuine `FRESH REJECT` per `scripts/verify-review.sh 26`) found that both UIs' "Login with Google" button used a frontend-relative path (`window.location.href = '/oauth2/authorization/google'`), which silently fails under the new split Vercel/VPS topology — the request resolves against the Vercel host itself, and both UIs' `vercel.json` SPA fallback rewrites it back to `index.html` instead of ever reaching `saga-auth.dnit.be`. This was a **coordinator brief error**: Sprint 26's V-1 brief enumerated the REST services and WebSocket service as needing environment-based origins but never mentioned this login entrypoint. Everything else in Codex's Sprint 26 review checked out clean (env files, CORS allow-lists, deploy workflows, all confirmed correct by source inspection).

## What was done (`0846bc5`)

Both `order-ui/src/app/pages/login/login.component.ts` and `inventory-ui/src/app/pages/login/login.component.ts`:
- Added `import { environment } from '../../../environments/environment';`
- Changed `window.location.href = '/oauth2/authorization/google';` to `` window.location.href = `${environment.authApiBase}/oauth2/authorization/google`; ``

In dev, `environment.authApiBase` is `''` (unchanged from Sprint 26), so the resulting string is byte-identical to before — no dev behavior change. In production it resolves to `https://saga-auth.dnit.be/oauth2/authorization/google`.

**Explicitly not addressed, by design** (see `docs/backlog/sprint-27.md`'s "Explicitly out of scope"): this does not make production Google OAuth fully functional end-to-end. `auth-server`'s OAuth2 success handler still hardcodes its redirect to `http://localhost:4200/login?oauth2=success`, and no real Google OAuth credentials exist anywhere in this repo. That's Phase 6 territory, gated on the Phase 2 secrets-policy decision. This fix corrects exactly what Codex flagged — the button now reaches the real backend origin instead of silently failing at the frontend.

## Coordinator verification

- Diff reviewed directly: both files changed identically, matches the brief exactly, no other files touched.
- Confirmed via grep before scoping the brief that no other `/oauth2/` or relative-redirect instance exists in either UI's `src/app` tree (`grep -rn "oauth2\|window.location.href"` — exactly one hit per UI, both now fixed).
- `cd order-ui && npx ng build` (production config) — BUILD SUCCESS; `grep -l "saga-auth.dnit.be" order-ui/dist/browser/*.js` matches `main.js`.
- `cd inventory-ui && npx ng build` — BUILD SUCCESS; same grep matches `main.js`.
- `git status --short` clean on `main` after the cherry-pick.

## A separate, non-code finding surfaced while live-testing this fix (flagged for Dimitri, not part of this sprint's scope)

While verifying Sprint 26/27 live, direct `curl` probes against `https://saga-auth.dnit.be` show the **live backend has not yet been redeployed** with Sprint 26's V-2 CORS change: an `Origin: http://localhost:4200` request still gets `200`, but `Origin: https://pub-rec-saga-orders-ui.vercel.app` gets `403 Invalid CORS request` (Spring's own CORS-rejection message). The code on `main` is correct; the running jars on `dnit-vps` are just stale. This needs a manual `workflow_dispatch` of `deploy-auth.yml`/`deploy-order.yml`/`deploy-inventory.yml` before the frontend can actually talk to the backend in production — not something this sprint or an automated agent should trigger unprompted against a live public service.

## Loop note

Reviewer: re-check exactly the file:line you cited in the Sprint 26 round-1 review now resolves to the environment-based URL in both UIs, and that the dev-mode behavior (empty `authApiBase`) is genuinely unchanged.
