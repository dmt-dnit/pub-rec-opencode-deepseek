# Sprint 38 Track C Review - OIDC Provisioning Hook Fix

Review-Target-Commit: `4117d3a`  
Handoff: `docs/backlog/sprint-38-handoff.md`  
Verdict: ACCEPT

## Findings

No blocking source-level findings in Sprint 38.

## Verified Against Handoff

- **The dead-on-arrival Sprint 36 registration point has been replaced with the correct OIDC one.** `SecurityConfig` now wires `oidcUserService(customOidcUserService)` at [auth-server/src/main/java/be/dnit/authserver/config/SecurityConfig.java](C:/projects/pub-rec-opencode-deepseek/auth-server/src/main/java/be/dnit/authserver/config/SecurityConfig.java:80), and the old `CustomOAuth2UserService` class is gone from the tree. That matches Spring Security's documented split between `userService(...)` for plain OAuth2 and `oidcUserService(...)` for OpenID Connect flows.

- **The new delegate type preserves the Google user-info retrieval path rather than losing behavior.** `CustomOidcUserService` delegates to Spring's built-in `OidcUserService` at [auth-server/src/main/java/be/dnit/authserver/service/CustomOidcUserService.java](C:/projects/pub-rec-opencode-deepseek/auth-server/src/main/java/be/dnit/authserver/service/CustomOidcUserService.java:19) and then applies the same local provisioning rule at [auth-server/src/main/java/be/dnit/authserver/service/CustomOidcUserService.java](C:/projects/pub-rec-opencode-deepseek/auth-server/src/main/java/be/dnit/authserver/service/CustomOidcUserService.java:37). Spring Security documents that `OidcUserService` is the OIDC-capable implementation and that it leverages OAuth2 user-info retrieval underneath, so this delegate swap fixes the extension point without dropping user-attribute loading behavior. Sources: [Spring Security `OidcUserService` API](https://docs.spring.io/spring-security/reference/6.4/api/java/org/springframework/security/oauth2/client/oidc/userinfo/OidcUserService.html), [Spring Security advanced OAuth2 login docs](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/advanced.html).

- **The new test is materially stronger than the old Sprint 36 mock shape.** `CustomOidcUserServiceTest` constructs real OIDC types (`OidcIdToken`, `DefaultOidcUser`, `OidcUserRequest`) at [auth-server/src/test/java/be/dnit/authserver/service/CustomOidcUserServiceTest.java](C:/projects/pub-rec-opencode-deepseek/auth-server/src/test/java/be/dnit/authserver/service/CustomOidcUserServiceTest.java:48), [auth-server/src/test/java/be/dnit/authserver/service/CustomOidcUserServiceTest.java](C:/projects/pub-rec-opencode-deepseek/auth-server/src/test/java/be/dnit/authserver/service/CustomOidcUserServiceTest.java:59), and [auth-server/src/test/java/be/dnit/authserver/service/CustomOidcUserServiceTest.java](C:/projects/pub-rec-opencode-deepseek/auth-server/src/test/java/be/dnit/authserver/service/CustomOidcUserServiceTest.java:80). That directly covers the request shape that was previously missing.

- **The provisioning semantics themselves remain intact.** New users are still created as `PENDING` / `CUSTOMER` with a random encoded password, and existing users are left untouched, at [auth-server/src/main/java/be/dnit/authserver/service/CustomOidcUserService.java](C:/projects/pub-rec-opencode-deepseek/auth-server/src/main/java/be/dnit/authserver/service/CustomOidcUserService.java:37). The rewritten tests assert both non-duplication and non-reset of an existing `ACTIVE` / `ADMIN` user at [auth-server/src/test/java/be/dnit/authserver/service/CustomOidcUserServiceTest.java](C:/projects/pub-rec-opencode-deepseek/auth-server/src/test/java/be/dnit/authserver/service/CustomOidcUserServiceTest.java:106) and [auth-server/src/test/java/be/dnit/authserver/service/CustomOidcUserServiceTest.java](C:/projects/pub-rec-opencode-deepseek/auth-server/src/test/java/be/dnit/authserver/service/CustomOidcUserServiceTest.java:121).

## Residual Checks Not Reproduced Here

- I did not perform the live Google login with a never-before-seen account from this review session.
- I did not exercise the full post-approval second-login path from this review session.

Those remain live verification steps, but the source-level OIDC hook correction is sound and matches the handoff.
