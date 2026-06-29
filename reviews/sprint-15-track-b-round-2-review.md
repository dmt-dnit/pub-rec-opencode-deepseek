# Sprint 15 Round 2 Review - JWKS Decoder Fix

Review target: `13cf8ddb7d0e938fe316a76c5757e1acb9997c5a`  
Handoff: `docs/backlog/sprint-15-handoff.md` round-2 section  
Verdict: **APPROVE / Sprint 15 cleared**

## Findings

No blocking findings.

The round-1 P1 is fixed. `rg` finds no remaining `JwtDecoder`, `NimbusJwtDecoder`, or `jwtDecoder()` bean in either resource-server `SecurityConfig.java`. The services now rely on Boot's OAuth2 resource-server auto-configuration:

- `order-service/src/main/java/com/example/orderservice/config/SecurityConfig.java:32`
- `inventory-service/src/main/java/com/example/inventoryservice/config/SecurityConfig.java:32`

The documented container overrides are now effective:

- `docker-compose.yml:65` and `docker-compose.yml:78`
- `scripts/startup-all.sh:79` and `scripts/startup-all.sh:87`

## Verification

- `bash scripts/pre-review-check.sh 15`: **PASS**.
- `rg -n "NimbusJwtDecoder|jwtDecoder\\(|JwtDecoder"` under `order-service/src/main` and `inventory-service/src/main`: **PASS**, no matches.
- `bash scripts/startup-all.sh`: **PASS**. Recreated the backend stack with Podman fallback from the repo script.
- Direct authenticated API probe: **PASS**.
  - `POST http://localhost:9000/api/auth/login` succeeded for `warehouse1@example.test` and `customer1@example.test`.
  - `GET http://localhost:8081/api/inventory` returned HTTP 200 with seeded SKUs.
  - `GET http://localhost:8080/api/orders` returned HTTP 200 with `[]`.
- `cd e2e && npm.cmd test`: **PASS**.
  - `1 passed (14.7s)`
  - Smoke covered login, initial inventory, order placement, live confirmation, reservation feed, and inventory decrement.
- Bounded service log check after the smoke: **PASS**. No `localhost:9000`, JWT decode, authentication-service, or connection-refused errors found in the recent `order-service` / `inventory-service` logs.
- Live CI: **PASS**. GitHub Actions run `28371179353` passed for `13cf8ddb7d0e938fe316a76c5757e1acb9997c5a`.

## Residual Notes

The previous non-blocking Sprint 15 notes still stand and are correctly tracked for Sprint 16: classic starter bridge, Jackson-2 compatibility shim, Spring Boot 4.1.0 availability, and lack of a Java CVE scanner gate. None block this round-2 fix.

Review-created Playwright artifacts and backend containers were cleaned up after verification. The pre-existing Angular dev servers were reused and left running.

