# Sprint 24 Track B Review - OutboxRelay Transaction Proxy Fix

Review-Target-Commit: `9cf3f7f`  
Handoff: `docs/backlog/sprint-24-handoff.md`  
Verdict: ACCEPT

## Findings

No blocking source-level findings in Sprint 24.

## Verified Against Handoff

- **The production fix is exactly the narrow proxy-routing change claimed in the handoff.** `processPending()` remains untouched; the only logic change is the addition of `ObjectProvider<OutboxRelay>` and routing the scheduled path through `self.getObject().processPending()` at [inventory-service/src/main/java/com/example/inventoryservice/service/OutboxRelay.java](C:/projects/pub-rec-opencode-deepseek/inventory-service/src/main/java/com/example/inventoryservice/service/OutboxRelay.java:28), [inventory-service/src/main/java/com/example/inventoryservice/service/OutboxRelay.java](C:/projects/pub-rec-opencode-deepseek/inventory-service/src/main/java/com/example/inventoryservice/service/OutboxRelay.java:35), [inventory-service/src/main/java/com/example/inventoryservice/service/OutboxRelay.java](C:/projects/pub-rec-opencode-deepseek/inventory-service/src/main/java/com/example/inventoryservice/service/OutboxRelay.java:41), and [inventory-service/src/main/java/com/example/inventoryservice/service/OutboxRelay.java](C:/projects/pub-rec-opencode-deepseek/inventory-service/src/main/java/com/example/inventoryservice/service/OutboxRelay.java:46). I diff-checked that there is no behavioral drift inside `processPending()` itself.

- **The regression test hits the previously missing call path.** The new test invokes `scheduledRelay()` rather than `processPending()` directly and asserts the outbox row reaches `SENT` with a non-null `sentAt`, at [inventory-service/src/test/java/com/example/inventoryservice/OutboxRelayScheduledInvocationTest.java](C:/projects/pub-rec-opencode-deepseek/inventory-service/src/test/java/com/example/inventoryservice/OutboxRelayScheduledInvocationTest.java:64), [inventory-service/src/test/java/com/example/inventoryservice/OutboxRelayScheduledInvocationTest.java](C:/projects/pub-rec-opencode-deepseek/inventory-service/src/test/java/com/example/inventoryservice/OutboxRelayScheduledInvocationTest.java:77), [inventory-service/src/test/java/com/example/inventoryservice/OutboxRelayScheduledInvocationTest.java](C:/projects/pub-rec-opencode-deepseek/inventory-service/src/test/java/com/example/inventoryservice/OutboxRelayScheduledInvocationTest.java:81), and [inventory-service/src/test/java/com/example/inventoryservice/OutboxRelayScheduledInvocationTest.java](C:/projects/pub-rec-opencode-deepseek/inventory-service/src/test/java/com/example/inventoryservice/OutboxRelayScheduledInvocationTest.java:84). That closes the precise hole described in the handoff: prior tests used the proxied bean entrypoint directly.

- **The self-injection mechanism is appropriate for this bug class.** Using `ObjectProvider<OutboxRelay>` avoids eager self-construction problems while still forcing the scheduled call through the Spring-managed proxy. I do not see a circular-construction issue in this shape because resolution is deferred until `scheduledRelay()` executes.

## Independent Verification

- I re-ran the targeted inventory-service tests with Java 21 against integrated `main`:
  - `OutboxIntegrationTest`: `tests="2"`, `failures="0"`, `errors="0"` at [inventory-service/target/surefire-reports/TEST-com.example.inventoryservice.OutboxIntegrationTest.xml](C:/projects/pub-rec-opencode-deepseek/inventory-service/target/surefire-reports/TEST-com.example.inventoryservice.OutboxIntegrationTest.xml:1)
  - `OutboxRelayConcurrencyTest`: `tests="1"`, `failures="0"`, `errors="0"` at [inventory-service/target/surefire-reports/TEST-com.example.inventoryservice.OutboxRelayConcurrencyTest.xml](C:/projects/pub-rec-opencode-deepseek/inventory-service/target/surefire-reports/TEST-com.example.inventoryservice.OutboxRelayConcurrencyTest.xml:1)
  - `OutboxRelayScheduledInvocationTest`: `tests="1"`, `failures="0"`, `errors="0"` at [inventory-service/target/surefire-reports/TEST-com.example.inventoryservice.OutboxRelayScheduledInvocationTest.xml](C:/projects/pub-rec-opencode-deepseek/inventory-service/target/surefire-reports/TEST-com.example.inventoryservice.OutboxRelayScheduledInvocationTest.xml:1)

## Residual Checks Not Reproduced Here

- I did not reproduce the coordinator’s explicit revert-and-fail cycle against the old code.
- I did not perform the live VPS redeploy or verify the disappearance of the `"No active transaction"` log there from this review session.

Those are useful follow-up checks, but they are not blockers to accepting the committed fix.
