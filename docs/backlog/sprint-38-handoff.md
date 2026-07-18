# Sprint 38 Handoff — fix Google OIDC login bypassing user provisioning

**Coordinator:** Claude Code. **Implementer:** opencode+DeepSeek (worktree). **Date:** 2026-07-18.
Review-Target-Commit: 4117d3a
**Severity: live-blocking, found and fixed same day.**

## What was done

Full root-cause writeup: `docs/backlog/sprint-38.md`. Short version: Google's login
includes the `openid` scope and returns an ID token, so Spring Security routes it
through its own internal `OidcUserService`, completely bypassing the plain
`OAuth2UserService` Sprint 36 registered via `.userService(...)` — that extension point
only applies to non-OIDC providers. New Google accounts got a Whitelabel Error Page
(`IllegalStateException: OAuth2 user not found in local DB`) because
`CustomOAuth2UserService.loadUser()` was silently never invoked at all. Confirmed via
a real production stack trace and a direct check of the live admin approval page (no
`PENDING` row appeared for the test account).

Fix: `CustomOAuth2UserService` replaced with `CustomOidcUserService implements
OAuth2UserService<OidcUserRequest, OidcUser>`, delegating to Spring's real
`OidcUserService`, wired via `.oidcUserService(...)` instead of `.userService(...)`.
Same provisioning logic (find-or-create `PENDING`/`CUSTOMER`), correct extension point.
Old dead class and its test deleted. New `CustomOidcUserServiceTest` builds a
genuinely realistic `OidcIdToken`/`DefaultOidcUser`/`OidcUserRequest` — the exact shape
Spring Security constructs for a real Google login, which is precisely what Sprint 36's
tests got wrong (they mocked a generic `OAuth2User`, never an OIDC one) and precisely
why this shipped without being caught.

## Coordinator verification

- Read the full diff directly before integrating: exactly the expected shape — old
  class deleted, new one created, `SecurityConfig` updated, test renamed and rewritten
  with realistic OIDC types.
- `./mvnw clean verify` on `auth-server`, independently, both in the isolated worktree
  and again on integrated `main`: **BUILD SUCCESS, Tests run: 9, Failures: 0, Errors: 0**
  both times.

## Explicitly out of scope (unchanged from the brief)

- No change to `OAuth2SuccessHandler`'s JWT-issuance/status-branching logic — already
  correct and live-verified for the `ACTIVE` case before this sprint.
- No change to `application.yml`'s OAuth2 client registration.

## What's still needed (Dimitri, live ops)

Redeploy `auth-server` with this commit, then log in with a genuinely new Google
account. Expected: no Whitelabel error, lands on `?oauth2=pending`, and the account
shows up on the admin approval page as `PENDING`. Approve it, then confirm a second
login from that same account logs straight in via the `ACTIVE` branch.

## What Codex should check independently

Confirm `OidcUserService` (Spring's built-in delegate) genuinely performs the same
Google user-info retrieval as the previously-used `DefaultOAuth2UserService` did (plus
ID-token-specific handling) — i.e., that switching the delegate type doesn't lose any
behavior beyond fixing the registration-point bug.
