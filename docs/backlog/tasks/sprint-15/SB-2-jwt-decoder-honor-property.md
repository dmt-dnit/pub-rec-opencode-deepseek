# SB-2 — Resource servers: honor the configured jwk-set-uri (Codex P1, Sprint 15 round 2)

**Sprint:** 15 (Track B Sprint 2), round 2
**Source:** `reviews/sprint-15-track-b-review.md` §P1 — Playwright smoke fails in the containerized stack
**Priority:** Must — blocks Sprint 15 AC2/AC3 (resource servers respond; saga works end-to-end)
**Implementer:** Claude **sonnet** worktree agent (small but security-wiring; mechanical removal). Worktree branched from current `main` HEAD — **create it OUTSIDE `.claude/`** only if using opencode; a Claude agent worktree is fine.
**Scope:** `order-service` and `inventory-service` `config/SecurityConfig.java` only. Do not touch auth-server, shared-model, UIs, or anything else.

## Problem

Both resource services define an explicit decoder bean that hardcodes localhost, overriding the configured property and its container env override:

```java
// order-service & inventory-service SecurityConfig.java (lines 23-26)
@Bean
public JwtDecoder jwtDecoder() {
    return NimbusJwtDecoder.withJwkSetUri("http://localhost:9000/oauth2/jwks").build();
}
```

Each service's `application.yml` already sets `spring.security.oauth2.resourceserver.jwt.jwk-set-uri: http://localhost:9000/oauth2/jwks`, and `docker-compose.yml` overrides it via `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=http://auth-server:9000/oauth2/jwks`. Because the explicit bean wins, the override is ignored → inside containers the service tries `localhost:9000`, gets connection refused, and every authenticated `/api/**` call returns 500. The Playwright smoke fails at the first authenticated call. (Pre-existing since the domain pivot; surfaced by the containerized smoke.)

## Fix

In **both** `order-service` and `inventory-service` `config/SecurityConfig.java`:
1. Delete the entire `jwtDecoder()` `@Bean` method (lines 23-26).
2. Remove the now-unused imports `org.springframework.security.oauth2.jwt.JwtDecoder` and `org.springframework.security.oauth2.jwt.NimbusJwtDecoder`.
3. Change nothing else — the filter chain does **not** reference `jwtDecoder()` (`.oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(...)))` only wires the authorities converter). With no `JwtDecoder` bean present, Spring Boot's OAuth2 resource-server auto-config builds a `NimbusJwtDecoder` from `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`, which honors the container env override.

Do **not** add a new bean or inject the property manually — letting auto-config own it is the correct, minimal fix.

## Acceptance criteria

1. Neither resource service defines a `JwtDecoder` bean; no `NimbusJwtDecoder` import remains. `grep -rn "NimbusJwtDecoder\|jwtDecoder()" order-service/src/main inventory-service/src/main` returns nothing.
2. `cd shared-model && ./mvnw -q -DskipTests install` then `./mvnw verify` green in **both** order-service and inventory-service (Java 21) — paste real output. The existing EmbeddedKafka/idempotency/DLT tests must still pass (they don't make authenticated HTTP calls, and the decoder is lazy so context still starts).
3. State explicitly: the **live containerized Playwright smoke is Codex-only** here (no Docker/browser in the coordinator env) — this fix makes the JWKS URL honor `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI`, which is what the smoke needs.

## Notes
- This is the only Sprint 15 blocker. The classic-bridge / Jackson-2 shims (Codex AC4 PARTIAL) are accepted, tracked as Sprint 16 cleanup. Spring Boot 4.1.0 availability is noted but out of scope (sprint scoped 4.0.x).
