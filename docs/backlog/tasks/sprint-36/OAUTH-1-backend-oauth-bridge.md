# OAUTH-1 — backend Google→JWT bridge (`auth-server`)

**Sprint:** 36. **Track:** C — go-live, Phase 6. **Implementer:** opencode+DeepSeek (worktree).

## Why

Confirmed by reading the code (not assumed): `SecurityConfig.java`'s current
`oauth2Login` success handler never issues a JWT and there is no custom
`OAuth2UserService` anywhere in this module — a Google login today never creates a
`UserEntity`, never gets a role, and never gets checked against the app's
`PENDING`/`ACTIVE` gate. This task builds that bridge.

## Current state (read directly, re-verify if this has moved)

`auth-server/src/main/java/be/dnit/authserver/config/SecurityConfig.java`, inside
`securityFilterChain(...)`:
```java
.oauth2Login(oauth2 -> oauth2
    .successHandler((request, response, authentication) -> {
        response.sendRedirect("http://localhost:4200/login?oauth2=success");
    })
)
```

`auth-server/src/main/java/be/dnit/authserver/service/JwtService.java` already has
`generateToken(UserEntity user)` — reuse this exactly, don't reimplement JWT minting.

`auth-server/src/main/java/be/dnit/authserver/model/UserEntity.java`: `password` column
is `@Column(nullable = false)` — a Google-provisioned user still needs *some* password
value in the row (never usable to log in with, since it's random and never told to the
user). Follow the same shape `AuthController.register(...)` already uses:
`passwordEncoder.encode(request.password())` — just encode a random value instead of a
user-supplied one.

`auth-server/src/main/resources/application.yml` already has the OAuth2 client
registration block with safe placeholder defaults — don't touch that part:
```yaml
security:
  oauth2:
    client:
      registration:
        google:
          client-id: ${GOOGLE_CLIENT_ID:placeholder}
          client-secret: ${GOOGLE_CLIENT_SECRET:placeholder}
          scope: openid,profile,email
```

## What to build

1. **New config property** for the frontend redirect base URL (mirrors the
   `app.jwt.key-path` / `APP_JWT_KEY_PATH` pattern from Sprint 32 — env-var driven,
   safe local default):
   ```yaml
   app:
     oauth2:
       success-redirect-base-url: ${APP_OAUTH2_REDIRECT_BASE:http://localhost:4200}
   ```

2. **New `CustomOAuth2UserService`** implementing
   `OAuth2UserService<OAuth2UserRequest, OAuth2User>`. Delegate the actual Google
   fetch to a `DefaultOAuth2UserService` instance, then:
   - Read `email` and `name` from the returned `OAuth2User`'s attributes (standard
     OIDC claims for Google: `email`, `name`).
   - Look up `UserEntity` by email via the existing `UserRepository`.
   - If not found: create one with `role=CUSTOMER`, `status=PENDING`, and a password
     of `passwordEncoder.encode(UUID.randomUUID().toString())` (never usable, just
     satisfies the NOT NULL column — same encoding call shape as
     `AuthController.register`).
   - If found: leave the existing row untouched (don't silently promote/reset an
     existing user's role or status just because they logged in via Google once).
   - Return the `OAuth2User` unmodified (or wrap it if you need to carry the resolved
     `UserEntity` forward to the success handler — either approach is fine, whichever
     is less code; the success handler re-looks-up by email if simplest).

3. **Rewrite the success handler** to replace the one-liner. Logic:
   - Extract the authenticated email (from `authentication.getPrincipal()`, an
     `OAuth2User` — `.getAttribute("email")`).
   - Look up the `UserEntity` (guaranteed to exist by step 2's `CustomOAuth2UserService`
     having just run first in the same request).
   - Branch on `status`:
     - `ACTIVE` → `jwtService.generateToken(user)`, redirect to
       `{success-redirect-base-url}/login?oauth2=success&token=<jwt>`.
     - `PENDING` → redirect to `{success-redirect-base-url}/login?oauth2=pending`, no
       token.
     - anything else (`DISABLED`) → redirect to
       `{success-redirect-base-url}/login?oauth2=error`, no token.
   - Wire the new `CustomOAuth2UserService` into `SecurityConfig`'s `.oauth2Login(...)`
     via `.userInfoEndpoint(u -> u.userService(customOAuth2UserService))`.

## Explicitly out of scope

- Do not touch `client-id`/`client-secret` config — placeholder defaults stay exactly
  as they are; real credentials are a separate, later, Dimitri-driven step.
- Do not change `AuthController`, email/password login, or registration.
- Do not change the `issuer("http://localhost:9000")` literal in `JwtService.java` —
  confirmed cosmetic (resource servers use `jwk-set-uri`, never validate `iss`).

## Acceptance criteria (show real output, don't assert "Pass")

1. **New test(s)** exercising the provisioning/status-branching logic without a real
   Google round-trip (not possible with placeholder credentials — say so explicitly if
   you considered and ruled out any other approach). Suggested shape: a
   `@SpringBootTest` or narrower slice test that constructs a mock `OAuth2User`
   (Spring Security provides `DefaultOAuth2User` for exactly this — construct one
   directly with a fake `email`/`name` attribute map) and calls
   `CustomOAuth2UserService.loadUser(...)` with a stubbed/mocked
   `OAuth2UserRequest`+delegate, then asserts: (a) first call creates a `PENDING`
   `CUSTOMER` row, (b) a second call for the same email does not create a duplicate
   or reset an already-`ACTIVE` user's role/status. Also test the success handler's
   branching directly if it's structured as a testable class/method rather than an
   inline lambda (prefer extracting it to a named class for this reason — easier to
   test, easier for the next reader).
2. `./mvnw clean verify` — BUILD SUCCESS, 0 failures/errors, including the new tests.
3. Confirm the app still starts locally with the placeholder credentials (it does
   today — this must not regress): `./mvnw spring-boot:run`, confirm no startup
   exception, `Ctrl+C` once confirmed up.
4. Show the actual `git diff`.
5. `git status --short` clean after commit.
