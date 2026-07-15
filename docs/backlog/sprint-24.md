# Sprint 24 — fix OutboxRelay self-invocation bug (breaks the saga's return leg)

**Track:** C — go-live. **Theme:** real application-logic bug found live during Track C
Phase 3.5's Kafka deploy, not deploy config. `inventory-service`'s `OutboxRelay` has
never successfully relayed an event since it was written — this fixes it.

## Why this sprint exists
Once Kafka became reachable on `dnit-vps` (Sprint 23's live-apply), `inventory-service`'s
logs revealed `OutboxRelay.scheduledRelay()` failing every ~500ms with
`org.springframework.dao.InvalidDataAccessApiUsageException: No active transaction`.
This was invisible before because nothing reached this code path while Kafka was
unreachable — it is not a new bug, it's a pre-existing one that's been dormant.

## Root cause (confirmed by reading `OutboxRelay.java`)
```java
@Scheduled(fixedDelayString = "${outbox.relay.interval-ms:500}")
public void scheduledRelay() {
    processPending();   // <-- self-invocation, bypasses the Spring AOP proxy
}

@Transactional
public void processPending() {
    List<OutboxEvent> pending = outboxEventRepository.findByStatusOrderByCreatedAtAsc(...);
    ...
}
```
Classic Spring AOP self-invocation pitfall: `@Transactional` (and `@Scheduled`,
`@Async`, etc.) is implemented via a proxy around the bean. A call from `scheduledRelay()`
to `processPending()` using the implicit `this.` reference bypasses that proxy entirely
— `@Transactional` on `processPending()` has never actually taken effect when invoked
this way. Hibernate 7.4.1 strictly requires an active transaction (even for reads),
hence the exception on every single firing.

**Confirms invisibility to the existing test suite:** `OutboxIntegrationTest` and
`OutboxRelayConcurrencyTest` both call `outboxRelay.processPending()` on an
`@Autowired`-injected bean reference — that call *does* go through the proxy, so the
tests correctly exercise `@Transactional` and pass. They never call through
`scheduledRelay()`, so they never hit the bug. **Practical consequence, confirmed live:**
`inventory-service` can consume `OrderPlacedEvent` and presumably write a pending outbox
row, but the relay that publishes `InventoryReservationEvent` back to Kafka (and to the
browser via `SimpMessagingTemplate`) has never once succeeded — the saga's return leg is
broken, and has been since this class was written.

## Fix — preserve `processPending()` exactly as-is, fix the caller
`processPending()`'s signature, annotation, and behavior must not change — the existing
tests depend on calling it directly on the injected bean. The fix is in
`scheduledRelay()`: route the call through the proxy via `ObjectProvider<OutboxRelay>`
self-injection (the standard, idiomatic Spring pattern for this exact problem — lazy,
avoids a circular-dependency-at-construction issue that a direct self-referencing
constructor parameter would cause):

```java
private final ObjectProvider<OutboxRelay> self;

public OutboxRelay(OutboxEventRepository outboxEventRepository,
                   InventoryEventPublisher publisher,
                   SimpMessagingTemplate messagingTemplate,
                   ObjectMapper objectMapper,
                   ObjectProvider<OutboxRelay> self) {
    this.outboxEventRepository = outboxEventRepository;
    this.publisher = publisher;
    this.messagingTemplate = messagingTemplate;
    this.objectMapper = objectMapper;
    this.self = self;
}

@Scheduled(fixedDelayString = "${outbox.relay.interval-ms:500}")
public void scheduledRelay() {
    self.getObject().processPending();
}
```
(`processPending()` itself: **zero changes**.)

## Task (1, self-contained)

| ID | Title | Risk | Implementer (rec) |
|----|-------|------|-------------------|
| B-1 | Fix `OutboxRelay` self-invocation, add a regression test proving the scheduled path actually transacts | Med (real logic fix, but small and well-understood — a known Spring pitfall with a standard fix) | opencode+DeepSeek |

## Deliverables
1. `inventory-service/src/main/java/com/example/inventoryservice/service/OutboxRelay.java`
   — the fix above, exactly. Do not touch `processPending()`'s body, signature, or
   `@Transactional` annotation.
2. **A new regression test that actually exercises `scheduledRelay()`** (not
   `processPending()` directly) against the Spring-managed bean, and asserts no
   exception is thrown / the outbox row transitions to `SENT`. This is the test that
   would have caught the original bug — without it, a future refactor could
   reintroduce the same self-invocation mistake and nothing would catch it. Use the
   existing `OutboxIntegrationTest`'s `EmbeddedKafka`/Spring context setup as the
   pattern to follow, don't invent a new test harness.
3. Do not touch `OutboxIntegrationTest.java` or `OutboxRelayConcurrencyTest.java`'s
   existing test bodies — they should continue passing unchanged (they were already
   correctly written, calling through the proxy; this is confirmation the fix doesn't
   need to touch them, not that they're out of scope to *read* for context).

## Acceptance criteria (observable outcomes)
1. `grep -n "self.getObject().processPending()" inventory-service/src/main/java/com/example/inventoryservice/service/OutboxRelay.java` matches.
2. `grep -n "@Transactional" inventory-service/src/main/java/com/example/inventoryservice/service/OutboxRelay.java` shows it still only on `processPending()`, unchanged position/form.
3. `cd inventory-service && ./mvnw test` — full suite green, **show the actual output**,
   including `OutboxIntegrationTest` and `OutboxRelayConcurrencyTest` by name in the
   results (proving they weren't skipped/deleted).
4. The new regression test is present, named descriptively (e.g.
   `OutboxRelayScheduledInvocationTest` or added as a new `@Test` method in
   `OutboxIntegrationTest` that calls `scheduledRelay()` specifically, not
   `processPending()`), and demonstrably fails against the old code (verify this by
   temporarily reverting the fix locally, confirming the new test fails, then
   reapplying the fix — state in your report that you did this, don't just assert it).
5. `git status --short` shows only the two files (`OutboxRelay.java` + the new/modified
   test file) — no scope creep into `order-service` or other inventory-service files.

## Verification the coordinator will do beyond the diff
This bug was found live, so the fix needs to be proven live too, not just by unit
tests — after this lands and passes Codex review, the coordinator will redeploy
`inventory-service` to `dnit-vps` via the existing `deploy-inventory.yml` workflow and
confirm in the live logs that `"No active transaction"` no longer appears and a real
`"Relayed outbox event id=..."` success line does. This is stated here so the implementer
understands the fix needs to actually work, not just compile and pass mocked tests.

## Loop note
This is real application logic — full cadence: opencode implements → coordinator
verifies by reading + running the real test suite (not trusting "tests pass" without
seeing output) → handoff → Codex reviews → `verify-review.sh` gates the close → live
redeploy + live verification, same rigor as the Sprint 22/23 deploy work.
