# Task H-2: Make `inventory-service`'s Testcontainers test skip gracefully without Docker

**Resolves:** Must-fix 2 in `reviews/sprint-3-track-a-review.md` ("`mvn -q test` now fails with `Could not find a valid Docker environment.` This replaces the OpenJ9 embedded-Kafka crash with a hard Docker dependency that is not satisfied here").

## Context

Sprint 3's `G-3` correctly diagnosed and fixed the original problem (an in-process `@EmbeddedKafka` broker crashing the JVM's JIT on OpenJ9) by moving to a Testcontainers-backed real Kafka broker. That part of the fix was right. But it introduced a new hard failure mode: if no Docker (or Podman, see below) daemon is reachable, `InventoryIntegrationTest` now fails the build outright instead of the old crash. Codex's review environment has no Docker available, so the net effect for that environment is still "this module's tests don't pass," just for a different reason — the sprint's actual goal (a verifiable `mvn test` in the reviewer's environment) is still unmet.

This repo's own conventions are mixed here — Dimitri's local environment uses **Podman**, not Docker, which Testcontainers can work with but needs explicit configuration (`DOCKER_HOST` pointed at the Podman socket, often `TESTCONTAINERS_RYUK_DISABLED=true` since rootless Podman doesn't run the Ryuk cleanup container the same way). Codex's environment apparently has neither. Rather than trying to guarantee a container engine is always present everywhere this test might run, make the test degrade gracefully.

## Task

1. Gate `InventoryIntegrationTest`'s container startup behind a runtime check using Testcontainers' own availability probe:
   ```java
   import org.junit.jupiter.api.Assumptions;
   import org.testcontainers.DockerClientFactory;

   @BeforeAll
   static void setUp() {
       Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
               "No container runtime available — skipping Testcontainers-based test");
       kafka.start();
   }
   ```
   `Assumptions.assumeTrue` with a false condition makes JUnit 5 report the test as **skipped**, not failed — `mvn test` exits 0 for this class instead of erroring the whole build.
2. Confirm this still genuinely runs and passes when a container engine *is* available (don't let the assumption silently mask a real future regression — test this locally if you have Docker or Podman configured for Testcontainers).
3. Document in the test class (a one-line comment is enough) that this test is skipped without a container runtime, so a future reader doesn't mistake "skipped" for "passing."
4. If Podman is what's actually available in this task's execution environment, configure Testcontainers for it (`DOCKER_HOST`/`TESTCONTAINERS_RYUK_DISABLED` as needed, via a `.testcontainers.properties` file or environment setup) and confirm the test actually runs against a real Podman-backed broker at least once, rather than only confirming the skip path.

## Out of scope
- Don't revert to `@EmbeddedKafka` — that re-introduces the OpenJ9 JIT segfault this was meant to fix. The Testcontainers approach is correct; it just needs to not hard-fail when no engine is present.
- Don't add Docker/Podman installation or CI provisioning in this task — that's a Sprint-4-or-later CI concern (`B-5` in `docs/backlog/sprint-1.md`), not this test's job.

## Acceptance criteria
- `mvn -q test` for `inventory-service` exits `0` in an environment with no Docker/Podman daemon, with the Kafka-dependent test reported as **skipped**, not silently removed or hidden.
- `mvn -q test` still actually exercises the real Testcontainers-backed broker and passes its assertions when a container engine is available — verify this at least once, don't only prove the skip path.
- The rest of `inventory-service`'s test suite (anything not depending on this specific test) is unaffected.
