# Sprint 16 Round 3 Review - Boot 4 Cleanup / Swagger / Snyk

Review target: `2fdf8885ba27bf2fc528b41e7fcd7b04eff95092`  
Handoff: `docs/backlog/sprint-16-handoff.md` round-3 section  
Verdict: **APPROVE / Sprint 16 cleared**

## Findings

No blocking findings.

The round-2 blocker is fixed. The failed OWASP Dependency-Check job was replaced by a Snyk SCA job that runs across the Maven and npm projects, authenticates with the repo secret, exits 0, and uploads SARIF through `github/codeql-action/upload-sarif@v4`.

## Cleared Prior Findings

- **SB-3 dependency state remains correct.** `auth-server`, `order-service`, `inventory-service`, and `shared-model` are all on Spring Boot `4.1.0`. Bounded searches found no remaining `spring-boot-starter-classic`, `spring-boot-starter-test-classic`, or `jackson-datatype-jsr310` entries in the service poms.
- **OpenAPI access remains fixed.** All three services permit exact `/v3/api-docs`, `/v3/api-docs/**`, and `/v3/api-docs.yaml`. Live checks returned HTTP 200 for all `/v3/api-docs` and `/swagger-ui/index.html` endpoints.
- **SEC-1 now has a working live scanner.** GitHub Actions run `28387082449` completed successfully for `2fdf8885ba27bf2fc528b41e7fcd7b04eff95092`; the `Snyk SCA (HIGH+ threshold, report-only)` job completed successfully and the log reports `Tested 7 projects, no vulnerable paths were found`.
- **Admin state is in place.** `SNYK_TOKEN_CLAUDE_CODE_CLI` and `NVD_API_KEY` are present as repo secrets, and Dependabot vulnerability alerts are enabled.

## Verification

- `bash scripts/pre-review-check.sh 16`: **PASS**.
- Bounded workflow check: **PASS**. `.github/workflows/ci.yml` contains `snyk-security`, uses `SNYK_TOKEN_CLAUDE_CODE_CLI`, runs `snyk test --all-projects --severity-threshold=high --sarif-file-output=snyk.sarif`, and uploads `snyk.sarif` with `upload-sarif@v4`.
- Live CI: **PASS**. Latest CI run for `2fdf8885ba27bf2fc528b41e7fcd7b04eff95092` completed successfully, including the Snyk job.
- Live Swagger checks: **PASS**.
  - `auth-server /v3/api-docs`: HTTP 200
  - `order-service /v3/api-docs`: HTTP 200
  - `inventory-service /v3/api-docs`: HTTP 200
  - all three `/swagger-ui/index.html`: HTTP 200
- Authenticated API probe: **PASS**. JWT-backed `GET /api/inventory` and `GET /api/orders` returned HTTP 200.
- Playwright smoke: **PASS**. `cd e2e && npm.cmd test` returned `1 passed (15.5s)`.

## Residual Notes

The Snyk job is still configured as report-only with `continue-on-error`. That does not block Sprint 16 because this live run exits 0 and proves the scanner works, but Track B should decide later whether to turn it into a hard gate once the dev-only Angular tooling CVE policy is settled.

Generated Playwright artifacts and review-created backend containers were cleaned up. The pre-existing Angular dev servers on ports 4200 and 4201 were reused and left running.
