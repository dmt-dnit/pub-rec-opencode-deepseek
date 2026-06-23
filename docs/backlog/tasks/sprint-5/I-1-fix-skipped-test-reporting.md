# Task I-1: Make the Docker-optional test actually report as skipped

**Resolves:** Must-fix in `reviews/sprint-4-track-a-review.md` ("the acceptance criteria required the Kafka-dependent test to be reported as skipped, not silently hidden. The current Surefire report... records `tests="0"` and `skipped="0"`").

## Context

Sprint 4's `H-2` added `Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), ...)` inside `InventoryIntegrationTest`'s `@BeforeAll`. This does make `mvn test` exit `0` with no container runtime available — but Codex's review found the Surefire XML shows `tests="0"` / `skipped="0"`, not a reported skip. A `@BeforeAll` that throws (which is what `Assumptions.assumeTrue` does on failure) appears to prevent the test class's `@Test` methods from ever being registered with JUnit5's reporting at all, rather than marking them individually as skipped. The practical problem: "0 tests ran" and "tests passed, intentionally skipped" are indistinguishable in a CI dashboard. If a pipeline is supposed to have Docker available and doesn't, this would silently hide that as a no-op rather than surfacing it.

## Task

1. Move the assumption check from the class-level `@BeforeAll` into each individual `@Test` method (or use a JUnit5 `@EnabledIf`/`@DisabledIf` / `ExecutionCondition` extension that evaluates before the test is collected) so the test framework registers the test as **skipped**, not absent.
   - Simplest fix: replace `Assumptions.assumeTrue(...)` in `@BeforeAll` with the same call at the top of `shouldPublishAndReceiveInventoryReservationEvent()` itself. JUnit5 reports a per-test assumption failure as `skipped`, which is what the brief wants.
   - If the container needs to be started before any test method runs (so `@BeforeAll` still needs the assumption to avoid starting Kafka unnecessarily), guard the container startup separately from the test registration — e.g. check `DockerClientFactory.instance().isDockerAvailable()` once in a static initializer to decide whether to even attempt `kafka.start()`, but still call `Assumptions.assumeTrue(...)` inside the `@Test` method so JUnit registers the skip correctly regardless of where the container-start guard lives.
2. After the fix, run `mvn test` with no Docker/Podman available and inspect the actual Surefire XML — confirm it shows the test as `skipped="1"` (or equivalent), not `tests="0"`.
3. Then run it again with a real container engine available, and confirm the test actually executes and passes — this still hasn't been verified by anyone in this sprint chain. If no container engine is available to you either, say so explicitly rather than asserting it works.

## Out of scope
- Don't touch `order-service` — unrelated module (`I-3` covers its Mockito issue separately).
- Don't revert to `@EmbeddedKafka` — the Testcontainers approach itself is correct; this task only fixes how the skip is reported.

## Acceptance criteria
- With no Docker/Podman daemon reachable, `mvn test` exits `0` **and** the Surefire report shows the Kafka-dependent test explicitly skipped (not absent/zero).
- With a container engine reachable, the same test actually runs against a real broker and passes — verified at least once, with the actual command output shown, not just asserted.
