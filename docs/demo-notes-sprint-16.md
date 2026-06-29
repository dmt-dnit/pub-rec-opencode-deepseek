# Demo Notes - Sprint 16

Sprint 16 is now cleared after three review rounds. It is one of the best conference-story examples because every layer looked plausible at least once, but the independent gate still found mismatches that build-green checks did not expose.

What worked:

- The Boot 4.1.0 / Jackson 3 migration now lands cleanly across `auth-server`, `order-service`, `inventory-service`, and `shared-model`.
- The containerized Playwright smoke passes after the Boot/Jackson/Security changes: login, order placement, live confirmation, reservation feed, and inventory decrement all still work.
- OpenAPI is live on all three services. `/v3/api-docs` and `/swagger-ui/index.html` return 200 for auth, order, and inventory.
- SEC-1 now has working server-side vulnerability visibility through Snyk. The live CI run scanned 7 projects and reported no vulnerable paths found.
- Dependabot vulnerability alerts are enabled, and the required Snyk repo secret is present.

What the review caught:

- Round 1 caught a stale-base integration overwrite: the handoff said the runnable services were on Spring Boot 4.1.0 with the classic/Jackson-2 shims removed, but the actual service poms still showed Boot 4.0.7 plus `spring-boot-starter-classic`, `spring-boot-starter-test-classic`, and `jackson-datatype-jsr310`.
- Round 1 also caught that auth-server Swagger UI loaded while its JSON endpoint `/v3/api-docs` returned 401 because the security matcher permitted `/v3/api-docs/**` but not the bare `/v3/api-docs` path.
- Round 2 caught that the first vulnerability-scanner gate was not actually operational: OWASP Dependency-Check failed at `Scan shared-model`, skipped downstream scans, and produced no artifact.
- Round 3 verified the replacement Snyk gate against live CI, not just YAML. The job authenticated, scanned all projects, exited 0, and uploaded SARIF through the current CodeQL action.

Demo framing:

- This is a clean example of why Codex should verify file evidence and live CI, not handoff prose. The UI smoke passed early, but dependency and security acceptance criteria were still wrong.
- It shows the difference between runtime validation and dependency validation: Playwright proved the saga, but only pom/workflow inspection and CI logs proved the dependency train and scanner gate.
- It also shows a mature agent workflow correction: when OWASP was the wrong CI tool for the project constraints, the team replaced the mechanism while preserving the security outcome.
- The residual policy question is deliberate, not a bug: Snyk is still report-only through `continue-on-error`. A later sprint can decide when to make it a hard gate.
