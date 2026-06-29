# Demo Notes - Sprint 16

Sprint 16 is another useful review-story moment, but not a cleared sprint.

What worked:

- The containerized Playwright smoke passed after the Boot/Jackson changes currently in the repo: login, order placement, live confirmation, reservation feed, and inventory decrement all still work.
- `order-service` and `inventory-service` expose `/v3/api-docs` successfully.
- `NVD_API_KEY` is present as a repo secret, and Dependabot vulnerability alerts are now enabled.

What the review caught:

- The handoff said the runnable services were on Spring Boot 4.1.0 with the classic/Jackson-2 shims removed, but the actual service poms still show Boot 4.0.7 plus `spring-boot-starter-classic`, `spring-boot-starter-test-classic`, and `jackson-datatype-jsr310`.
- The auth-server Swagger UI loads, but its JSON document endpoint `/v3/api-docs` returns 401 because the security matcher permits `/v3/api-docs/**` but not the bare `/v3/api-docs` path.
- The OWASP CI job exists but was still in progress during review, so the vulnerability scan is not yet a proven green gate.

Demo framing:

- This is a clean example of why Codex should verify file evidence, not handoff prose. The UI smoke passed, but the dependency/security acceptance criteria were still wrong.
- It also shows a useful split between runtime validation and dependency validation: Playwright can prove the saga, but it cannot prove the services are actually on the intended dependency train.

