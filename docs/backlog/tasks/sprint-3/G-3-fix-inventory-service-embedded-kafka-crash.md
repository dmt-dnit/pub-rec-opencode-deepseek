# Task G-3: Fix `inventory-service` test crash on OpenJ9 — forked JVM segfault during `@EmbeddedKafka` startup

**Resolves:** Must-fix 2 (inventory-service half) in `reviews/sprint-2-track-a-review.md` ("`mvn -q test` under the same Semeru/OpenJ9 21.0.6 crashes the forked JVM with a segmentation fault inside `j9jit29.dll` while the integration test starts").

## Context

`InventoryIntegrationTest.java:33` uses `@EmbeddedKafka`, which boots a real Kafka broker (Scala/JVM, including its own JIT-heavy code paths) **inside the same forked test JVM**. On Codex's IBM Semeru/OpenJ9 21.0.6 build, this crashes the JVM itself (`j9jit29.dll`, i.e. OpenJ9's JIT compiler) before the test can even run — this is a JVM-vendor-specific native crash, not a Mockito issue like G-2, and not something a mocking-library config change will fix.

This is harder to fix portably than G-2 because the failure is inside the embedded broker's own JIT compilation, not in this project's code. The most durable fix is to stop running a full embedded Kafka broker inside the test JVM at all.

## Task

1. Replace `@EmbeddedKafka` in `InventoryIntegrationTest.java` with a **Testcontainers**-backed Kafka broker (`org.testcontainers:kafka`), which runs the broker in a separate Docker container/process rather than in-process in the same JVM that's running the JIT-sensitive test code. This sidesteps the OpenJ9 JIT crash entirely because the broker is no longer sharing a process with the test runner.
   - Add the `testcontainers` and `testcontainers-kafka` (or `org.testcontainers:kafka`) test-scope dependencies to `inventory-service/pom.xml`, matching whatever Testcontainers BOM version is compatible with Spring Boot 3.x / Spring Kafka already in use.
   - Replace the `@EmbeddedKafka` annotation and its `brokerProperties`/port wiring with a `KafkaContainer` (e.g. `confluentinc/cp-kafka` image), wiring `spring.kafka.bootstrap-servers` to the container's dynamically-assigned address via `@DynamicPropertySource`.
   - Keep the existing `TestKafkaConfig`/`testReservationContainerFactory` bean as-is — that part already works and isn't related to this crash.
2. Confirm the test still passes against the real containerized broker, including the existing `testConsumer.poll(...)` assertion.
3. Document the dependency: this test now requires Docker to be available wherever it runs (local dev, CI, and Codex's review environment). Note this explicitly in the task report — if Codex's environment can't run Docker either, that's a new constraint worth surfacing rather than silently assuming it's fine.
4. If Testcontainers/Docker isn't available in this environment to verify the fix end-to-end, at minimum confirm the code compiles and the container/property wiring is correct by inspection, and say so plainly in the report rather than claiming a full green run that didn't happen.

## Out of scope
- Don't migrate `order-service`'s `OrderEventIntegrationTest` to Testcontainers in this task — its Java-21 problem (G-2) is a different root cause (Mockito self-attach) with a more targeted fix; migrating it too is reasonable future hardening but not required to close this specific blocker. If you do it anyway for consistency, say so explicitly rather than leaving it implicit.
- Don't add Testcontainers to any other module.

## Acceptance criteria
- `mvn -q test` (or `./mvnw -q test`) passes for `inventory-service` under a standard Java 21 JDK with Docker available.
- The same command no longer triggers a JIT/native crash under IBM Semeru/OpenJ9 21 if that distribution is available to test with — if it can't be tested directly, explain why moving off `@EmbeddedKafka` should resolve it (the crash was in the embedded broker's in-process JIT compilation, which no longer happens in this JVM).
- The test's actual assertions (publish `OrderPlacedEvent`, receive `InventoryReservationEvent` with `RESERVED` status) are unchanged — this is an infrastructure fix, not a test-logic change.
