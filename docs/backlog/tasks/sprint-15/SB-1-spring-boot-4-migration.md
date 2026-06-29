# SB-1 — Spring Boot 3.4.3 → 4.0.x migration (all 4 Java modules)

**Sprint:** 15 (Track B Sprint 2)
**Priority:** Must — backend is on an EOL Spring Boot minor (3.x free support ends 2026-06-30)
**Implementer:** TBD by Dimitri — recommended Claude **opus** worktree agent (logic-heavy major upgrade; coordinator verifies per module). opencode+DeepSeek acceptable for an independent take. Isolated worktree, branched from current `main` HEAD (verify the base commit).
**Scope:** `shared-model`, `auth-server`, `order-service`, `inventory-service`. **Do not** touch the Angular UIs. Keep behaviour identical — this is an upgrade, not a feature change.

## Goal

Move all four modules from Spring Boot 3.4.3 to the latest Spring Boot **4.0.x**, Spring Framework 7 / Spring Security 7 / Jackson 3 / JUnit 6, with the JWT SSO flow, the Kafka choreographed saga, and all Sprint 14 hardening (retry/DLQ/idempotency, outcome-idempotency) still working and all tests green.

## Approach — phased, verify per module

Do it in order; `shared-model` must build/install before the others. **Use the two-step bridge** if it reduces churn: first add `spring-boot-starter-classic` (+ `spring-boot-starter-test-classic` for tests) to restore the classpath after the version bump, get green, then remove the classic starters and adopt the modular starters. Show real `./mvnw` output at each gate.

### Phase A — version bump prerequisite
1. First bump the parent to the **latest 3.5.x** in all four poms, build everything, fix any deprecation that's now an error. (Recommended pre-step per the official guide.)
2. Then bump the parent to the **latest 4.0.x**. Java stays 21 (Boot 4 minimum; Enforcer `[21,22)` stays valid).

### Phase B — dependencies / starters (poms)
Apply the Boot 4 renames where used:
- `spring-boot-starter-web` → `spring-boot-starter-webmvc`
- `spring-boot-starter-oauth2-resource-server` → `spring-boot-starter-security-oauth2-resource-server`
- Autoconfigure is modularized — add any now-required `spring-boot-starter-<tech>` and fix moved import packages (`org.springframework.boot.<tech>...`).
- **Jackson 3:** group id `com.fasterxml.jackson` → `tools.jackson` (except `jackson-annotations`). Update any direct Jackson deps/imports.
- Get every module to **compile** (`./mvnw -q -DskipTests compile`). Catalog Tier-1 (won't-build) breakage as you fix it.

### Phase C — runtime: Spring Security 7 (CSRF) + Jackson 3 config
- **Security 7 / CSRF:** CSRF is now **ON by default for API endpoints**. All three services (`auth-server`, `order-service`, `inventory-service`) are stateless JWT resource servers — read each `config/SecurityConfig.java` and ensure CSRF is explicitly disabled (or correctly configured) so the REST APIs don't start returning 403. Adapt any removed/renamed Security 7 DSL methods. The seeded-role / `hasRole('ADMIN')` behaviour in `AdminController` must still pass.
- **Jackson 3 config:** both `order-service` and `inventory-service` `application.yml` set `spring.jackson.serialization.write-dates-as-timestamps: false` — move it to the new `spring.jackson.json.*` location (verify exact key against the guide). Confirm Kafka event JSON still (de)serializes: `JsonSerializer`/`JsonDeserializer`, `spring.json.add.type.headers`, `spring.json.trusted.packages: "com.example.sharedmodel"`, `spring.json.value.default.type`. If Jackson 3 changes bite, `spring.jackson.use-jackson2-defaults=true` is an accepted temporary fallback — note it explicitly if used.
- **spring-kafka:** confirm the Boot-4-managed spring-kafka works with our `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` + `ExponentialBackOffWithMaxRetries(3)` (in each `KafkaTopicConfig`). The retry-topic `backoff.random`→`.jitter` property rename likely doesn't apply (we configure backoff in code) — confirm.
- Gate: each service **starts** and the saga runs (a thrown reservation still routes to `.DLT`; a duplicate is still idempotent).

### Phase D — tests
- **`@MockBean`/`@SpyBean` are removed → `@MockitoBean`/`@MockitoSpyBean`.** Replace across all suites. Known users include the Sprint 14 tests: `OrderEventListenerOutcomeIdempotencyTest`, `OrderEventListenerIdempotencyTest`, `InventoryReservationListenerIdempotencyTest` (and any DLT tests) — grep for `@MockBean`/`@SpyBean` and convert every one. `@MockitoBean` is field-level only (not in `@Configuration`).
- `@SpringBootTest` no longer auto-provides MockMvc / TestRestTemplate — add the slice annotations (`@AutoConfigureMockMvc`, etc.) only where those are actually used.
- Confirm `spring-boot-starter-test` / `spring-kafka-test` / EmbeddedKafka work on Boot 4 (JUnit 6). Our EmbeddedKafka DLT + idempotency tests are the highest-risk suites — get them green without Docker.
- Gate: `./mvnw test` (and `verify`) green in **every** module — paste real output.

### Phase E — Maven build order sanity
- `cd shared-model && ./mvnw clean install` then `clean verify` in `auth-server`, `order-service`, `inventory-service`. All green on Spring Boot 4.0.x.

### Phase G — CI action-version bump (Sprint 14 follow-up 1, folded in)
Since CI must re-verify the new build anyway, bump in `.github/workflows/ci.yml`: `actions/checkout@v4`→`@v5` (or current), `actions/setup-java@v4`→`@v5`, `actions/setup-node@v4`→`@v6`. Verify with the live Actions run (Codex/coordinator). This also clears the Node 20 deprecation caveat.

## Acceptance criteria (observable)

1. All 4 modules' poms are on Spring Boot **4.0.x**; `./mvnw verify` is green in each — paste the real per-module test summaries + `BUILD SUCCESS`.
2. The three resource servers start and their REST APIs respond (no CSRF 403 regression); `hasRole('ADMIN')` admin path still works.
3. Kafka saga still works end-to-end and events cross the Jackson 3 boundary intact; the Sprint 14 DLT + idempotency + outcome-idempotency tests pass (now under `@MockitoBean`).
4. `grep -rn "@MockBean\|@SpyBean"` returns nothing in `src/test`; no `com.fasterxml.jackson` group-id deps remain except `jackson-annotations`.
5. CI green on the new build with bumped action majors (live run — Codex-only if the implementer can't push).
6. State explicitly anything that couldn't be run, and any use of the `use-jackson2-defaults` / `starter-classic` fallbacks.

## Notes
- This is large and high-risk — work phase by phase, keep the saga behaviour identical, and don't silently weaken any Sprint 14 test to make it pass under the new framework (if a migrated test fails, fix the cause).
- Coordinator will verify per-module by reading the diff and re-running `mvnw verify`; the Playwright smoke (Codex) is the end-to-end backstop.
