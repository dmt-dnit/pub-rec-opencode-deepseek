# Sprint 16 Review - Boot 4 Cleanup, OpenAPI, Vulnerability Visibility

Review target: `b3ffe39e74737408ef66aff695a0388b0dd1f76d`  
Handoff: `docs/backlog/sprint-16-handoff.md`  
Verdict: **REJECT / not cleared**

## Findings

### P1 - SB-3 did not actually remove the Boot 4 compatibility shims from the services

The handoff claims the repo is on Spring Boot 4.1.0 with no `spring-boot-starter-classic`, no `spring-boot-starter-test-classic`, and no Jackson-2 `jackson-datatype-jsr310`. That is false for the three runnable services.

Evidence:

- `auth-server/pom.xml:10` still uses Spring Boot parent `4.0.7`.
- `auth-server/pom.xml:27` still has `spring-boot-starter-classic`.
- `order-service/pom.xml:10` still uses Spring Boot parent `4.0.7`.
- `order-service/pom.xml:27` still has `spring-boot-starter-classic`.
- `order-service/pom.xml:39` still has `jackson-datatype-jsr310`.
- `order-service/pom.xml:79` still has `spring-boot-starter-test-classic`.
- `inventory-service/pom.xml:10` still uses Spring Boot parent `4.0.7`.
- `inventory-service/pom.xml:27` still has `spring-boot-starter-classic`.
- `inventory-service/pom.xml:39` still has `jackson-datatype-jsr310`.
- `inventory-service/pom.xml:79` still has `spring-boot-starter-test-classic`.
- Only `shared-model/pom.xml:10` is on Spring Boot `4.1.0`.

Impact: SB-3's core acceptance is not met. The running services are still on the Sprint 15 compatibility bridge and Spring Security 7.0.6 train, so the handoff's "Boot 4.1.0 / Security 7.1.0 / no Jackson-2 shim" security and dependency-currency conclusion does not apply to the actual services.

### P1 - auth-server OpenAPI document endpoint is still protected

The live containerized check shows:

- `GET http://localhost:9000/v3/api-docs` returns `401` with a Bearer challenge.
- `GET http://localhost:8080/v3/api-docs` returns `200`.
- `GET http://localhost:8081/v3/api-docs` returns `200`.
- `GET /swagger-ui/index.html` returns `200` on all three services.
- `GET /v3/api-docs/swagger-config` returns `200` on all three services.

The auth-server security matcher is:

- `auth-server/src/main/java/com/example/authserver/config/SecurityConfig.java:71` permits `"/v3/api-docs/**"`.
- `auth-server/src/main/java/com/example/authserver/config/SecurityConfig.java:73` makes every other request authenticated.

The bare `/v3/api-docs` path does not match `"/v3/api-docs/**"`, so the Swagger UI shell loads but points to a JSON document that the browser cannot fetch anonymously. Fix direction: permit both `"/v3/api-docs"` and `"/v3/api-docs/**"` in all services for consistency.

### P2 - OWASP CI job is present but not yet a completed live green gate

The new OWASP job exists in `.github/workflows/ci.yml:174-252`, uses `secrets.NVD_API_KEY`, and uploads HTML reports. The live CI run for `b3ffe39` is still `in_progress`; the OWASP job is still `in_progress` on `Scan shared-model`.

The normal CI build/test jobs are green, but SEC-1's live scanner behavior is not yet proven. Because the job is `continue-on-error: true` at `.github/workflows/ci.yml:180`, even a completed green workflow would still need report inspection before treating it as a real vulnerability gate.

## Passed Checks

- `bash scripts/pre-review-check.sh 16`: **PASS**.
- `bash scripts/startup-all.sh`: **PASS**. Containerized backend rebuilt and started through the repo Podman fallback.
- Direct authenticated API probe: **PASS**. Login worked for customer and warehouse users; `GET /api/inventory` and `GET /api/orders` returned HTTP 200.
- Containerized Playwright smoke: **PASS**. `cd e2e && npm.cmd test` returned `1 passed (18.0s)`.
- Swagger UI shell: **PASS** on all three services via `/swagger-ui/index.html`.
- Swagger JSON: **PASS** for order-service and inventory-service, **FAIL** for auth-server as noted above.
- Admin settings: **DONE**. `NVD_API_KEY` exists as a repo secret, and Dependabot vulnerability alerts are now enabled (`GET /vulnerability-alerts` returns HTTP 204).

## Notes

Generated Playwright artifacts were removed after the run. Review-created backend containers were not left running. The pre-existing Angular dev servers on ports 4200 and 4201 were reused and left running.

