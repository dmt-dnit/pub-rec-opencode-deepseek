# Demo Notes - Sprint 17

Sprint 17 is presentation-worthy because it shows two reviewer catches that are easy for an implementation loop to miss:

- A "real Kafka via Testcontainers" test was added, but the lifecycle wiring is wrong. The container start happens in `@BeforeAll`, while the Spring property override depends on `@DynamicPropertySource`. That means the test can claim container coverage without reliably binding the app to the container broker.
- The transactional outbox improves crash safety for DB state, but the chosen relay shape still allows duplicate observable events. The handoff called that a non-bug because order-service is idempotent, but the inventory UI feed still duplicates because the relay also owns the STOMP push now.

Good conference framing:

- This is not a "the AI wrote bad code" story. It is a stronger one: the implementation and the coordinator handoff were both plausible, but an independent reviewer still found a lifecycle bug and a concurrency caveat that materially changes user-visible behavior.
- It is a clean example of why "exactly-once end-to-end" must be tested at the observable system boundary, not just reasoned about at the database/Kafka boundary.
- It also reinforces a Track A/Track B lesson: copying an existing test pattern is not validation if the original pattern was flawed.

Evidence worth reusing later:

- `order-service/src/test/java/com/example/orderservice/OrderServiceKafkaContainerTest.java`
- `inventory-service/src/main/java/com/example/inventoryservice/service/OutboxRelay.java`
- `inventory-service/src/main/java/com/example/inventoryservice/receiver/OrderEventListener.java`
- `inventory-ui/src/app/pages/dashboard/dashboard.component.ts`

What I could not capture in this sandbox:

- Fresh `mvnw verify` output, because the sandbox Java runtime cannot read the user-owned JDK security config.
- A live browser/demo replay of the duplicate feed, because starting the full stack depends on that blocked Java path.

## Round 2 follow-up

Round 2 is still a good story, but the interesting failure shifted from source correctness to delivery fidelity:

- The source fixes for all three round-1 findings are present and materially better.
- The repo's committed artifacts are stale enough that one reviewer-only claim already fails when exercised locally: the committed `auth-server/target/auth-server-0.0.1-SNAPSHOT.jar` does not contain `CorrelationIdFilter`, does not echo `X-Correlation-Id`, and therefore cannot demonstrate the claimed "auth-server logs carry correlationId" outcome.
- The committed Surefire XML for `OrderServiceKafkaContainerTest` still points to the old `Assumptions.assumeTrue(...)` skip path, even though the source now uses the correct `@Testcontainers` / `@Container` pattern.

Conference framing:

- This is a stronger lesson than "the fix was wrong." The implementation changed in the right direction, but the handoff still overstated what had been proven because the deliverable state mixed fresh source with stale runtime/test artifacts.
- Independent review here is not just code review; it is state-integrity review. In a multi-agent loop, "source is fixed" and "handoff is reproducible" are different claims.
- If you present this, it is a clean example of why an AI workflow needs explicit rules for artifact freshness, not only code diffs and green local summaries.
