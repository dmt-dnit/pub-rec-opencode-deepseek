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
