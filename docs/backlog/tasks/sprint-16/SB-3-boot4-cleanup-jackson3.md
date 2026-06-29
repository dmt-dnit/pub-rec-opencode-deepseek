# SB-3 — Boot-4 modernization: drop compatibility shims, adopt real Jackson 3, bump 4.1.0

**Sprint:** 16 (Track B Sprint 3)
**Priority:** Must — removes the Sprint 15 tech-debt; **highest-risk task** (touches saga event serialization)
**Implementer:** opencode+DeepSeek (continuity with the SB-1 migration; rule-5 genuine-second-opinion on serialization correctness) or Claude opus. **opencode worktree must be OUTSIDE `.claude/` and force `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`** (default java is 25); build each module as its own command (no `cd ../` chains).
**Scope:** the 4 Java modules' poms + the two service `application.yml`. No controller/listener/service logic changes expected.

## Background — what Sprint 15 left

Sprint 15 reached Spring Boot 4.0.7 via the official two-step bridge, deliberately leaving compatibility shims (declared in `docs/backlog/sprint-15-handoff.md`):
- `spring-boot-starter-classic` + `spring-boot-starter-test-classic` (restore the 3.x-style classpath)
- `spring.jackson.use-jackson2-defaults: true` (Jackson 2 behavior)
- `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` (Jackson 2 java-time module)

This task removes all three to land a clean modular / Jackson-3 stack.

## What to do

### 1. Bump 4.0.7 → 4.1.0
Parent version in all 4 poms. Java stays 21.

### 2. Drop the classic bridge, adopt modular starters
- Remove `spring-boot-starter-classic` and `spring-boot-starter-test-classic` from every pom.
- The classic bridge was masking which modular starters are actually needed. Add the explicit Boot-4 modular starters each service uses, e.g. `spring-boot-starter-webmvc`, `spring-kafka`, `spring-boot-starter-security` + `spring-boot-starter-security-oauth2-resource-server`, `spring-boot-starter-data-jpa`, `spring-boot-starter-actuator`, `spring-boot-starter-websocket`, `spring-boot-starter-test` (+ `spring-kafka-test`, `spring-security-test`, testcontainers where used). Compile + run tests to discover anything missing — let failures tell you which module/import is absent, don't guess.

### 3. Real Jackson 3
- Remove `spring.jackson.use-jackson2-defaults: true` from both service `application.yml`.
- Remove the `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` dependency — **Jackson 3's auto-configured `JsonMapper` handles Java-time (our `Instant` fields) natively; no separate datatype module is needed.**
- Verify no remaining `com.fasterxml.jackson` group-id dependency except `jackson-annotations` (`mvn dependency:tree | grep com.fasterxml.jackson`).
- **Behavior watch:** `use-jackson2-defaults:true` also enabled `FAIL_ON_UNKNOWN_PROPERTIES`; removing it adopts Jackson 3 defaults. Confirm the Kafka event round-trip still works — `OrderPlacedEvent` / `InventoryReservationEvent` (records, with `Instant` fields) must serialize and deserialize across the wire exactly as before. The existing DLT + idempotency + integration tests exercise this; they are the safety net. If a real Jackson-3 default breaks the contract, fix it explicitly (e.g. a targeted `spring.jackson.json.*` property) — do not re-enable the blanket `use-jackson2-defaults` fallback.

### 4. Kafka serialization
Spring Kafka on Boot 4.1 uses Jackson 3. Confirm `JsonSerializer`/`JsonDeserializer` + `spring.json.add.type.headers` / `spring.json.trusted.packages` / `spring.json.value.default.type` still work. The trusted-packages value (`com.example.sharedmodel`) is unchanged.

## Acceptance criteria (observable)

1. No `spring-boot-starter-classic`, `spring-boot-starter-test-classic`, `use-jackson2-defaults`, or `jackson-datatype-jsr310` anywhere (`grep -rn` proves it). All 4 poms on `4.1.0`.
2. `mvn dependency:tree` shows no `com.fasterxml.jackson` group except `jackson-annotations`.
3. `cd shared-model && ./mvnw -DskipTests install` then `./mvnw verify` green in auth-server, order-service, inventory-service (Java 21) — paste real per-module BUILD + `Tests run` output. All Sprint 14/15 hardening tests pass (DLT, idempotency, outcome-idempotency) — proving the Jackson-3 event round-trip.
4. Commit per logical step. Report SHAs, files changed, real verify output, and the dependency:tree grep.
5. State explicitly: the **containerized Playwright smoke is Codex-only** (no Docker/browser here) — Jackson default changes can affect API JSON, so flag it for Codex re-run.

## Notes
- Do not change controller/listener/service logic — this is a dependency/config cleanup. If a test needs adjusting for a Jackson-3 default, adjust the config, not the test's assertions.
- This is the risky one: the saga's correctness rides on the event JSON contract. Coordinator will re-run `mvnw verify` independently and read the dependency:tree; Codex re-runs the live smoke.
