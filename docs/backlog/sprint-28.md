# Sprint 28 — Admin role promotion + approval UI

**Track:** C — go-live, post-Phase-4 follow-up (not a numbered roadmap phase; a real
operational need surfaced by actually using the live system).

## Why this sprint exists

Dimitri registered his own account on the live Vercel deployment, and the coordinator
approved it manually via `curl` against `PUT /api/admin/users/{id}/approve` (there's no
UI for this — only a seeded `admin@example.test` account and a raw API). Two real asks
came out of that:

1. **Make Dimitri's own account an admin**, so he doesn't need the coordinator to run
   `curl` every time. **Important constraint: this repo is public on GitHub.** Seeding
   his real email/password into `DataSeeder.java` (source code) would expose a login
   credential for his actual account to anyone reading the repo — not acceptable, even
   for a demo app. Decided instead: add a small role-promotion admin endpoint, and
   promote his *already-registered* account (his own self-chosen password, never
   touching source) once via the API. This task (A-1) builds that endpoint; the
   one-time promotion itself is the coordinator's job after A-1 ships, not part of this
   sprint's automated scope.
2. **An actual Admin UI page** so future approvals don't need `curl` either — lists
   pending users, approve button, wired to the existing (already-working)
   `GET /api/admin/users` + `PUT /api/admin/users/{id}/approve` endpoints.

## A second, related finding worth fixing in the same pass

While looking at `AdminController.listUsers()` to plan the UI, the coordinator noticed it
returns the raw `UserEntity` directly — including the bcrypt password hash — to any
authenticated admin. Not exploitable without an admin session already, but there's no
reason to serialize password hashes to any client ever. Bundled into A-1 since it's the
same file/endpoint being touched anyway.

## Task list

1. **A-1 — auth-server**: add `PUT /api/admin/users/{id}/role` (admin-only), and stop
   serializing the password hash from `GET /api/admin/users`.
2. **A-2 — order-ui**: new `/admin` route + page, admin-only guard, lists pending users
   with an Approve action.
3. **A-3 — role-scoped service access** (added mid-scoping, Dimitri's live catch):
   currently *any* authenticated account can use both `order-ui`/`order-service` and
   `inventory-ui`/`inventory-service` regardless of role — a `CUSTOMER` account can hit
   inventory endpoints and vice versa. Restricts `order-service`/`order-ui` to
   `CUSTOMER`+`ADMIN` and `inventory-service`/`inventory-ui` to `WAREHOUSE_STAFF`+`ADMIN`,
   at both the Spring Security layer (real enforcement) and each frontend's route guard
   (UX only). Verified zero test-regression risk: the one existing test that exercises
   `order-service`'s `/api/orders` endpoint (`OrderEventIntegrationTest`) disables the
   Spring Security filter chain entirely (`@AutoConfigureMockMvc(addFilters = false)`),
   so it's unaffected either way.

Independent (different repos/tech stacks), can be implemented in either order, but
dispatch sequentially through opencode as usual (one worktree agent at a time). A-3
touches the same `auth.guard.ts`/`AuthService` files A-2 adds an accessor to in
`order-ui` — sequence A-2 before A-3 to avoid two worktrees editing the same file
independently and needing manual reconciliation.

## Explicitly out of scope

- **The new role-promotion endpoint is intentionally NOT wired into the UI in this
  sprint.** "Promote anyone to admin" is a more sensitive capability than "approve a
  pending registration" — self-promotion prevention, demoting the last remaining admin,
  audit logging, etc. haven't been designed. For now it's an API-only capability the
  coordinator uses once, directly. Exposing it in the UI is a future decision, not an
  oversight here.
- No change to `inventory-ui` — the admin page lives in `order-ui` only for now (roles
  are global across the whole system via `auth-server`, not per-service, so there's no
  functional reason it must exist in both; adding it to `inventory-ui` too is a cheap
  follow-up if wanted later, not scoped now).
- No audit log of who approved/promoted whom — matches the existing `approve` endpoint's
  behavior, not a new gap introduced by this sprint.

## Loop note

Reviewer: check that the password-hash fix in A-1 doesn't break `UserRepository`
persistence itself (it shouldn't — this is a *serialization*-only change, the entity's
`password` field and JPA mapping stay untouched, only the JSON response shape changes).
Also verify the new `/api/admin/users/{id}/role` endpoint validates the role value
against the actual `UserEntity.Role` enum (`CUSTOMER`, `WAREHOUSE_STAFF`, `ADMIN`) and
returns a real error for garbage input, not a silent no-op or a stack trace.
