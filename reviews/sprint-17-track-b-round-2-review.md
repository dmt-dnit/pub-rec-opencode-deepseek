# Sprint 17 Track B Round 2 Review

Review target: `0b06d0d8e5047f3e47e7ad2baa872e1ad26621fb`  
Handoff: `docs/backlog/sprint-17-handoff.md`  
Verdict: **REJECT / not cleared**

## Findings

### P1 - The auth-server artifact committed in the repo does not contain the new `CorrelationIdFilter`, so the claimed F3 live verification is not reproducible from this checkout

The round-2 source fix is present in [auth-server/src/main/java/com/example/authserver/filter/CorrelationIdFilter.java](C:/projects/pub-rec-opencode-deepseek/auth-server/src/main/java/com/example/authserver/filter/CorrelationIdFilter.java:23), and the implementation should echo `X-Correlation-Id` plus populate MDC at [auth-server/src/main/java/com/example/authserver/filter/CorrelationIdFilter.java](C:/projects/pub-rec-opencode-deepseek/auth-server/src/main/java/com/example/authserver/filter/CorrelationIdFilter.java:33) and [auth-server/src/main/java/com/example/authserver/filter/CorrelationIdFilter.java](C:/projects/pub-rec-opencode-deepseek/auth-server/src/main/java/com/example/authserver/filter/CorrelationIdFilter.java:38).

However, the committed runtime artifact in this repository is stale relative to that source change:

- `jar tf auth-server/target/auth-server-0.0.1-SNAPSHOT.jar | Select-String CorrelationIdFilter` returns no match.
- `auth-server/target/classes/com/example/authserver/filter/` does not exist in this checkout.
- Running the committed jar and probing `GET /oauth2/jwks` with `X-Correlation-Id: codex-auth-check-200` returned `200` but no echoed `X-Correlation-Id` response header, and the startup/request log file contained no `correlationId` or probe value matches.

Impact: the handoff's explicit Codex-only re-verification target, "auth-server logs carry correlationId", is not actually verified by the repository state that was handed over. Because this repo intentionally keeps built outputs under `target/`, stale artifacts are part of the deliverable, not ignorable local leftovers.

### P1 - The committed Surefire evidence for F1 still reflects the old skipped-by-assumption test, so "Testcontainers test runs in CI" is not independently proven here

The source-level F1 fix is present and looks correct: [order-service/src/test/java/com/example/orderservice/OrderServiceKafkaContainerTest.java](C:/projects/pub-rec-opencode-deepseek/order-service/src/test/java/com/example/orderservice/OrderServiceKafkaContainerTest.java:60) now uses `@Testcontainers(disabledWithoutDocker = true)`, [order-service/src/test/java/com/example/orderservice/OrderServiceKafkaContainerTest.java](C:/projects/pub-rec-opencode-deepseek/order-service/src/test/java/com/example/orderservice/OrderServiceKafkaContainerTest.java:66) declares the shared `@Container`, and [order-service/src/test/java/com/example/orderservice/OrderServiceKafkaContainerTest.java](C:/projects/pub-rec-opencode-deepseek/order-service/src/test/java/com/example/orderservice/OrderServiceKafkaContainerTest.java:70) unconditionally registers the container broker.

But the committed test artifact that should support the handoff's verification claim still shows the pre-fix behavior:

- [order-service/target/surefire-reports/TEST-com.example.orderservice.OrderServiceKafkaContainerTest.xml](C:/projects/pub-rec-opencode-deepseek/order-service/target/surefire-reports/TEST-com.example.orderservice.OrderServiceKafkaContainerTest.xml:217) records the test as skipped.
- The skip reason explicitly points to the old assumption path at [order-service/target/surefire-reports/TEST-com.example.orderservice.OrderServiceKafkaContainerTest.xml](C:/projects/pub-rec-opencode-deepseek/order-service/target/surefire-reports/TEST-com.example.orderservice.OrderServiceKafkaContainerTest.xml:218), [order-service/target/surefire-reports/TEST-com.example.orderservice.OrderServiceKafkaContainerTest.xml](C:/projects/pub-rec-opencode-deepseek/order-service/target/surefire-reports/TEST-com.example.orderservice.OrderServiceKafkaContainerTest.xml:220), and [order-service/target/surefire-reports/TEST-com.example.orderservice.OrderServiceKafkaContainerTest.xml](C:/projects/pub-rec-opencode-deepseek/order-service/target/surefire-reports/TEST-com.example.orderservice.OrderServiceKafkaContainerTest.xml:221), which no longer exists in current source.

Impact: I can confirm the source fix, but I cannot confirm the claimed CI/runtime proof from this repository state. The artifact trail currently contradicts the round-2 handoff rather than backing it up.

### P2 - F2's "inventory feed no longer duplicates" claim is still unproven at the system boundary in this environment

The code fix itself is materially better than round 1: [inventory-service/src/main/java/com/example/inventoryservice/repository/OutboxEventRepository.java](C:/projects/pub-rec-opencode-deepseek/inventory-service/src/main/java/com/example/inventoryservice/repository/OutboxEventRepository.java:12) adds `PESSIMISTIC_WRITE`, and the new race test in [inventory-service/src/test/java/com/example/inventoryservice/OutboxRelayConcurrencyTest.java](C:/projects/pub-rec-opencode-deepseek/inventory-service/src/test/java/com/example/inventoryservice/OutboxRelayConcurrencyTest.java:90) through [inventory-service/src/test/java/com/example/inventoryservice/OutboxRelayConcurrencyTest.java](C:/projects/pub-rec-opencode-deepseek/inventory-service/src/test/java/com/example/inventoryservice/OutboxRelayConcurrencyTest.java:123) directly asserts one publish and one STOMP send per row under two concurrent callers.

I do not have contrary source evidence on F2 after round 2. The gap is verification: I could not re-run the full containerized smoke in this sandbox because the available container runtime is not connected and a fresh Maven rebuild is blocked by sandboxed network access to Maven Central. That means the handoff sentence "the inventory-UI reservation feed no longer duplicates (live smoke)" is still not independently re-verified here.

Impact: this is not a new code defect, but it is still a process gap in the autonomous loop. One of the three reviewer-only acceptance checks remains unexecuted in the actual reviewer environment.

## Verification Notes

- Source inspection confirms the round-2 fixes for F1, F2, and F3 are present.
- I re-ran the F3 check against the committed `auth-server/target/auth-server-0.0.1-SNAPSHOT.jar`. The response did not echo `X-Correlation-Id`, and the built artifact does not contain `CorrelationIdFilter`.
- I could not independently query the push-triggered CI run from this environment, and the local container runtime is not usable here, so F1 CI execution and F2 live smoke remain unverified at runtime.

## Dependency / Security / Performance Notes

- No new dependency freshness regression stood out in the Sprint 17 source changes.
- The pessimistic lock introduced for F2 is a reasonable correctness-first tradeoff at this scale; I do not see a new performance concern beyond the intended serialization of relay workers.
- The blocker is reproducibility and state fidelity, not a newly discovered library vulnerability.
