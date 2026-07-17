# Sprint 36 — real Google OAuth end-to-end (Track C Phase 6)

**Track:** C — go-live, Phase 6. **Date:** 2026-07-17.

## Why this sprint exists, and what it actually is

Phase 6 was scoped in the roadmap as "wire up real Google OAuth" — reading the actual
code before writing this brief turned up something the roadmap didn't know: **the
Google OAuth login flow is not a small fix, it's unbuilt.** Confirmed by reading, not
assumed:

- `SecurityConfig.java`'s `oauth2Login` success handler is one line:
  `response.sendRedirect("http://localhost:4200/login?oauth2=success")` — no JWT is
  minted or attached anywhere in that redirect (no query param, no cookie, nothing).
- There is no custom `OAuth2UserService` anywhere in `auth-server` — a Google login
  never creates or matches a `UserEntity` row, never gets a role, never gets checked
  against the `PENDING`/`ACTIVE` gate the rest of the app enforces.
- Neither UI has any code reading a `?oauth2=success` query param — `grep -n "oauth2"
  order-ui/src/app/pages/login/login.component.ts` only shows the button that
  *initiates* login, nothing handling the return trip.

So this sprint builds the actual Google→JWT bridge, not just a redirect-URL fix.

## Decisions made before scoping (Dimitri, 2026-07-17)

1. **First-time Google login auto-creates a `UserEntity` with `status=PENDING`,
   `role=CUSTOMER`** — same admin-approval gate as email/password registration, no new
   trust bypass.
2. **Google OAuth is `order-ui` (customer) only.** Remove the "Login with Google"
   button from `inventory-ui` entirely — `WAREHOUSE_STAFF` is an internally-provisioned
   role, not meant for public self-signup.
3. **Real Google Cloud credentials are explicitly deferred** — build and verify
   everything against the existing `${GOOGLE_CLIENT_ID:placeholder}` /
   `${GOOGLE_CLIENT_SECRET:placeholder}` defaults (already the correct pattern, no
   change needed there). This means **the actual Google consent screen cannot be
   exercised live in this sprint** — placeholder credentials will not complete a real
   Google handshake. Verification must prove the code paths this sprint adds/changes
   via tests and direct manual navigation to the callback URLs with a hand-built query
   string, not by claiming a live Google login was tested (it can't be, honestly, until
   real credentials exist). Say this explicitly in the task report — don't claim
   "tested end-to-end" if the real Google leg was never exercised.

## Two tasks, tightly coupled by a shared query-param contract but separable by module

### OAUTH-1 (backend, `auth-server`) — see `docs/backlog/tasks/sprint-36/OAUTH-1-backend-oauth-bridge.md`
### OAUTH-2 (frontend, `order-ui` + `inventory-ui`) — see `docs/backlog/tasks/sprint-36/OAUTH-2-frontend-oauth-callback.md`

The contract between them (defined here so both can be implemented independently
without waiting on each other): after Google auth completes, `auth-server` redirects
the browser to `{configured frontend base URL}/login` with exactly one of:

- `?oauth2=success&token=<jwt>` — user is `ACTIVE`, JWT issued, log them in.
- `?oauth2=pending` — user is `PENDING` (new or existing), show an approval-pending message, no token.
- `?oauth2=error` — anything else (e.g. `DISABLED` status), show a generic failure message, no token.

## Explicitly out of scope

- Creating the real Google Cloud OAuth client — Dimitri's action, deferred, not part
  of this sprint.
- Any change to email/password login, registration, or the admin approval UI/API —
  this sprint only adds a second path into the same existing user model.
- The `issuer("http://localhost:9000")` literal in `JwtService.java` — confirmed
  cosmetic only (resource servers use `jwk-set-uri`, not `issuer-uri`, so `iss` is
  never validated; `grep -rn "issuer-uri" order-service/ inventory-service/` returns
  empty). Not worth touching in this sprint.

## Note on the stale secrets doc

`docs/security/secrets-and-test-data.md` currently states the JWT signing key
"is never written to disk... don't change this to a fixed/persisted key for
convenience" and that the H2 database is wiped every restart — both now false since
Sprint 32 and Sprint 29. The coordinator will update that doc directly after this
sprint lands (prose-only correction of stale factual claims, not a logic change) —
not part of this task's scope.

## Acceptance criteria (show real output, don't assert "Pass")

See each task brief for its own specific criteria. Cross-cutting:
1. `./mvnw clean verify` on `auth-server` — BUILD SUCCESS, including new tests for the
   provisioning/status-branching logic (mocked `OAuth2User`/`UserEntity`, not a real
   Google round-trip — that's not possible with placeholder credentials).
2. `ng build --configuration production` clean on both `order-ui` and `inventory-ui`.
3. Manual verification of the frontend callback handling: navigate directly to
   `http://localhost:4200/login?oauth2=success&token=<a real locally-issued JWT>`,
   `?oauth2=pending`, and `?oauth2=error` and confirm each renders correctly — this
   exercises the exact code path Google's real redirect would hit, without needing a
   real Google login.
4. Confirm the local dev app still starts cleanly with placeholder OAuth credentials
   (it does today — this sprint must not break that baseline).
