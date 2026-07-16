# Sprint 28 — Handoff to Codex

**Coordinator:** Claude Code. **Date:** 2026-07-16.
**Task:** admin role promotion + approval UI + role-scoped service access, all surfaced by actually using the live Vercel/VPS deployment.
**Implementer:** opencode+DeepSeek, standing default, one isolated worktree per task (A-1 → A-2 → A-3, sequential per the established pattern).
**Merge-gate tier:** coordinator-supervised, pushed direct to `main` per the Sprint 21 policy.
Review-Target-Commit: 2feba3a

## Why this sprint exists

Two real needs came out of actually using the live system after Sprint 26/27:

1. Dimitri registered his own account on the live site; the coordinator had to approve
   it manually via `curl` (no UI existed for this, only a raw API + one seeded admin
   account). He asked to become an admin himself and get an approval UI.
2. **Live-caught by Dimitri**: any successfully-authenticated account — regardless of
   role — could use *both* `order-ui`/`order-service` and `inventory-ui`/
   `inventory-service`. There was no role enforcement beyond "is this a valid JWT."

## What was done (3 commits, `main`)

- **A-1** (`8499483`, auth-server): new `PUT /api/admin/users/{id}/role` endpoint
  (admin-only, validates against the real `UserEntity.Role` enum, 400 on garbage input)
  + `@JsonIgnore` on `UserEntity.password` so `GET /api/admin/users` no longer leaks
  bcrypt hashes to any admin session (found live while scoping A-2's UI — a real,
  pre-existing exposure on the public deployment, fixed here since the same file was
  already being touched).
- **A-2** (`4dbc6f9`, order-ui): new `/admin` page (`admin.component.ts` +
  `admin.service.ts` + `admin.guard.ts`), lists all users sorted pending-first with an
  Approve button wired to the already-working `GET /api/admin/users` +
  `PUT /api/admin/users/{id}/approve`. `AuthService` gets a public `currentUser`
  accessor the guard needs. Admin link in the dashboard toolbar, visible only when
  `role === 'ADMIN'`.
- **A-3** (`2feba3a`): restricts `order-service`/`order-ui` to `CUSTOMER`+`ADMIN` and
  `inventory-service`/`inventory-ui` to `WAREHOUSE_STAFF`+`ADMIN`. Backend: each
  service's `SecurityConfig.java` changed `.requestMatchers("/api/**").authenticated()`
  to `.hasAnyRole(...)` with its own pair — this is the real enforcement point. Frontend:
  both `auth.guard.ts` files reject a known-wrong role (logging the user out back to
  `/login`) — UX only, not the security boundary. `inventory-ui`'s `AuthService` also
  gained the same `currentUser` accessor A-2 added to `order-ui`'s.

## A real security decision made mid-scoping, not a silent shortcut

The repo is **public on GitHub**. The initial ask ("make me admin") could have been
done by seeding Dimitri's real email/password into `DataSeeder.java` — rejected
immediately since that would expose a real login credential for his real account to
anyone reading the repo. Instead, A-1 adds a proper role-promotion endpoint; the
one-time promotion of his *already-registered* account (his own self-chosen password,
never touching source) is a coordinator action taken separately after this sprint
lands, not part of the automated implementation. See "Not done in this sprint" below.

## Coordinator verification — full independent rebuild from integrated `main`

- `shared-model`: `./mvnw clean install` — BUILD SUCCESS.
- `auth-server`: `./mvnw clean verify` — BUILD SUCCESS (no tests, pre-existing —
  acceptance criterion 3 in A-1's brief called this out explicitly rather than skip it
  silently).
- `order-service`: `./mvnw clean verify` — **BUILD SUCCESS, Tests run: 9, Failures: 0,
  Errors: 0, Skipped: 2** — unchanged from the pre-Sprint-28 baseline, confirming A-3's
  `hasAnyRole` change didn't regress the existing suite (the one test that exercises
  `/api/orders`, `OrderEventIntegrationTest`, disables the Spring Security filter chain
  entirely via `@AutoConfigureMockMvc(addFilters = false)` — verified this *before*
  scoping A-3, not after finding a surprise failure).
- `inventory-service`: `./mvnw clean verify` — **BUILD SUCCESS, Tests run: 9, Failures:
  0, Errors: 0, Skipped: 1** — same baseline, unchanged.
- `order-ui`: `npx ng build` — BUILD SUCCESS; new `admin-component` lazy chunk confirmed
  present in the build output.
- `inventory-ui`: `npx ng build` — BUILD SUCCESS.
- `git status --short` clean on `main` after all three cherry-picks; build artifacts
  never staged.

## Explicitly out of scope (unchanged from the task briefs)

- No role-promotion UI — the new endpoint is API-only, used once by the coordinator, not
  exposed to any frontend. Self-promotion guards, "can't demote the last admin," and
  audit logging are all undesigned and deliberately deferred.
- No cross-app redirect on the wrong-role guard rejection (just logged out to that
  app's own `/login`).
- No `inventory-ui` admin page (roles are global via `auth-server`, not per-service, so
  there's no functional need for it to exist in both — cheap follow-up if wanted).

## Not done in this sprint, coordinator's separate follow-up action

1. **Promoting `dimitri.gevers@gmail.com` to `ADMIN`** via the new endpoint — needs the
   live `saga-auth.dnit.be` backend redeployed with A-1's code first (it isn't yet;
   redeploying a live backend service needs Dimitri's explicit go-ahead each time, same
   as every prior backend redeploy this Track).
2. Redeploying `order-service`/`inventory-service` with A-3's role restriction is the
   same kind of live action — also held pending explicit go-ahead, and worth flagging
   directly to Dimitri: once redeployed, any account whose role doesn't match a
   service will be logged out of that service's UI immediately (by design, this sprint's
   whole point) — worth confirming test accounts are using the right service before
   redeploying, not a surprise regression if noticed.

## Loop note

Reviewer: the password-hash-leak fix (A-1) is a genuine, if minor, live security
exposure closed in the same pass as an unrelated feature — worth confirming
`@JsonIgnore` is on the field (not accidentally on a different member) and that
`CustomUserDetailsService`/`AuthController`'s internal use of `getPassword()`/
`setPassword()` for actual authentication is unaffected (it should be — this is a
serialization-only change, not a JPA/logic change). For A-3, the real regression check
is whether either service's existing test suite's authentication setup happens to use
a role the new `hasAnyRole` restriction would now reject — confirmed clean by reading
the actual test setup before scoping, not assumed.
