# V-2 — CORS allow-list update for the two Vercel origins

**Sprint:** 26. **Track:** C — go-live, Phase 4. **Scope:** backend only, all 3 Spring
services.

## Why

The three Spring services currently only allow CORS from `localhost:4200`/`4201` (the
`ng serve` dev ports). Once `order-ui`/`inventory-ui` are deployed to Vercel (V-1 in this
sprint prepares the frontend side), browser requests will originate from
`https://pub-rec-saga-orders-ui.vercel.app` and
`https://pub-rec-saga-inventory-ui.vercel.app` — without these in each service's
allow-list, every cross-origin request fails at the browser's CORS preflight, with no
server-side error to grep for (this fails silently from the backend's point of view).

## Current state (verified this session, re-check if it's moved)

All three services configure CORS the same way, one `CorsConfigurationSource` bean per
`config/SecurityConfig.java`:

- `auth-server/src/main/java/be/dnit/authserver/config/SecurityConfig.java:93`:
  `cfg.setAllowedOrigins(List.of("http://localhost:4200", "http://localhost:4201"));`
- `order-service/src/main/java/be/dnit/orderservice/config/SecurityConfig.java:44`:
  `cfg.setAllowedOrigins(List.of("http://localhost:4200"));`
- `inventory-service/src/main/java/be/dnit/inventoryservice/config/SecurityConfig.java:44`:
  `cfg.setAllowedOrigins(List.of("http://localhost:4201"));`

No test in any of the three services' `src/test` trees asserts on this list
(`grep -rl "corsConfigurationSource\|AllowedOrigins" */src/test` returns empty) — this is
safe to extend without touching or breaking any existing test.

## What to change

Add the two production Vercel origins to each list, keeping the existing localhost
entries (dev still needs to work):

- `auth-server` (serves both UIs' auth calls): add **both**
  `"https://pub-rec-saga-orders-ui.vercel.app"` and
  `"https://pub-rec-saga-inventory-ui.vercel.app"`.
- `order-service`: add **only** `"https://pub-rec-saga-orders-ui.vercel.app"`.
- `inventory-service`: add **only** `"https://pub-rec-saga-inventory-ui.vercel.app"`.

Exact strings matter — no trailing slash, `https://` scheme, exact hostname. A mismatch
here is the single most likely way this task silently doesn't work.

## Explicitly out of scope

- Vercel preview-deployment URLs (they use a different, non-predictable hostname per
  branch/deploy) — only the two stable production `*.vercel.app` domains.
- Any custom `dnit.be` frontend subdomain — not set up yet.

## Acceptance criteria (show real output, don't assert "Pass")

1. `cd shared-model && ./mvnw clean install` then, for each of `auth-server`,
   `order-service`, `inventory-service`: `./mvnw clean verify` — **BUILD SUCCESS**, actual
   Surefire summary line shown for the two services that have tests
   (`Tests run: X, Failures: 0, Errors: 0`).
2. `git diff` for each of the 3 `SecurityConfig.java` files shown in your report — this
   is a small, exact-string-sensitive change, so show the literal diff rather than just
   describing it.
3. `git status --short` clean after commit.
