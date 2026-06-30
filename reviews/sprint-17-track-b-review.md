# Sprint 17 Track B Review - Observability / Testcontainers / Outbox

Review target: `218a2b7b7c0d644ac2208722983cbc96e5773f97`  
Handoff: `docs/backlog/sprint-17-handoff.md`  
Verdict: **REJECT / not cleared**

## Findings

### P1 - `OrderServiceKafkaContainerTest` does not reliably wire Spring to the KafkaContainer broker

The new order-service Testcontainers test sets `dockerAvailable` and starts the container in `@BeforeAll`, but the broker override is only registered from `@DynamicPropertySource` when `dockerAvailable` is already true:

- [order-service/src/test/java/com/example/orderservice/OrderServiceKafkaContainerTest.java](C:/projects/pub-rec-opencode-deepseek/order-service/src/test/java/com/example/orderservice/OrderServiceKafkaContainerTest.java:71)
- [order-service/src/test/java/com/example/orderservice/OrderServiceKafkaContainerTest.java](C:/projects/pub-rec-opencode-deepseek/order-service/src/test/java/com/example/orderservice/OrderServiceKafkaContainerTest.java:79)
- [order-service/src/test/java/com/example/orderservice/OrderServiceKafkaContainerTest.java](C:/projects/pub-rec-opencode-deepseek/order-service/src/test/java/com/example/orderservice/OrderServiceKafkaContainerTest.java:94)
- [order-service/src/test/resources/application-test.yml](C:/projects/pub-rec-opencode-deepseek/order-service/src/test/resources/application-test.yml:1)

`dockerAvailable` defaults to `false`, so the `DynamicPropertySource` path has no guaranteed way to publish `spring.kafka.bootstrap-servers` before the Spring context is built. That means the test is not a dependable "real Kafka container" guard; it can fall back to an ambient broker/default wiring or fail for environment reasons before ever using the container it started. The same lifecycle pattern also exists in the older inventory-side Testcontainers test, so Sprint 17 copied a broken pattern instead of establishing a sound one.

Impact: the handoff's B-3 claim that CI now proves a real Testcontainers-backed order-service Kafka path is not trustworthy.

### P1 - The outbox relay race still causes duplicate publishes and duplicate inventory feed entries

The handoff downplays the relay race as a harmless future nicety, but the implementation still has two concurrent entry points into the same unclaimed pending-row scan:

- [inventory-service/src/main/java/com/example/inventoryservice/service/OutboxRelay.java](C:/projects/pub-rec-opencode-deepseek/inventory-service/src/main/java/com/example/inventoryservice/service/OutboxRelay.java:40)
- [inventory-service/src/main/java/com/example/inventoryservice/service/OutboxRelay.java](C:/projects/pub-rec-opencode-deepseek/inventory-service/src/main/java/com/example/inventoryservice/service/OutboxRelay.java:47)
- [inventory-service/src/main/java/com/example/inventoryservice/service/OutboxRelay.java](C:/projects/pub-rec-opencode-deepseek/inventory-service/src/main/java/com/example/inventoryservice/service/OutboxRelay.java:53)
- [inventory-service/src/main/java/com/example/inventoryservice/receiver/OrderEventListener.java](C:/projects/pub-rec-opencode-deepseek/inventory-service/src/main/java/com/example/inventoryservice/receiver/OrderEventListener.java:28)
- [inventory-ui/src/app/pages/dashboard/dashboard.component.ts](C:/projects/pub-rec-opencode-deepseek/inventory-ui/src/app/pages/dashboard/dashboard.component.ts:109)

`scheduledRelay()` and `OrderEventListener.onOrderPlaced()` both invoke `processPending()`. `processPending()` reads all `PENDING` rows without any lock/claim step, then publishes to Kafka and pushes `/topic/messages` before the competing invocation has any way to exclude the same row. Order-service idempotency only masks the duplicate Kafka side. The inventory UI appends every STOMP event directly, so this race is user-visible as duplicated reservation feed entries.

Impact: Sprint 17 does not actually achieve the handoff's "effective exactly-once end-to-end" claim for observable behavior. The race is not just a theoretical cleanup item; it leaks to the demo UI.

### P2 - B-2's "all three services" correlation-ID claim is not implemented on auth-server

The handoff says observability now covers all three services, but auth-server only gained ECS logging and actuator exposure. There is no request filter, interceptor, MDC population, or `X-Correlation-Id` handling anywhere under `auth-server/src/main`:

- [auth-server/src/main/resources/application.yml](C:/projects/pub-rec-opencode-deepseek/auth-server/src/main/resources/application.yml:35)
- [auth-server/src/main/java/com/example/authserver/config/SecurityConfig.java](C:/projects/pub-rec-opencode-deepseek/auth-server/src/main/java/com/example/authserver/config/SecurityConfig.java:61)

By contrast, the actual correlation-ID plumbing exists only in order-service and inventory-service. That is enough for the saga trace itself, but it does not satisfy the handoff's broader "all three services" observability wording or the B-2 brief's "every log line on all three services" expectation.

Impact: this is a handoff/implementation mismatch. The saga path is partially instrumented, but auth-server is outside the correlation scheme.

## Verification Limits

- I could not re-run `./mvnw verify` in this sandbox because the Java runtime here cannot read the user-owned JDK security file (`C:\Users\dimit\.jdks\openjdk-25\conf\security\java.security`), so Maven wrapper startup fails before project code runs.
- I could not run `bash scripts/pre-review-check.sh 17` because this environment has no installed WSL distribution.
- I reviewed the pushed code state directly (`origin/main == 218a2b7`). The local checkout is not literally clean because an unrelated untracked `.claude/` worktree directory is present.

## Residual Notes

I did not find a new dependency-version regression in Sprint 17 itself. The new backend dependencies are limited to actuator and the already-pinned Testcontainers `1.21.3` test scope. The review blockers are correctness and test-validity issues, not freshness drift.
