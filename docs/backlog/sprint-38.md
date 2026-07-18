# Sprint 38 — fix Google OIDC login bypassing user provisioning entirely

**Track:** C — go-live, Phase 6 follow-up. **Date:** 2026-07-18.
**Severity: live-blocking.** Confirmed on the real production site: a brand-new Google
account gets a Whitelabel Error Page after consenting, and no `UserEntity` row is ever
created — new-user Google signup is completely broken.

## Root cause, confirmed via a real stack trace + a real negative check

Live `journalctl` output (`m1st3ryme@gmail.com`, a genuinely new Google account) shows:
```
java.lang.IllegalStateException: OAuth2 user not found in local DB: m1st3ryme@gmail.com
	at be.dnit.authserver.config.OAuth2SuccessHandler.onAuthenticationSuccess(OAuth2SuccessHandler.java:37)
```
and Dimitri confirmed directly on the live admin approval page
(`https://pub-rec-saga-orders-ui.vercel.app/admin`) that no such user exists — only the
5 pre-existing accounts show. So `CustomOAuth2UserService.loadUser()`'s create-branch
never ran at all for this login.

**Why:** Google's login here includes the `openid` scope and returns an ID token
(confirmed in the log — `NimbusJwtDecoder` fetches `https://www.googleapis.com/oauth2/v3/certs`
to verify it), so Spring Security treats this as an **OIDC login**, not a plain OAuth2
one. `CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User>`,
wired via `.userInfoEndpoint(u -> u.userService(customOAuth2UserService))` in
`SecurityConfig.java:81` — but **that extension point is only consulted for non-OIDC
providers**. For an OIDC provider, Spring Security substitutes its own internal
`OidcUserService` and never calls the one we registered. This is why:
- `CustomOAuth2UserService.loadUser()` never throws (it's simply never invoked — no
  stack trace for it anywhere in the log, only for the success handler downstream).
- A brand-new email never gets a `UserEntity` row.
- Dimitri's own Google login "worked" only because his account **already existed**
  (normal registration + later admin promotion) — `OAuth2SuccessHandler`'s own
  `findByEmail` lookup found that pre-existing row, entirely independent of whether the
  custom user service ever ran.

Sprint 36's automated tests (`CustomOAuth2UserServiceTest`) mocked a generic
`OAuth2UserRequest`/`DefaultOAuth2User` — never an OIDC-shaped request — which is
exactly why this gap wasn't caught before reaching production.

## What to change

`auth-server/src/main/java/be/dnit/authserver/service/CustomOAuth2UserService.java`:
convert this into an OIDC-flavored user service. Suggested shape (rename the class to
`CustomOidcUserService` for clarity, since Google is this project's only registered
provider and it is inherently OIDC):

```java
package be.dnit.authserver.service;

import be.dnit.authserver.model.UserEntity;
import be.dnit.authserver.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private OAuth2UserService<OidcUserRequest, OidcUser> delegate = new OidcUserService();

    public CustomOidcUserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    void setDelegate(OAuth2UserService<OidcUserRequest, OidcUser> delegate) {
        this.delegate = delegate;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = delegate.loadUser(userRequest);

        String email = oidcUser.getAttribute("email");
        String name = oidcUser.getAttribute("name");

        userRepository.findByEmail(email).orElseGet(() -> userRepository.save(new UserEntity(
                email,
                passwordEncoder.encode(UUID.randomUUID().toString()),
                name != null ? name : email,
                UserEntity.Role.CUSTOMER,
                UserEntity.Status.PENDING
        )));

        return oidcUser;
    }
}
```

`SecurityConfig.java`: replace the `.userService(customOAuth2UserService)` registration
with `.oidcUserService(customOidcUserService)`:
```java
.oauth2Login(oauth2 -> oauth2
    .userInfoEndpoint(u -> u.oidcUserService(customOidcUserService))
    .successHandler(oauth2SuccessHandler)
)
```
Update the field/constructor injection accordingly (rename
`customOAuth2UserService` → `customOidcUserService`, type `CustomOidcUserService`).

`OAuth2SuccessHandler.java`: no change needed — it already just does
`authentication.getPrincipal()` cast to a principal with `.getAttribute("email")`, and
`OidcUser` (like `OAuth2User`) supports `getAttribute(...)`. Confirm this cast/usage
still compiles and behaves correctly against an `OidcUser` principal (it implements
`OAuth2User`, so `(OAuth2User) authentication.getPrincipal()` should still work) — verify
directly, don't assume.

**Delete the old plain-`OAuth2UserService` class and its test**, or repurpose them —
don't leave a dead, never-invoked `CustomOAuth2UserService` around once
`CustomOidcUserService` replaces it; this project's own dead-code hygiene should apply
here given how this exact kind of leftover unused code just caused real confusion.

## The critical thing this sprint must get right in its own tests

Sprint 36's tests passed while this bug shipped to production, because they mocked a
generic `OAuth2UserRequest`/`DefaultOAuth2User` shape instead of the real OIDC shape
Google actually sends. **The new test for `CustomOidcUserService` must construct a
realistic `OidcUserRequest`** (needs a real `OidcIdToken` — construct one directly with
`OidcIdToken.withTokenValue(...)` and the standard claims map, `sub`/`email`/`name`/etc.
— and a `ClientRegistration` with `scopes("openid", "profile", "email")`, matching the
real registration) **and a `DefaultOidcUser`** (not `DefaultOAuth2User`) as the
delegate's mocked return value. This is the one thing that would have caught the actual
bug — get it right this time, not a shape that happens to compile but doesn't match
what Spring Security really constructs for an OIDC login.

## Explicitly out of scope

- No change to the JWT-issuance/status-branching logic in `OAuth2SuccessHandler` beyond
  confirming the principal cast still works — that logic itself was already correct
  and live-verified for the `ACTIVE` case.
- No change to `application.yml`'s OAuth2 client registration.

## Acceptance criteria (show real output, don't assert "Pass")

1. New test(s) using a genuinely OIDC-shaped `OidcUserRequest`/`DefaultOidcUser` (see
   above) — proves: (a) a first-time login creates a `PENDING`/`CUSTOMER` row, (b) a
   repeat login for the same email doesn't duplicate or reset an existing user's
   role/status. Reuse/adapt the existing `OAuth2SuccessHandlerTest` as-is if it still
   applies (it tests the handler in isolation, not the user service, so it may not need
   changes — confirm either way).
2. `./mvnw clean verify` — BUILD SUCCESS, 0 failures/errors.
3. Show the actual `git diff`.
4. `git status --short` clean after commit.

## Live verification (Dimitri, after redeploy — not part of automated criteria)

Log in with a genuinely new Google account (not previously in the system). Confirm: no
Whitelabel error, redirect lands on `?oauth2=pending`, and the account now shows up on
the admin approval page as `PENDING`. Then approve it and confirm a second login from
that same account logs straight in (`ACTIVE` branch).
