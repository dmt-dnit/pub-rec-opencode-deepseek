# Sprint 36 Handoff — real Google OAuth end-to-end (Track C Phase 6)

**Coordinator:** Claude Code. **Implementer:** opencode+DeepSeek (2 parallel worktrees). **Date:** 2026-07-17.
Review-Target-Commit: b0c271e

## What was done

Full context: `docs/backlog/sprint-36.md`,
`docs/backlog/tasks/sprint-36/OAUTH-1-backend-oauth-bridge.md`,
`docs/backlog/tasks/sprint-36/OAUTH-2-frontend-oauth-callback.md`.

Scoping this sprint found the actual state was worse than the roadmap assumed: Google
OAuth login had no JWT issuance, no user provisioning, and no frontend callback
handling at all — a complete dead end even with real credentials. Decisions locked with
Dimitri before scoping: auto-provision first-time Google users as `PENDING`/`CUSTOMER`
(same admin-approval gate as normal registration); Google OAuth is `order-ui` only
(button removed from `inventory-ui`); real Google Cloud credentials explicitly
deferred — everything built and verified against the existing
`${GOOGLE_CLIENT_ID:placeholder}` default.

**OAUTH-1 (`4ac3009`, `auth-server`):**
- New `CustomOAuth2UserService` — delegates the real Google fetch to
  `DefaultOAuth2UserService`, then finds-or-creates a `UserEntity` by email
  (`PENDING`/`CUSTOMER`, random bcrypt password satisfying the NOT NULL column, never
  usable). Leaves an existing user's role/status untouched on repeat login.
- New `OAuth2SuccessHandler` (extracted to a named, testable class, replacing the old
  inline lambda that only ever redirected with no token) — branches on the user's
  status: `ACTIVE` mints a real JWT via the existing `JwtService.generateToken(user)`
  and redirects with `?oauth2=success&token=<jwt>`; `PENDING` redirects with
  `?oauth2=pending`, no token; anything else redirects with `?oauth2=error`, no token.
- New `app.oauth2.success-redirect-base-url` property (`APP_OAUTH2_REDIRECT_BASE` env
  override, defaults to `http://localhost:4200` — unchanged local/CI behavior).
- **Found and fixed two circular-DI issues while wiring this in**, both standard Spring
  patterns: extracted `PasswordEncoder` out of `SecurityConfig` into a new
  `PasswordConfig` (the new `CustomOAuth2UserService` needs `PasswordEncoder`, and
  `SecurityConfig`'s constructor now needs `CustomOAuth2UserService` — without this
  extraction, resolving `passwordEncoder()` would require `SecurityConfig` to be
  already constructed, a genuine cycle); `@Lazy` on `JwtEncoder` in `JwtService`'s
  constructor (same shape of cycle via `OAuth2SuccessHandler` → `JwtService` →
  `JwtEncoder` → `SecurityConfig`). Unlike the frontend's Sprint 33 `NG0200` bug, Spring
  fails loudly at startup on a genuine circular constructor dependency rather than
  silently breaking at runtime — this would have been caught by `mvnw clean verify`/app
  boot immediately, not shipped silently.
- 8 new tests (`OAuth2SuccessHandlerTest`, `CustomOAuth2UserServiceTest`) covering
  status-branching, first-login provisioning, idempotency on repeat login, and that an
  existing `ACTIVE`/`ADMIN` user's role/status is never reset by a Google login.

**OAUTH-2 (`b0c271e`, `order-ui` + `inventory-ui`):**
- `order-ui/auth.service.ts`: new `loginWithToken(token)` — stores the token, calls the
  existing (Sprint-33-hardened) `fetchMe()` to populate the user from `/api/auth/me`.
- `order-ui/login.component.ts`: on init, reads query params —
  `oauth2=success&token=...` logs in and navigates to `/dashboard`;
  `oauth2=pending`/`oauth2=error` show an inline message, no silent failure.
- `inventory-ui/login.component.ts`: Google login button, divider, and
  `loginWithGoogle()` removed entirely (template and code) — email/password login
  untouched.

## Coordinator verification (independent, not the implementer's self-report)

- **Read every line of both diffs directly** before integrating — confirmed the
  circular-DI fixes are genuine, standard, correct Spring patterns (not a
  workaround masking a real bug), and that the status-branching logic exactly matches
  the query-param contract both tasks were scoped against.
- `./mvnw clean verify` on `auth-server`, independently, twice — once inside the
  isolated worktree, once again on the fully integrated `main` after both cherry-picks:
  **BUILD SUCCESS, Tests run: 9, Failures: 0, Errors: 0** both times.
- **Started the app directly** (`./mvnw spring-boot:run`) with the untouched
  placeholder OAuth credentials and confirmed via the actual log line
  `"Started AuthServerApplication in 37.5 seconds"` with zero
  `BeanCurrentlyInCreationException`/`APPLICATION FAILED` anywhere in the log — the
  real proof the DI fixes work, not just that tests pass.
- `ng build --configuration production` clean on both `order-ui` and `inventory-ui`,
  independently, twice (isolated worktree + integrated `main`).
- Confirmed via `git status --short` clean after each cherry-pick, no conflicts (the
  two tasks touched entirely disjoint files: `auth-server/**` vs `order-ui/**` +
  `inventory-ui/**`).

## What could not be verified (said explicitly, not glossed over)

**No real Google OAuth round-trip was exercised** — not possible with placeholder
`client-id`/`client-secret`; Google will reject a request from a fake client
regardless of how correct the rest of the code is. This is expected and was scoped as
out-of-reach for this sprint from the start (Dimitri's explicit call to defer real
credential creation). What *was* verified instead:
- The backend's user-provisioning and status-branching logic via mocked
  `OAuth2User`/`OAuth2UserRequest` objects (standard Spring Security test pattern for
  this exact scenario) — proves the code that runs *after* Google's real handshake
  completes.
- The app boots cleanly with the OAuth2 client registration wired in (placeholder
  values) — proves the Spring configuration itself is valid.
- Frontend callback handling was verified by reading the diff against the exact
  contract, and via a clean production build — **not** via an actual browser
  navigation test (no browser access in this coordinator session). This is the one
  gap: someone with browser access should navigate to
  `http://localhost:4200/login?oauth2=success&token=<a-real-locally-issued-JWT>`,
  `?oauth2=pending`, and `?oauth2=error` against a running `order-ui` to see the actual
  rendered behavior.

## Explicitly out of scope (unchanged from the brief)

- Creating the real Google Cloud OAuth client — Dimitri's action, deferred.
- Any change to email/password login, registration, or the admin approval UI/API.
- The cosmetic `issuer("http://localhost:9000")` literal in `JwtService.java` (confirmed
  unused for validation — resource servers use `jwk-set-uri`, not `issuer-uri`).

## What Codex should check independently

1. The two circular-DI fixes (`PasswordConfig` extraction, `@Lazy JwtEncoder`) — confirm
   they're genuinely necessary and correctly scoped, not papering over a deeper design
   issue in `SecurityConfig`.
2. Whether `CustomOAuth2UserService`'s package-private `setDelegate(...)` test seam is
   an acceptable pattern here (production code carrying a test-only mutation point) —
   minor style question, not a functional concern.
3. Whether `OAuth2SuccessHandler`'s `orElseThrow(() -> new IllegalStateException(...))`
   (when the OAuth2-authenticated email has no matching `UserEntity`) is the right
   failure mode — this should be unreachable in practice since
   `CustomOAuth2UserService` runs first in the same request and guarantees the row
   exists, but worth confirming the ordering guarantee holds.

## Next step (not part of this sprint)

Live browser verification of the frontend callback handling (see "What could not be
verified" above), and — whenever Dimitri is ready — creating the real Google Cloud
OAuth client (redirect URI `https://saga-auth.dnit.be/login/oauth2/code/google`) and
wiring `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` into the VPS's `auth.env`, same pattern
as `APP_JWT_KEY_PATH`/`SPRING_DATASOURCE_URL`.
