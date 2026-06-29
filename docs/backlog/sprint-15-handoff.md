# Sprint 15 (Track B Sprint 2) — Handoff to Codex

**Coordinator:** Claude Code. **Date:** 2026-06-29.
**Task:** SB-1 — Spring Boot 3.4.3 → 4.0.x migration (all 4 Java modules).
**Implementer:** opencode + DeepSeek (`deepseek-v4-pro`), driven headless from the coordinator; I verified by reading the diff and **re-running `mvnw verify` myself** on a clean checkout.

## Why this sprint

Dependency-currency check (cadence step 1, 2026-06-29) found the backend on **Spring Boot 3.4.3 — an EOL minor**; the whole 3.x line's free support ends 2026-06-30. Dimitri chose a full 4.x migration now. Angular 22 + Java 21 confirmed current.

## Commits (on `main`, fast-forwarded)

| SHA | Phase |
|-----|-------|
| `075412f` | A1 — bump parent to 3.5.16 (prerequisite) |
| `4de86c3` | A2/B/D — bump to **4.0.7** (classic bridge), `@MockBean`→`@MockitoBean`, Kafka/Jackson3/test-dep fixes |
| `99ea2d9` | C — remove stale security auto-config excludes from test yml |
| `f190f68` | G — CI actions bumped (checkout@v5, setup-java@v5, setup-node@v6) |

`git status --short`: clean. 15 files changed (4 poms, 2 service `application.yml`, 2 test `application-test.yml`, 7 test classes, `ci.yml`). No source-logic change to controllers/services/listeners.

## Independent verification (my own clean `mvnw verify`, Java 21, not DeepSeek's report)

```
shared-model       exit=0  BUILD SUCCESS
auth-server        exit=0  BUILD SUCCESS
order-service      exit=0  BUILD SUCCESS  — Tests run: 6, Failures: 0, Errors: 0
   InventoryReservationListenerDltTest 1 · ...IdempotencyTest 1 · OrderEventIntegrationTest 3 · OrderEventPublisherTest 1
inventory-service  exit=0  BUILD SUCCESS  — Tests run: 5, Failures: 0, Errors: 0, Skipped: 1
   OrderEventListenerDltTest 1 · ...IdempotencyTest 1 · OrderEventListenerOutcomeIdempotencyTest 2 · InventoryIntegrationTest skipped (Docker)
```

All Sprint 14 hardening tests (retry/DLQ, idempotency, outcome-idempotency) pass under Boot 4 + `@MockitoBean`.

## What changed (key surfaces)

- **Poms:** parent → `4.0.7`; `spring-boot-starter-web` → `spring-boot-starter-webmvc` (+ classic bridge, see below); `spring-boot-starter-test` → `spring-boot-starter-test-classic`; explicit Testcontainers `1.21.3` (Boot 4 dropped its BOM); `jackson-datatype-jsr310` added; `spring-security-test` (order-service).
- **Security 7 / CSRF:** the three `SecurityConfig.java` are **unchanged** — they already had `.csrf(csrf -> csrf.disable())`, which is still valid Security 7 DSL, so the CSRF-on-by-default change does not regress the resource-server APIs.
- **Jackson 3:** `spring.jackson.serialization.write-dates-as-timestamps:false` replaced with `spring.jackson.use-jackson2-defaults:true` (see shims). Kafka `spring.json.*` config unchanged; events still (de)serialize (proved by the DLT/integration tests).
- **Tests:** `@MockBean`→`@MockitoBean` (new package `org.springframework.test.context.bean.override.mockito`), `AutoConfigureMockMvc` import package moved, EmbeddedKafka switched off hardcoded port 9095 to a random port. **Assertions unchanged — no test weakened** (verified line-by-line: `verify(...times(1))` / `assertThat(...)` intact).

## Declared shims (within scope — the brief pre-authorized these fallbacks)

1. **`spring-boot-starter-classic` + `spring-boot-starter-test-classic`** — the official two-step bridge; restores the full classpath on 4.0. The "remove classic + adopt fully modular starters" follow-up was **not** completed.
2. **`spring.jackson.use-jackson2-defaults:true`** + Jackson-2 `jsr310` datatype — runs Jackson with 2.x-compatible behavior rather than a clean Jackson-3 wiring.

Net: it is genuinely on supported **Boot 4.0.7** with identical behavior, but in compatibility mode, not the fully-modernized end state. Accepted by Dimitri (2026-06-29) with a Sprint 16 follow-up to remove the shims. **Acceptance criterion #4 (no `com.fasterxml.jackson` except annotations; modular starters) is intentionally not met** for this reason.

## Codex-only / not verified here

- **Live CI run:** commits are local until pushed; the action-version bump (v5/v5/v6) needs a live Actions run to confirm green. Push then `gh run watch`.
- **End-to-end runtime:** I verified backend build + tests, not the full running stack. The **Playwright smoke** (login → order → live saga → inventory decrement) is the authoritative end-to-end check under Boot 4 / Security 7 / Jackson-2-compat — please run it. SecurityConfigs and `spring.json.*` are unchanged and behavior is preserved by design, so no regression is expected, but the live saga + JWT flow is Codex's backstop.

## Pre-review
`bash scripts/pre-review-check.sh 15` — passes (clean tree, this handoff present).
