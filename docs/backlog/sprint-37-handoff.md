# Sprint 37 Handoff — fix OAuth2 redirect_uri scheme behind the reverse proxy

**Coordinator:** Claude Code. **Implementer:** opencode+DeepSeek (worktree). **Date:** 2026-07-18.
Review-Target-Commit: de147a0

## What was done

Found live while Dimitri was verifying Sprint 36's Google OAuth wiring on the real VPS:
`journalctl` showed `auth-server` generating
`redirect_uri=http://saga-auth.dnit.be/login/oauth2/code/google` — `http://`, not
`https://`. Nginx (`deploy/nginx/saga-auth.conf`) already correctly sends
`X-Forwarded-Proto $scheme` on the HTTPS server block; `auth-server` just wasn't
configured to trust it. Fix: added `forward-headers-strategy: framework` under
`application.yml`'s existing `server:` block — Spring Boot's standard mechanism for
trusting `X-Forwarded-*` headers from a reverse proxy when reconstructing absolute
URLs.

## Coordinator verification

- `./mvnw clean verify` on `auth-server`, independently, both in the isolated worktree
  and again on integrated `main`: **BUILD SUCCESS, Tests run: 9, Failures: 0, Errors: 0**
  both times — no regression from Sprint 36's tests.
- Reviewed the actual diff: exactly one line added, nothing else touched.

## Explicitly out of scope

- No Nginx config change — already correct.
- No change to OAuth2 client registration or success handler logic (Sprint 36).

## What's still needed (Dimitri, live ops — not part of this sprint's automated criteria)

Redeploy `auth-server` on `dnit-vps` with this commit, then confirm live that
`https://saga-auth.dnit.be/oauth2/authorization/google` now generates a redirect to
Google with `redirect_uri=https://saga-auth.dnit.be/login/oauth2/code/google` (scheme
now correct) — visible in `journalctl -u pubrec-auth`, same way the original bug was
found.
