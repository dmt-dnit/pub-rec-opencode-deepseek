# Sprint 27 — Fix Google login redirect for split Vercel/VPS topology

**Track:** C — go-live, Phase 4 follow-up. **Origin:** Codex Sprint 26 REJECT (`reviews/sprint-26-track-c-review.md`, P1).

## Why this sprint exists

Codex's Sprint 26 review found one genuine, confirmed regression: both UIs' "Login with Google" button does `window.location.href = '/oauth2/authorization/google'` — a frontend-relative path. That worked under `ng serve`'s dev proxy (same-origin, proxied to `localhost:9000`) and would have kept working on a same-origin reverse-proxy deploy, but breaks under Sprint 26's actual split-origin Vercel/VPS topology: the request resolves against the Vercel frontend's own origin, and both UIs' new `vercel.json` (Sprint 26 V-1) rewrites every unmatched path back to `index.html`. So the button never reaches `saga-auth.dnit.be` at all — it just reloads the SPA.

**Coordinator brief error, not an implementer miss:** Sprint 26's V-1 brief enumerated the REST services (`auth.service.ts`, `order.service.ts`/`inventory.service.ts`) and `websocket.service.ts` as the places needing `environment`-based origins, but never mentioned `login.component.ts`'s OAuth redirect. The implementer did exactly what was briefed; the brief was incomplete. Confirmed directly — coordinator re-read both files (`order-ui/src/app/pages/login/login.component.ts:73`, `inventory-ui/src/app/pages/login/login.component.ts:73`) and both are byte-identical in shape.

Codex's review otherwise found the rest of Sprint 26 clean by source inspection: env files, `angular.json` config, CORS allow-lists, and the two deploy-hook workflows all confirmed correct. This is a single, narrow fix.

## What to change

Both `order-ui/src/app/pages/login/login.component.ts` and `inventory-ui/src/app/pages/login/login.component.ts`:

1. Add the environment import (same pattern already used in this sprint's other service files): `import { environment } from '../../../environments/environment';` — check the actual relative path from `src/app/pages/login/` to `src/environments/` (one level deeper than `src/app/services/`, adjust the `../` count accordingly).
2. Change line 73 from:
   ```ts
   window.location.href = '/oauth2/authorization/google';
   ```
   to:
   ```ts
   window.location.href = `${environment.authApiBase}/oauth2/authorization/google`;
   ```

In dev, `environment.authApiBase` is `''` (Sprint 26 V-1), so the resulting string is byte-identical to today's — no dev behavior change. In production, it resolves to `https://saga-auth.dnit.be/oauth2/authorization/google`, reaching the real backend instead of the Vercel host's own `index.html` fallback.

## Explicitly out of scope

- **This does not make production Google OAuth fully functional end-to-end.** `auth-server`'s OAuth2 success handler still hardcodes the redirect back to `http://localhost:4200/login?oauth2=success` (`auth-server/src/main/java/be/dnit/authserver/config/SecurityConfig.java`) and there are no real Google OAuth credentials configured anywhere in this repo (`docs/security/secrets-and-test-data.md`'s no-real-secrets invariant). That's Phase 6 territory, gated on the Phase 2 secrets-policy decision, and stays deferred. This sprint fixes exactly what Codex flagged — the button now reaches the real backend origin instead of silently failing at the frontend — not the full OAuth loop.
- No change to `auth.service.ts`, `order.service.ts`, `inventory.service.ts`, `websocket.service.ts`, `vercel.json`, `angular.json`, CORS configs, or the deploy-hook workflows — Codex confirmed those are all correct as-is.

## Acceptance criteria (observable outcomes, show real output)

1. `git diff` of both `login.component.ts` files shown in the report.
2. `cd order-ui && npx ng build` (default = production config) — **BUILD SUCCESS**, then `grep "saga-auth.dnit.be" order-ui/dist/browser/*.js` still matches (unchanged from Sprint 26, now also reachable from the OAuth button).
3. Same for `inventory-ui`.
4. Confirm dev behavior unchanged: the `environment.ts` (dev) files are untouched, so `environment.authApiBase` stays `''` — state this explicitly rather than just asserting no dev regression.
5. `git status --short` clean after commit.

## Loop note

This is a one-finding, one-fix round. Reviewer: re-check exactly the file:line Codex cited (`login.component.ts:73` in both UIs) resolves to the environment-based URL, and that no other relative-path OAuth/redirect assumption was missed elsewhere in either UI (a quick grep for `/oauth2/` and `window.location.href` across both `src/app` trees is enough — Codex's review didn't flag any other instance, and a coordinator grep before writing this brief found none either).
