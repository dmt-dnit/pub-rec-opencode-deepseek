# Sprint 36 Track C Review - Google OAuth Backend and Frontend Bridge

Review-Target-Commit: `b0c271e`  
Handoff: `docs/backlog/sprint-36-handoff.md`  
Verdict: ACCEPT

## Findings

No blocking source-level findings in Sprint 36.

## Verified Against Handoff

- **The frontend callback bridge is present and correctly scoped to `order-ui`.** `loginWithToken()` now stores the JWT and reuses the hardened `/me` bootstrap path at [order-ui/src/app/services/auth.service.ts](C:/projects/pub-rec-opencode-deepseek/order-ui/src/app/services/auth.service.ts:54). The login page consumes `oauth2=success`, `oauth2=pending`, and `oauth2=error` query params at [order-ui/src/app/pages/login/login.component.ts](C:/projects/pub-rec-opencode-deepseek/order-ui/src/app/pages/login/login.component.ts:66). `inventory-ui` no longer exposes the Google login entrypoint at [inventory-ui/src/app/pages/login/login.component.ts](C:/projects/pub-rec-opencode-deepseek/inventory-ui/src/app/pages/login/login.component.ts:15), which matches the stated one-UI scope.

- **The Sprint 36 Spring circular-dependency fixes are legitimate, not papering over a deeper flaw.** `PasswordEncoder` is now provided from a separate configuration class at [auth-server/src/main/java/be/dnit/authserver/config/PasswordConfig.java](C:/projects/pub-rec-opencode-deepseek/auth-server/src/main/java/be/dnit/authserver/config/PasswordConfig.java:8), which removes the constructor path that would have required `SecurityConfig` to finish instantiating before exposing the encoder bean. `JwtService` now lazily receives `JwtEncoder` at [auth-server/src/main/java/be/dnit/authserver/service/JwtService.java](C:/projects/pub-rec-opencode-deepseek/auth-server/src/main/java/be/dnit/authserver/service/JwtService.java:17), which is the standard Spring way to break a bean graph cycle when the dependency is only needed at token-generation time.

- **The success handler contract matches the handoff.** `OAuth2SuccessHandler` branches on persisted user status and only mints a JWT for `ACTIVE` accounts at [auth-server/src/main/java/be/dnit/authserver/config/OAuth2SuccessHandler.java](C:/projects/pub-rec-opencode-deepseek/auth-server/src/main/java/be/dnit/authserver/config/OAuth2SuccessHandler.java:40). The expected redirect shapes are directly covered by tests at [auth-server/src/test/java/be/dnit/authserver/config/OAuth2SuccessHandlerTest.java](C:/projects/pub-rec-opencode-deepseek/auth-server/src/test/java/be/dnit/authserver/config/OAuth2SuccessHandlerTest.java:42).

- **The "missing local row" failure mode is strict but coherent for Sprint 36's non-OIDC path.** `OAuth2SuccessHandler` throws if the authenticated email has no local `UserEntity` at [auth-server/src/main/java/be/dnit/authserver/config/OAuth2SuccessHandler.java](C:/projects/pub-rec-opencode-deepseek/auth-server/src/main/java/be/dnit/authserver/config/OAuth2SuccessHandler.java:36). For the Sprint 36 design, that is acceptable because the provisioning service is intended to run first in the same authentication flow. The later Sprint 38 fix changed the registration point for OIDC providers; it did not invalidate the handler's assumption once the right delegate is wired.

## Dependency / Framework Check

- Spring Security's `oauth2Login().userInfoEndpoint()` exposes separate hooks for `userService(...)` and `oidcUserService(...)`, and the presence of `openid` drives OIDC-specific processing. That makes the Sprint 36 design internally consistent for plain OAuth2 providers but incomplete for Google's OIDC flow, which Sprint 38 correctly fixes. Sources: [Spring Security advanced OAuth2 login docs](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/advanced.html), [Spring Security OAuth2/OIDC overview](https://docs.enterprise.spring.io/spring-security/reference/6.2/servlet/oauth2/index.html).

## Residual Checks Not Reproduced Here

- I did not execute a real Google round-trip with live client credentials from this review session.
- I did not run a browser-level check of the callback page rendering for `success`, `pending`, and `error` query states.

Those are still live-verification steps, but they are not blockers to accepting the committed source for Sprint 36.
