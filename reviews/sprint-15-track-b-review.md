# Sprint 15 Review - Spring Boot 4 Migration

Review target: `17843ea7222390ffcd51daafa8776270d39ac001`  
Handoff: `docs/backlog/sprint-15-handoff.md`  
Verdict: **REJECT / not cleared**

## Findings

### P1 - Containerized JWT validation is broken, so the Playwright smoke cannot pass

`order-service` and `inventory-service` still define their own `JwtDecoder` beans with a hardcoded JWKS URL:

- `order-service/src/main/java/com/example/orderservice/config/SecurityConfig.java:24-25`
- `inventory-service/src/main/java/com/example/inventoryservice/config/SecurityConfig.java:24-25`

Both return `NimbusJwtDecoder.withJwkSetUri("http://localhost:9000/oauth2/jwks").build()`.

This was not introduced by the Sprint 15 diff, but Sprint 15's Boot 4 / Security 7 acceptance explicitly requires the resource servers and JWT-backed saga to still work. The live smoke is therefore not clear.

That bypasses the container-specific override that the documented stack provides:

- `docker-compose.yml:65` sets `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=http://auth-server:9000/oauth2/jwks`
- `docker-compose.yml:78` sets the same for `inventory-service`
- `scripts/startup-all.sh:79` and `scripts/startup-all.sh:87` mirror the same fallback wiring

Result from the live smoke setup:

- `cd e2e && npm test` failed at `e2e/smoke.spec.ts:70`, waiting for `SKU-001` in the inventory table.
- The browser diagnostics recorded HTTP 500s from both UIs.
- Direct authenticated calls to the real backend paths returned HTTP 500:
  - `GET http://localhost:8081/api/inventory`
  - `GET http://localhost:8080/api/orders`
- Service logs showed the root cause:
  - `AuthenticationServiceException: An error occurred while attempting to decode the Jwt`
  - `I/O error on GET request for "http://localhost:9000/oauth2/jwks": Connection refused`

This blocks Sprint 15 acceptance criteria 2 and 3 in `docs/backlog/tasks/sprint-15/SB-1-spring-boot-4-migration.md:49-50`, plus the Playwright backstop called out at `docs/backlog/tasks/sprint-15/SB-1-spring-boot-4-migration.md:57`.

Fix direction: remove the explicit `JwtDecoder` bean and let Spring Boot use `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`, or inject that configured property into the bean instead of hardcoding `localhost`.

## Acceptance Scorecard

- `AC1` Boot 4.0.x poms and Maven verify: **PASS**. All four poms use parent `4.0.7` (`auth-server`, `order-service`, `inventory-service`, `shared-model` line 10). Local Maven verifies passed under Java 21 after isolating one transient parallel-run auth compile/classpath failure; live CI is also green.
- `AC2` Resource servers start and REST APIs respond: **FAIL** for the documented containerized stack because JWT validation calls `localhost:9000` inside service containers.
- `AC3` Kafka saga still works end-to-end: **FAIL / blocked**. The Playwright saga cannot reach the initial inventory/order API state because authenticated REST calls fail first.
- `AC4` No `@MockBean` / `@SpyBean`: **PASS** in active source tests. `com.fasterxml.jackson` and classic bridge cleanup: **PARTIAL / accepted caveat**, not clean against the original criterion. The handoff explicitly keeps `spring-boot-starter-classic`, `spring-boot-starter-test-classic`, `spring.jackson.use-jackson2-defaults:true`, and `jackson-datatype-jsr310` (`docs/backlog/sprint-15-handoff.md:44-45`).
- `AC5` Live CI green: **PASS**. GitHub Actions run `28365627533` passed for `17843ea` with all six jobs green.
- `AC6` Caveats documented: **PASS**. The handoff clearly documents the classic/Jackson compatibility shims.

## Dependency / Vulnerability Notes

- Frontend production audits are clean: `npm.cmd audit --omit=dev` returned `found 0 vulnerabilities` in both `order-ui` and `inventory-ui`.
- `npm.cmd outdated` is not actionable here: both UIs are on Angular `22.0.4`, while npm registry `latest` dist-tags reported older Angular majors for several packages.
- Maven Versions Plugin reports a newer Spring train: `spring-boot-starter-parent 4.0.7 -> 4.1.0`, with matching Spring Security/Kafka managed updates. This is not a strict Sprint 15 failure because the task scoped "latest Spring Boot 4.0.x" (`docs/backlog/tasks/sprint-15/SB-1-spring-boot-4-migration.md:10`), but the repo is not globally latest.
- Backend vulnerability status is not mechanically proven. CI currently runs build/test only (`.github/workflows/ci.yml:43-44`, `64-65`, `85-86`, `106-107`) and has no Java CVE scanner such as OWASP Dependency-Check, Snyk, or Dependabot audit gate.

## Verification Run

- `gh run view 28365627533 --repo dmt-dnit/pub-rec-opencode-deepseek --json jobs,url,headSha,conclusion`: **PASS**, all six jobs green at `17843ea`.
- `shared-model`: `.\mvnw.cmd --batch-mode -q clean install`: **PASS**.
- `auth-server`, `order-service`, `inventory-service`: `.\mvnw.cmd --batch-mode -q clean verify`: **PASS** when run individually under Java 21.
- Podman image builds for `auth-server`, `order-service`, and `inventory-service`: **PASS**.
- Angular dev servers on `4200` and `4201`: **PASS**, both served HTTP 200.
- `cd e2e && npm.cmd test`: **FAIL**, blocked at initial inventory table assertion due backend 500s from JWT decoder misconfiguration.
- Generated Playwright artifacts and review-created app containers/processes were cleaned up after evidence capture.
