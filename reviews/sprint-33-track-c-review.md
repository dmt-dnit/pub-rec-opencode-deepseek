# Sprint 33 Track C Review - NG0200 Circular-DI Auth Token Loss

Review-Target-Commit: `380792f`  
Handoff: `docs/backlog/sprint-33-handoff.md`  
Verdict: ACCEPT

## Findings

No blocking source-level findings in Sprint 33.

## Verified Against Handoff

- **The circular DI edge is removed in the exact place that caused it.** Both functional interceptors now read the token directly from `localStorage` via the exported shared key, instead of calling `inject(AuthService)` during request handling at [order-ui/src/app/interceptors/auth.interceptor.ts](C:/projects/pub-rec-opencode-deepseek/order-ui/src/app/interceptors/auth.interceptor.ts:1) and [inventory-ui/src/app/interceptors/auth.interceptor.ts](C:/projects/pub-rec-opencode-deepseek/inventory-ui/src/app/interceptors/auth.interceptor.ts:1). That matches Angular's documented model for `NG0200`: a DI cycle exists when a dependency path loops back to the service being constructed, and functional interceptors do run in an injection context where `inject(...)` participates in DI resolution. The handoff's root-cause analysis is therefore credible, not just speculative. Source: [Angular NG0200 docs](https://angular.dev/errors/NG0200), [Angular interceptor docs](https://angular.dev/guide/http/interceptors).

- **The auth token key is now defined once per app and imported at the read site.** `AUTH_TOKEN_KEY` is exported from each app's `auth.service.ts` at [order-ui/src/app/services/auth.service.ts](C:/projects/pub-rec-opencode-deepseek/order-ui/src/app/services/auth.service.ts:8) and [inventory-ui/src/app/services/auth.service.ts](C:/projects/pub-rec-opencode-deepseek/inventory-ui/src/app/services/auth.service.ts:8), and the interceptors import that symbol instead of re-declaring the string literal at [order-ui/src/app/interceptors/auth.interceptor.ts](C:/projects/pub-rec-opencode-deepseek/order-ui/src/app/interceptors/auth.interceptor.ts:2) and [inventory-ui/src/app/interceptors/auth.interceptor.ts](C:/projects/pub-rec-opencode-deepseek/inventory-ui/src/app/interceptors/auth.interceptor.ts:2). I did not find any remaining duplicated token-key literal in TypeScript sources.

- **The narrower logout rule matches the backend's actual auth failure shape.** `fetchMe()` now logs out only on `HttpErrorResponse` with `401` or `403` at [order-ui/src/app/services/auth.service.ts](C:/projects/pub-rec-opencode-deepseek/order-ui/src/app/services/auth.service.ts:37) and [inventory-ui/src/app/services/auth.service.ts](C:/projects/pub-rec-opencode-deepseek/inventory-ui/src/app/services/auth.service.ts:37). On the backend, `/api/auth/me` is an authenticated endpoint at [auth-server/src/main/java/be/dnit/authserver/controller/AuthController.java](C:/projects/pub-rec-opencode-deepseek/auth-server/src/main/java/be/dnit/authserver/controller/AuthController.java:66), protected by the resource-server chain at [auth-server/src/main/java/be/dnit/authserver/config/SecurityConfig.java](C:/projects/pub-rec-opencode-deepseek/auth-server/src/main/java/be/dnit/authserver/config/SecurityConfig.java:67). Given that shape, treating `401/403` as "session invalid" and leaving network / transient / unexpected errors intact is the correct behavior change.

- **Guard changes are diagnostic only and do not alter the existing pass/fail policy.** The added `console.warn(...)` calls in [order-ui/src/app/guards/auth.guard.ts](C:/projects/pub-rec-opencode-deepseek/order-ui/src/app/guards/auth.guard.ts:8), [order-ui/src/app/guards/admin.guard.ts](C:/projects/pub-rec-opencode-deepseek/order-ui/src/app/guards/admin.guard.ts:9), and the matching inventory guard at [inventory-ui/src/app/guards/auth.guard.ts](C:/projects/pub-rec-opencode-deepseek/inventory-ui/src/app/guards/auth.guard.ts:8) explain redirect reasons without widening or tightening route access. That matches the stated scope.

## Residual Checks Not Reproduced Here

- I did not run a post-deploy browser hard-refresh test against the live Vercel builds from this review session.
- I did not add or run a frontend test that explicitly reproduces the constructor-time `/me` call plus interceptor re-entry path; the sprint remains validated by source inspection and coordinator builds rather than a regression spec.

Those are useful follow-ups, but they are not blockers to accepting this fix.
