# Sprint 17 round 2 — Codex fixes (F1/F2/F3)

**Source:** `reviews/sprint-17-track-b-review.md`. Three blockers across B-3/B-O1/B-2.

## F2 (most important) — outbox relay race duplicates the inventory-UI feed
`OutboxRelay.processPending()` is called by both the `@Scheduled` poller and synchronously by `OrderEventListener`. It does `findByStatusOrderByCreatedAtAsc(PENDING)` → publish → mark SENT → `convertAndSend("/topic/messages")` with **no claim/lock**. Two concurrent calls scan the same PENDING rows and both publish + both STOMP-push. Order-service idempotency hides the Kafka duplicate, but the **inventory-ui reservation feed shows duplicate entries** (the STOMP push is not consumer-protected).

**Fix:** make each outbox row processed exactly once under concurrency. Add an **atomic claim**: e.g. a `@Modifying` query that flips `PENDING → SENDING` for a row and only proceed if it claimed it (rows affected == 1), then publish and mark `SENT`; OR a pessimistic write lock (`@Lock(PESSIMISTIC_WRITE)` on the PENDING fetch with the relay `@Transactional`) so concurrent relays serialize and the second sees no PENDING rows. Either way: exactly one publish + one `convertAndSend` per row, even with the scheduler and the listener nudge racing. Keep the listener nudge for low latency if the claim makes it safe; otherwise document removing it. **Add a test** that invokes `processPending()` concurrently (or twice) for the same PENDING set and asserts `publisher.publish` and `convertAndSend` happen exactly once per row.

## F1 — Testcontainers test not bound to the container broker
`OrderServiceKafkaContainerTest`: `dockerAvailable` is set in `@BeforeAll`, but `@DynamicPropertySource kafkaProperties` reads it — and `@DynamicPropertySource` runs **before** `@BeforeAll`, so `dockerAvailable` is still `false` and `spring.kafka.bootstrap-servers` is never pointed at the container. The container is also a plain static field, not managed by the Testcontainers JUnit5 extension.

**Fix:** use the proper Testcontainers JUnit5 pattern: annotate the class `@Testcontainers(disabledWithoutDocker = true)` and the broker field `@Container static KafkaContainer kafka` so the container starts **before** `@DynamicPropertySource` and the whole class skips cleanly without Docker. Then `@DynamicPropertySource` can unconditionally register `kafka::getBootstrapServers`. Remove the `dockerAvailable`/`assumeTrue` plumbing (the annotation handles the skip). Verify the test context actually uses the container broker.

## F3 — auth-server missing correlation ID (handoff claimed "all three services")
auth-server got ECS structured logging + actuator but **no** correlation filter/MDC, so its log lines carry no `correlationId`. The B-2 brief required all three.

**Fix:** add a `CorrelationIdFilter` to auth-server mirroring `order-service/.../filter/CorrelationIdFilter.java` (OncePerRequestFilter: reuse `X-Correlation-Id` header or generate a UUID → MDC `correlationId`, echo as response header, clear MDC in finally). Register it as a `@Component`. auth-server has no Kafka, so no interceptor is needed — just the REST filter.

## Acceptance
1. F2: a test proves exactly one publish + one `convertAndSend` per outbox row under concurrent `processPending()`. All existing inventory tests still pass.
2. F1: `OrderServiceKafkaContainerTest` uses `@Testcontainers(disabledWithoutDocker=true)` + `@Container`; binds to the container broker; skips cleanly without Docker; runs in CI (Docker present).
3. F3: auth-server has a `CorrelationIdFilter`; its log lines carry `correlationId`.
4. `mvnw verify` green in all affected modules (real output). Do NOT weaken any existing test.
