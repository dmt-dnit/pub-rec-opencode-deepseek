# Sprint 16 Round 2 Review - Boot 4 Cleanup / Swagger / OWASP

Review target: `34f9e7da85e191f4eb24e7acc7b2d4bb397a806a`  
Handoff: `docs/backlog/sprint-16-handoff.md` round-2 section  
Verdict: **REJECT / not cleared**

## Findings

### P1 - OWASP Dependency-Check job completes as a failed job and produces no report artifact

The round-2 CI run completed overall as success only because the OWASP job is `continue-on-error`. The job itself failed:

- Run: `https://github.com/dmt-dnit/pub-rec-opencode-deepseek/actions/runs/28382514498`
- OWASP job: `https://github.com/dmt-dnit/pub-rec-opencode-deepseek/actions/runs/28382514498/job/84088566021`
- `OWASP Dependency-Check (CVSS >= 7 threshold, report-only)=completed:failure`
- Step status: `Scan shared-model=completed:failure`; `Scan auth-server`, `Scan order-service`, and `Scan inventory-service` were skipped.
- Artifact check for run `28382514498` returned no uploaded artifacts, so there is no `dependency-check-report.html` evidence to inspect.

Impact: SEC-1 is not proven. The scanner job exists, but it did not complete the module scans and did not upload the promised reports. This blocks clearing Sprint 16's vulnerability-visibility work even though the job is intentionally report-only.

## Cleared Round-1 Findings

- **SB-3 state is now correct.** All four modules are on Spring Boot `4.1.0`; the bounded search found no remaining `spring-boot-starter-classic`, `spring-boot-starter-test-classic`, or `jackson-datatype-jsr310` entries. Service poms now show `spring-boot-kafka`, `spring-boot-starter-webmvc-test`, and `springdoc-openapi-starter-webmvc-ui` where expected.
- **auth-server OpenAPI is fixed.** Live `GET http://localhost:9000/v3/api-docs` returned HTTP 200. `order-service` and `inventory-service` `/v3/api-docs` also returned HTTP 200. All three `/swagger-ui/index.html` endpoints returned HTTP 200.
- **Containerized smoke is still green.** `cd e2e && npm.cmd test` returned `1 passed (16.6s)` against the rebuilt round-2 backend stack.

## Verification

- `bash scripts/pre-review-check.sh 16`: **PASS**.
- Bounded pom check: **PASS** for Boot 4.1.0 and shim removal.
- Bounded `SecurityConfig` check: **PASS** for exact `/v3/api-docs`, `/v3/api-docs/**`, and `/v3/api-docs.yaml` permits in all three services.
- `bash scripts/startup-all.sh`: **PASS**. Backend rebuilt and started through the Podman fallback.
- Swagger live checks:
  - `auth-server /v3/api-docs`: **200**
  - `order-service /v3/api-docs`: **200**
  - `inventory-service /v3/api-docs`: **200**
  - all three `/swagger-ui/index.html`: **200**
- Authenticated API probe: **PASS** after service warmup.
- Playwright smoke: **PASS**, `1 passed (16.6s)`.
- Admin checks: **PASS**. `NVD_API_KEY` exists as a repo secret and Dependabot alerts are enabled (`GET /vulnerability-alerts` returns HTTP 204).

## Notes

Generated Playwright artifacts and review-created backend containers were cleaned up. The pre-existing Angular dev servers on ports 4200 and 4201 were reused and left running.

