# Task A-8: auth-server — realistic roles and seed accounts

**Depends on:** nothing else in sprint 1 — this task is fully independent of A-1 through A-7 and can be done in any order relative to them. `auth-server` does not change its structural role at all; only the seeded role names and demo accounts change to fit the Order/Inventory domain.

## Context
`auth-server` is the showcase's central identity provider (JWT issuance + JWKS), shared unchanged by the Order Service and Inventory Service. It currently seeds two generic accounts (`admin@example.com` / `ADMIN`, `user@example.com` / `USER`). For a believable Order/Inventory showcase, the roles and seed accounts should reflect who'd actually use the system: customers placing orders, warehouse staff who'd care about stock, and an admin who approves new registrations.

## Current state
- `auth-server/src/main/java/com/example/authserver/model/UserEntity.java`:
  ```java
  public enum Role { USER, ADMIN }
  ```
- `auth-server/src/main/java/com/example/authserver/DataSeeder.java` seeds:
  - `admin@example.com` / `admin123` / `Admin User` / `ADMIN` / `ACTIVE`
  - `user@example.com` / `user123` / `Test User` / `USER` / `ACTIVE`
- `AdminController.java` and `SecurityConfig.java` (`auth-server`) gate `/api/admin/**` on `hasRole('ADMIN')` — this must keep working unchanged.
- `kafka-demo`/`kafka-demo-2` (or their A-1 renamed equivalents) and the Angular apps read the `role` JWT claim as a plain string — they don't currently branch on specific role values, so widening the enum is safe.

## Task

### 1. `UserEntity.java`
- Change the enum to:
  ```java
  public enum Role { CUSTOMER, WAREHOUSE_STAFF, ADMIN }
  ```
  (Drop generic `USER` — every account in this domain is one of these three.)

### 2. `DataSeeder.java`
Replace the two seeded accounts with three, all using a clearly-fictitious domain so seed data can never be mistaken for a real address:

| Email | Password | Name | Role | Status |
|---|---|---|---|---|
| `admin@example.test` | `admin123` | `Admin User` | `ADMIN` | `ACTIVE` |
| `customer1@example.test` | `customer123` | `Demo Customer` | `CUSTOMER` | `ACTIVE` |
| `warehouse1@example.test` | `warehouse123` | `Demo Warehouse Staff` | `WAREHOUSE_STAFF` | `ACTIVE` |

Keep the existing `if (userRepository.count() > 0) return;` guard and the console-printed seed summary (update it to list all three accounts).

## Out of scope
- Don't change `AuthController`, `SecurityConfig`, `JwtService`, `AdminController`, or the registration flow's default role assignment logic beyond what's needed to compile against the new enum. (Check `AuthController.register()` — it currently hardcodes `UserEntity.Role.USER` for new registrations; change that single reference to `UserEntity.Role.CUSTOMER`, since a self-registering user in this domain is a customer. Don't add role-selection to the registration form/DTO — out of scope.)
- Don't change anything in `order-ui`/`inventory-ui` login or register pages.

## Acceptance criteria
- `cd auth-server && ./mvnw clean compile` succeeds.
- On a fresh H2 instance, startup seeds exactly the three accounts above with the roles/statuses specified.
- Logging in as `admin@example.test` and calling `GET /api/admin/users` still succeeds (role-gating unaffected).
- Logging in as `customer1@example.test` and calling `GET /api/admin/users` still returns `403`.
- Registering a new account via `POST /api/auth/register` results in a `UserEntity` with `role = CUSTOMER`, `status = PENDING`, matching existing approval-flow behavior.
