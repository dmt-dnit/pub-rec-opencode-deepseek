# A-3 — restrict each service/UI to its intended role

**Sprint:** 28. **Scope:** both backend services' `SecurityConfig.java` + both
frontends' `auth.guard.ts`.

## Why

Dimitri flagged this live: any successfully-authenticated account (regardless of role)
can currently use *both* `order-ui`/`order-service` and `inventory-ui`/`inventory-service`
— a `CUSTOMER` account can hit inventory endpoints and vice versa. That's not the
intended model. The seeded roles already exist for exactly this purpose
(`CUSTOMER`, `WAREHOUSE_STAFF`, `ADMIN`) but nothing currently checks them beyond
"is this a valid JWT at all."

**Intended model, this task's scope:**
- `order-service`/`order-ui`: `CUSTOMER` and `ADMIN` only.
- `inventory-service`/`inventory-ui`: `WAREHOUSE_STAFF` and `ADMIN` only.
- `auth-server` itself is unaffected — it's the shared identity provider for both, not
  scoped to either.

## Current state (verified, re-check if it's moved)

`order-service/src/main/java/be/dnit/orderservice/config/SecurityConfig.java`'s
`authorizeHttpRequests` block:
```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/ws/**", "/api/ws/**").permitAll()
    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
    .requestMatchers("/actuator/**").permitAll()
    .requestMatchers("/api/**").authenticated()
    .anyRequest().permitAll()
)
```
`inventory-service/src/main/java/be/dnit/inventoryservice/config/SecurityConfig.java` is
byte-identical in shape (same `.requestMatchers("/api/**").authenticated()` line).

Both services' `jwtAuthenticationConverter()` maps the JWT's `role` claim to a
`ROLE_<value>` authority via `JwtGrantedAuthoritiesConverter` — so `hasRole("CUSTOMER")`/
`hasAnyRole(...)` already works correctly against the existing token shape; no change
needed to how roles are extracted, only to what's required.

Both frontends' `auth.guard.ts` (byte-identical, confirmed) only check
`auth.isLoggedIn()` (token presence), no role check at all.

## What to change

### Backend (both services, same shape, different allowed roles)

`order-service/.../SecurityConfig.java`: change
```java
.requestMatchers("/api/**").authenticated()
```
to
```java
.requestMatchers("/api/**").hasAnyRole("CUSTOMER", "ADMIN")
```

`inventory-service/.../SecurityConfig.java`: change the same line to
```java
.requestMatchers("/api/**").hasAnyRole("WAREHOUSE_STAFF", "ADMIN")
```

Leave every other line (the `permitAll()` matchers, WebSocket, actuator, swagger)
untouched — this only tightens the already-authenticated `/api/**` matcher.

### Frontend (both UIs, same shape, different allowed roles)

Both currently expose the user's role via `AuthService`'s `user$`
(`BehaviorSubject<UserInfo | null>`), same shape in both UIs — this task depends on A-2
adding a public synchronous accessor to `order-ui`'s `AuthService` (`get currentUser():
UserInfo | null`); add the same accessor to `inventory-ui`'s `AuthService` if A-2 hasn't
already been applied there (it hasn't — A-2 only touches `order-ui`). Check both files'
actual current state before assuming which already has the accessor.

`order-ui/src/app/guards/auth.guard.ts`: after confirming login, also check role:
```ts
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authGuard = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isLoggedIn()) return router.parseUrl('/login');
  const role = auth.currentUser?.role;
  if (role && role !== 'CUSTOMER' && role !== 'ADMIN') {
    auth.logout();
    return router.parseUrl('/login');
  }
  return true;
};
```
`inventory-ui/src/app/guards/auth.guard.ts`: same shape, check `role !== 'WAREHOUSE_STAFF'
&& role !== 'ADMIN'` instead.

**Note the same known edge case as A-2's guard**: on a hard refresh, `currentUser` may
still be `null` if `fetchMe()` hasn't resolved yet — this guard's `if (role && ...)`
check is written so a `null` role (not yet fetched) does NOT incorrectly log someone
out; it only rejects when the role is *known* and *wrong*. This is a deliberate,
acceptable trade-off (a wrong-role user might briefly see a flash of the UI before the
backend's own `hasAnyRole` check 403s their API calls anyway — the backend is the real
enforcement point, the frontend guard is UX, not the security boundary).

## Explicitly out of scope

- No change to `auth-server`'s own `SecurityConfig.java` — it's shared, not scoped.
- No cross-app redirect (e.g., a `WAREHOUSE_STAFF` user hitting `order-ui` doesn't get
  auto-redirected to `inventory-ui`'s URL) — just logged out back to that app's own
  `/login`. Keeping it simple; revisit only if this becomes an actual UX complaint.
- `ADMIN` gets access to both, unchanged from today for admin accounts.

## Acceptance criteria (show real output, don't assert "Pass")

1. `cd shared-model && ./mvnw clean install`, then `order-service`/`inventory-service`
   each `./mvnw clean verify` — **BUILD SUCCESS**, actual Surefire summary shown for
   both (`Tests run: 9, ...` is the expected baseline — call out explicitly if either
   existing test suite now fails, since a test authenticating as the "wrong" role for
   that service would be a real regression this change could cause).
2. `cd order-ui && npx ng build` and `cd inventory-ui && npx ng build` — **BUILD
   SUCCESS** for both.
3. Show the actual `git diff` for all 4 changed files.
4. `git status --short` clean after commit.

## Loop note

Reviewer: this is the kind of change where the existing test suites are the real
regression check — a `CUSTOMER`-authenticated integration test hitting `order-service`
should still pass; if inventory-service's test suite happens to authenticate as
`CUSTOMER` anywhere (check the actual test setup, don't assume), this change would
break it, and that's a real finding, not a false positive.
