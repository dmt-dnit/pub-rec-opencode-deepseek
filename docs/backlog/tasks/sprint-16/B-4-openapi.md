# B-4 — OpenAPI / Swagger UI on the three services

**Sprint:** 16 (Track B Sprint 3)
**Priority:** Should — Track B backlog item (API contracts)
**Implementer:** Claude sonnet worktree agent (additive, read-verifiable). Branch from current `main` (after SB-3 lands, so the modular poms are settled) — verify the base with `git diff --name-only`.
**Scope:** `auth-server`, `order-service`, `inventory-service` — add springdoc; expose Swagger UI. No business-logic changes.

## What to do

Add **springdoc-openapi 3.0.x** (the line that supports Spring Boot 4 / Spring Framework 7 — `springdoc-openapi-starter-webmvc-ui` version `3.0.x`, pick the latest 3.0.x) to each of the three services' poms. Confirm the exact current 3.0.x version from Maven Central rather than guessing.

For each service:
- Swagger UI reachable (default `/swagger-ui.html`, OpenAPI JSON at `/v3/api-docs`) — or a configured path; state which.
- The generated spec must list that service's **real** endpoints with request/response schemas matching the actual DTOs (e.g. order-service `POST /api/orders` with the order request/response; inventory-service `GET /api/inventory`; auth-server login/register/admin endpoints).
- **Security:** the three services are JWT resource servers with `/api/**` authenticated. The Swagger UI + `/v3/api-docs` paths must be reachable for the demo — permit them in each `SecurityConfig` (add the springdoc paths to the `permitAll` matchers). Don't open `/api/**`.

## Acceptance criteria (observable)

1. Each service's poms include springdoc 3.0.x; `mvnw verify` stays green in all three (real output).
2. `/v3/api-docs` returns a valid OpenAPI 3 document on each service listing its real endpoints with DTO schemas. (Verify by starting the service and cur\ling `/v3/api-docs`, or via a springdoc test; if you can't run the full stack, generate the spec via the build and state what you verified vs what's Codex-only.)
3. Swagger UI path is reachable without a JWT (permitted in `SecurityConfig`); `/api/**` stays authenticated.
4. No change to existing endpoints or tests' behavior; existing suites still green.

## Notes
- springdoc auto-generates from the controllers — minimal annotation needed. Add `@Operation`/`@Schema` only if a DTO/endpoint renders unclearly.
- Keep it additive: a dependency + (if needed) a few permit-path lines in each `SecurityConfig`. Flag the live `/v3/api-docs` curl as Codex-verifiable if you can't run the services.
