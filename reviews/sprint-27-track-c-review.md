# Sprint 27 Track C Review - Google OAuth Redirect Fix

Review-Target-Commit: `0846bc5`  
Handoff: `docs/backlog/sprint-27-handoff.md`  
Verdict: ACCEPT

## Findings

No blocking source-level findings in Sprint 27.

## Verified Against Handoff

- **The Sprint 26 blocker is fixed at the exact call site Codex rejected.** Both login components now route Google login through the environment-backed auth origin instead of a frontend-relative path, at [order-ui/src/app/pages/login/login.component.ts](C:/projects/pub-rec-opencode-deepseek/order-ui/src/app/pages/login/login.component.ts:74) and [inventory-ui/src/app/pages/login/login.component.ts](C:/projects/pub-rec-opencode-deepseek/inventory-ui/src/app/pages/login/login.component.ts:74). That directly resolves the split-origin failure mode from Sprint 26.

- **Dev-mode behavior remains unchanged.** In both UIs, `environment.authApiBase` is still the empty string in dev at [order-ui/src/environments/environment.ts](C:/projects/pub-rec-opencode-deepseek/order-ui/src/environments/environment.ts:3) and [inventory-ui/src/environments/environment.ts](C:/projects/pub-rec-opencode-deepseek/inventory-ui/src/environments/environment.ts:3), so the resulting runtime string is still `/oauth2/authorization/google` under local `ng serve`.

- **Production-mode behavior now points at the real backend origin.** The production environment files still resolve `authApiBase` to `https://saga-auth.dnit.be` at [order-ui/src/environments/environment.prod.ts](C:/projects/pub-rec-opencode-deepseek/order-ui/src/environments/environment.prod.ts:3) and [inventory-ui/src/environments/environment.prod.ts](C:/projects/pub-rec-opencode-deepseek/inventory-ui/src/environments/environment.prod.ts:3), so the Google button now targets `https://saga-auth.dnit.be/oauth2/authorization/google` instead of staying on the Vercel-hosted SPA.

## Residual Checks Not Reproduced Here

- I could not independently reproduce the Angular build success from this review session because the local frontend dependency state is platform-inconsistent here: `order-ui` still has an `esbuild` Linux-vs-Windows binary mismatch, and `inventory-ui` has no usable local `ng.cmd` in this environment. That is an environment limitation, not a source finding.
- I did not perform the live backend redeploy mentioned in the handoff, so the stale live CORS state remains an operational follow-up, not part of this source review.
