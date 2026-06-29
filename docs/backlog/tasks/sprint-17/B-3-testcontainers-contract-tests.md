# B-3 — Testcontainers integration tests + event contract test

**Sprint:** 17 (Track B Sprint 4)
**Priority:** Should — Track B backlog
**Implementer:** Claude sonnet worktree agent (or opencode+DeepSeek). Branch from current `main`; apply diff + verify state.
**Scope:** `order-service` + `inventory-service` test sources; `shared-model` (or a small shared test) for the contract test; possibly poms (Testcontainers deps). No main-code changes.

## Goal
Add real-Kafka (Testcontainers) integration tests alongside the existing EmbeddedKafka tests, and a contract test that catches accidental drift in the `OrderPlacedEvent` / `InventoryReservationEvent` JSON between the two services.

## Context
- Current Kafka tests use **EmbeddedKafka** (no Docker) — keep those; they're fast and run without a Docker daemon.
- `inventory-service` already has `InventoryIntegrationTest` using Testcontainers (Testcontainers `1.21.3`, `org.testcontainers:kafka`), **skipped locally without Docker**. Use it as the pattern.
- GitHub Actions `ubuntu-latest` runners **have Docker**, so Testcontainers tests run in CI. They are skipped (not failed) where Docker is absent.

## What to do

### 1. Testcontainers integration test per service
For `order-service` and `inventory-service`, add a Testcontainers-backed integration test (real Kafka container via `org.testcontainers:kafka`, `@DynamicPropertySource` wiring `spring.kafka.bootstrap-servers` to the container) that exercises the **real** producer + consumer path:
- order-service: publish `OrderPlacedEvent` to a real broker and assert it lands on `order-events`; consume a real `InventoryReservationEvent` and assert the order status transitions.
- inventory-service: consume a real `OrderPlacedEvent`, assert stock decremented + `InventoryReservationEvent` published to `inventory-events`.
- Gate the Testcontainers tests so they **skip cleanly when Docker is unavailable** (the existing `InventoryIntegrationTest` pattern — `@EnabledIfDockerAvailable` or Testcontainers' built-in `DockerClientFactory.instance().isDockerAvailable()` assumption that produces a skip, not a hard error). Do not break `./mvnw test` on a Docker-less machine.

### 2. Event contract test
Add a contract test (in `shared-model` test sources, or a small test visible to both) that serializes `OrderPlacedEvent` and `InventoryReservationEvent` to JSON and deserializes them back, asserting the round-trip and the exact field set — so a field rename/removal on one side that isn't matched on the other fails the build. Since both services share `shared-model` for these records, the test lives best in `shared-model`. Use the **same Jackson 3** mapper config the services use (records, `Instant` fields). Assert the JSON shape (field names) explicitly, not just round-trip equality, so drift is caught.

## Acceptance criteria (observable)
1. `./mvnw test` (or `verify`) runs the Testcontainers suite **with Docker present** and passes — show output. **Without Docker**, those tests **skip** (not fail) and the rest pass. State which environment you ran in.
2. The contract test passes; deliberately renaming a field in one event record (locally, to prove it) makes the contract test fail — describe this check (you don't have to commit the broken state).
3. Existing EmbeddedKafka + idempotency/DLT tests still pass.
4. CI (which has Docker) runs the Testcontainers tests — note this is verified live by Codex/CI if you can't run Docker here.

## Notes
- Don't remove the EmbeddedKafka tests — augment. They're the Docker-free fast path.
- Keep Testcontainers `1.21.3` (already pinned in inventory-service) consistent; add the same to order-service's pom if needed.
- Testcontainers tests are slow — acceptable; they run in CI and skip locally without Docker.
