# Sprint 15 (Track B Sprint 2) — Spring Boot 3.4 → 4.x migration

**Track:** B — hardening. **Why a whole sprint:** dependency-currency check (cadence step 1) on 2026-06-29 found the backend pinned to **Spring Boot 3.4.3, an EOL minor** — the entire 3.x line's free OSS support ends **June 30, 2026**. Current is 3.5.16 / 4.1.0. This is the silent-drift failure the check exists to catch; the fix is now a **major** upgrade (Spring Framework 7), so it gets its own sprint. Decided with Dimitri (2026-06-29): full 4.x migration now (no separate spike).

## Currency snapshot (live-checked 2026-06-29)

| Stack | Pinned | Status | Action |
|-------|--------|--------|--------|
| Spring Boot | 3.4.3 | **EOL** — 3.x free support ends 2026-06-30; current 4.1.0 | **migrate to 4.x (this sprint)** |
| Java | 21 (LTS) | fine (to 2029+); Boot 4 min is Java 21 | none |
| Angular | 22.0.2 | current (active to Dec 2026) | none |
| GitHub Actions | checkout@v4 / setup-java@v4 / setup-node@v4 | newer majors (v7/v5/v6) | folded into this sprint (low-risk, see SB-1 §G) |

Sources: [endoflife.date/spring-boot](https://endoflife.date/spring-boot), [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide), [endoflife.date/angular](https://endoflife.date/angular).

## Scope

Migrate all four Maven modules (`shared-model`, `auth-server`, `order-service`, `inventory-service`) from Spring Boot 3.4.3 to the latest Spring Boot 4.0.x, keeping the system functionally identical: the JWT SSO flow, the Kafka choreographed saga, and all Sprint 14 hardening (retry/DLQ/idempotency) must still pass. Angular UIs are untouched.

## Known breaking changes that hit THIS stack (from the official guide)

The 3.5→4.0 jump carries ~115 breaking changes; these are the ones our code touches:

1. **Prereq:** upgrade to latest **3.5.x** first, then 4.0.x. Consider the two-step `spring-boot-starter-classic` / `spring-boot-starter-test-classic` bridge to restore the classpath, then remove it.
2. **Starter renames (poms):** `spring-boot-starter-web` → `spring-boot-starter-webmvc`; `spring-boot-starter-oauth2-resource-server` → `spring-boot-starter-security-oauth2-resource-server`. Autoconfigure jar is modularized — each tech is its own `spring-boot-<tech>` module/starter.
3. **Spring Security 7:** **CSRF is now ON by default for API endpoints.** Our three services are stateless JWT resource servers — CSRF must be explicitly disabled (or the APIs will 403). Review every `config/SecurityConfig.java`; the Security 7 DSL may also have removed/renamed methods.
4. **Jackson 3.0:** group id `com.fasterxml.jackson` → `tools.jackson` (except `jackson-annotations`). Affects our Kafka event JSON path (`JsonSerializer`/`JsonDeserializer`, `spring.json.*` props) and any direct Jackson use. Property moves: `spring.jackson.serialization.*` / `read.*` / `write.*` reorganize under `spring.jackson.json.*` — both services set `spring.jackson.serialization.write-dates-as-timestamps: false`, which must move. `spring.jackson.use-jackson2-defaults=true` is an available fallback if needed.
5. **spring-kafka:** retry config moved Spring-Retry → Spring Framework; `spring.kafka.retry.topic.backoff.random` → `.jitter` (we configure backoff in code, so likely n/a — verify). Confirm the Boot-4-managed spring-kafka + spring-kafka-test versions work with our `DefaultErrorHandler`/`DeadLetterPublishingRecoverer`/EmbeddedKafka.
6. **Tests:** **`@MockBean`/`@SpyBean` are REMOVED → `@MockitoBean`/`@MockitoSpyBean`.** Our Sprint 14 tests use `@MockBean` (e.g. `OrderEventListenerOutcomeIdempotencyTest`, the idempotency tests). `@SpringBootTest` no longer auto-provides MockMvc/TestRestTemplate (add slice annotations if used). JUnit 6 baseline.
7. **Min Java 21** — already satisfied.

## Task

One brief — this is a single tightly-coupled migration (a per-module split can't be verified independently because `mvnw verify` needs the tests to compile, and the tests depend on the migrated main code):

- **SB-1** — `tasks/sprint-15/SB-1-spring-boot-4-migration.md` — the full 4.x migration, phased (deps → compile → security/Jackson runtime → tests), with the CI action-version bump (Sprint 14 follow-up 1) folded in since CI must re-verify the new build anyway.

The other Sprint 14 follow-ups (inventory exactly-once/outbox hardening; server-side vuln scanning) and remaining Track B items (B-2, B-3, B-4, B-6) stay in the backlog for Sprint 16+.

## Risk & implementer

High-risk major upgrade across 4 modules with security + serialization + test-framework surface area. This hits rule-5 triggers (ambiguous architecture, genuine second-opinion value). Recommended implementer: **Claude opus worktree agent** (logic-heavy, and the coordinator can intervene/re-verify per module) — or opencode+DeepSeek for an independent take. Confirm with Dimitri before dispatch. Verification is per-module `mvnw verify` green + the full CI run green + (Codex) the Playwright smoke still passing end-to-end.

## Acceptance (sprint-level)

1. All 4 modules build on Spring Boot 4.0.x; `mvnw verify` green in each (real output).
2. JWT SSO flow unchanged; the three resource servers validate tokens and do not 403 on their APIs (CSRF handled).
3. Kafka saga end-to-end still works (order → reserve → confirm/reject) with events serializing/deserializing across the Jackson 3 boundary.
4. All Sprint 14 hardening tests (retry/DLQ/idempotency, outcome-idempotency) still pass under `@MockitoBean`.
5. CI green on the new build, with bumped action majors.
6. `pre-review-check.sh 15` passes.
