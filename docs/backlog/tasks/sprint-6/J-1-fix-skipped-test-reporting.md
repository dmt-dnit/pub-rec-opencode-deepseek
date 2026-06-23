# Task J-1: Make the Docker-optional test report as skipped, not disappear

**Resolves:** Must-fix in `reviews/sprint-5-track-a-review.md` ("the current Surefire report ... still records `tests=\"0\"` and `skipped=\"0\"` when no Docker runtime is available").

## Context

Sprint 4's Docker-optional change stopped `inventory-service` from hard-failing when no container runtime is reachable, which was useful progress. But the current implementation still puts the assumption inside `@BeforeAll`:

- `inventory-service/src/test/java/com/example/inventoryservice/InventoryIntegrationTest.java:52-57`

That means JUnit never registers the test method as skipped. The current Surefire XML still shows:

- `inventory-service/target/surefire-reports/TEST-com.example.inventoryservice.InventoryIntegrationTest.xml:2`

with `tests="0"` / `skipped="0"`. A CI dashboard cannot distinguish that from "nothing ran."

## Task

1. Move the Docker-availability assumption out of the class-level `@BeforeAll` path that suppresses test registration.
   - The simplest fix is to call `Assumptions.assumeTrue(...)` at the top of `shouldPublishAndReceiveInventoryReservationEvent()`.
   - If you still need to guard `kafka.start()` before any test method runs, separate the "should we start Kafka?" guard from the per-test "should this test be reported as skipped?" guard.
2. Re-run `mvn test` with no Docker or Podman daemon reachable and inspect the generated Surefire XML.
   - Confirm it now reports the test as skipped (`skipped="1"` or equivalent), not `tests="0"`.
3. Re-run the same test with a real container runtime available and confirm it actually executes and passes.
   - If you do not have Docker or Podman available either, say so explicitly instead of claiming the positive path was verified.

## Out of scope

- Do not change `order-service`; that module's Mockito/OpenJ9 fallback is no longer the active carryover.
- Do not replace Testcontainers with `@EmbeddedKafka`.

## Acceptance criteria

- With no Docker or Podman daemon reachable, `mvn test` exits `0` and the Surefire report explicitly records the test as skipped.
- With a container runtime reachable, the same test actually runs and passes against a real broker, or the lack of such an environment is stated explicitly.
