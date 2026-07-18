# Sprint 37 — fix OAuth2 redirect_uri scheme behind the reverse proxy

**Track:** C — go-live, Phase 6 follow-up. **Date:** 2026-07-18.

## Why this sprint exists

Found live, mid-verification of Sprint 36's Google OAuth wiring: `auth-server`'s
generated OAuth2 `redirect_uri` comes out as `http://saga-auth.dnit.be/login/oauth2/code/google`
(confirmed via the live `journalctl` output), not `https://`. The redirect URI
registered in Google Cloud Console is the `https://` version — Google requires an exact
scheme match, so this mismatch will reject the login with `redirect_uri_mismatch` once
past the client-id/secret step.

**Root cause, confirmed by reading both sides:** Nginx (`deploy/nginx/saga-auth.conf`)
already correctly sends `proxy_set_header X-Forwarded-Proto $scheme;` on the HTTPS
server block — the reverse proxy side is fine. `auth-server`'s `application.yml` has no
`server.forward-headers-strategy` setting, so Spring Boot/Tomcat never trusts that
header and reconstructs absolute URLs (like the OAuth `redirect_uri`) using the scheme
of the internal proxy_pass connection (plain HTTP), not the real external one.

## What to change

`auth-server/src/main/resources/application.yml`, in the existing `server:` block:
```yaml
server:
  port: 9000
  forward-headers-strategy: framework
```

This is Spring Boot's standard, documented mechanism for exactly this situation (a
reverse proxy terminating TLS in front of the app) — it makes Spring trust
`X-Forwarded-Proto`/`X-Forwarded-Host`/`X-Forwarded-Port` from the (already-correct)
Nginx config and use them when building any absolute URL, not just the OAuth redirect.

## Explicitly out of scope

- No Nginx config change — it's already correct.
- No change to the OAuth2 client registration or success handler logic (Sprint 36).

## Acceptance criteria (show real output, don't assert "Pass")

1. `./mvnw clean verify` on `auth-server` — BUILD SUCCESS, no test regressions (this is
   a config-only change, existing tests shouldn't be affected, but confirm).
2. Show the actual `git diff`.
3. `git status --short` clean after commit.

Live verification (not part of the automated build, needs the real VPS): after
redeploying `auth-server` with this change, hitting
`https://saga-auth.dnit.be/oauth2/authorization/google` should generate a redirect to
Google with `redirect_uri=https://saga-auth.dnit.be/login/oauth2/code/google` (scheme
now correct) — Dimitri to confirm on the live box once redeployed.
