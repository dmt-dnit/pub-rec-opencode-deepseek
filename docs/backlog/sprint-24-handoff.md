# Sprint 24 — Handoff to Codex

**Coordinator:** Claude Code. **Date:** 2026-07-15.
**Task:** B-1 (fix `OutboxRelay`'s self-invocation `@Transactional` bug). Real application logic, not deploy config — full review cycle warranted.
**Implementer:** opencode+DeepSeek, standing default, isolated worktree. Diff coordinator-reviewed by reading; tests independently re-run against integrated `main`, not just trusted from the worktree.
**Merge-gate tier:** coordinator-supervised, pushed direct to `main` per the Sprint 21 policy.
Review-Target-Commit: 9cf3f7f

## Why this sprint exists
Found live during Track C Phase 3.5's Kafka deploy (2026-07-15, same day): once `dnit-vps` had a real Kafka broker reachable, `inventory-service`'s logs showed `OutboxRelay.scheduledRelay()` failing on every firing (every ~500ms) with `org.springframework.dao.InvalidDataAccessApiUsageException: No active transaction`. This was invisible before because nothing reached this code path while Kafka was unreachable — it's a pre-existing bug, not something the Kafka deploy introduced.

## Root cause
Classic Spring AOP self-invocation pitfall. Before the fix:
```java
@Scheduled(fixedDelayString = "${outbox.relay.interval-ms:500}")
public void scheduledRelay() {
    processPending();   // implicit this. — bypasses the Spring-managed proxy
}

@Transactional
public void processPending() { ... }
```
`@Transactional` is implemented via a proxy around the bean. A call using the implicit `this.` reference from within the same class never goes through that proxy, so `processPending()`'s `@Transactional` has never actually taken effect when invoked from the `@Scheduled` path. Hibernate 7.4.1 strictly requires an active transaction even for reads, hence the exception on every firing.

**Confirms why the existing test suite never caught this:** `OutboxIntegrationTest`/`OutboxRelayConcurrencyTest` both call `outboxRelay.processPending()` on an `@Autowired`-injected bean reference — that *does* go through the proxy, so those tests correctly exercised `@Transactional` and passed. They never called through `scheduledRelay()`, so they never hit the bug.

**Practical impact, confirmed live:** the saga's return leg was broken — `inventory-service` could consume `OrderPlacedEvent` and write a pending outbox row, but the relay that publishes `InventoryReservationEvent` back to Kafka (and to the browser via `SimpMessagingTemplate`) never once succeeded.

## Fix (`9cf3f7f`)
`processPending()` is byte-for-byte unchanged (confirmed via `git diff` — its signature, body, and `@Transactional` annotation are untouched, since the existing tests depend on calling it directly). The fix is entirely in the caller: `ObjectProvider<OutboxRelay> self` field added via constructor injection (lazy — avoids a circular-dependency-at-construction issue a direct self-referencing constructor parameter would cause), `scheduledRelay()` now calls `self.getObject().processPending()`, routing through the proxy.

## Regression test (`OutboxRelayScheduledInvocationTest.java`, new file)
Calls `outboxRelay.scheduledRelay()` — not `processPending()` — through the `@Autowired` proxy, asserts the outbox row transitions to `SENT`. **Coordinator-verified this test actually catches the bug**, not just that it exists: the implementer's report states it was tested against the pre-fix code first (reverted `scheduledRelay()` to direct `processPending()`, ran the new test, got the exact live failure — `InvalidDataAccessApiUsageException: No active transaction` — then reapplied the fix). The coordinator did not independently re-run that revert-and-fail cycle, but the test's structure (calling `scheduledRelay()` specifically) makes the claim credible on inspection, and the fix's mechanism (proxy routing) is the well-established, standard solution to this exact category of bug.

## Coordinator verification
- Read `OutboxRelay.java` in full — `processPending()` untouched, fix matches the brief exactly.
- Read the new test in full — correctly follows the existing `OutboxIntegrationTest` pattern (same `@TestConfiguration`/`EmbeddedKafka` shape), calls the right method.
- **Independently rebuilt from integrated `main`** (not the worktree): `shared-model` `./mvnw clean install` → `inventory-service` `./mvnw -Dtest=OutboxIntegrationTest,OutboxRelayConcurrencyTest,OutboxRelayScheduledInvocationTest test` → **BUILD SUCCESS, 4 tests run, 0 failures, 0 errors** (actual output captured, not asserted).
- `git status --short` after cherry-pick: only the 2 expected files (`OutboxRelay.java` modified, `OutboxRelayScheduledInvocationTest.java` new) — no scope creep into `order-service` or elsewhere.

## What's still pending after this review
Per the brief: once this clears review, the coordinator will redeploy `inventory-service` to `dnit-vps` via the existing `deploy-inventory.yml` workflow and confirm live that `"No active transaction"` no longer appears and a real `"Relayed outbox event id=..."` success line does. That live verification is **not part of this handoff** — it happens after Codex's verdict, same sequencing as Sprint 22/23's artifact-review-then-live-apply split, even though this task itself is app code, not deploy config.

## Loop note
This is real logic — please give it a genuine read, not a rubber stamp. The interesting thing to check independently: does `ObjectProvider<OutboxRelay>` self-injection actually avoid a circular dependency at construction time (it should — `ObjectProvider` defers resolution), and does the regression test's assertion (`row.getStatus() == SENT`) actually require the transaction to have committed, not just that no exception was thrown?
