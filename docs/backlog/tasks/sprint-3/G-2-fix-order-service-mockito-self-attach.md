# Task G-2: Fix `order-service` test failure on OpenJ9 — Mockito inline mock-maker self-attach

**Resolves:** Must-fix 2 (order-service half) in `reviews/sprint-2-track-a-review.md` ("`mvn -q test` under IBM Semeru 21.0.6 fails before the assertions run because the `@MockBean` at `OrderEventIntegrationTest.java:58` triggers Mockito's inline mock maker, which cannot self-attach on this OpenJ9 VM").

## Context

`OrderEventIntegrationTest.java:58` declares `@MockBean private SimpMessagingTemplate messagingTemplate;`. Mockito 5.x defaults to the **inline** mock maker, which needs to self-attach a Java agent to the running JVM at test time. That self-attach fails on Codex's IBM Semeru/OpenJ9 21.0.6 build, so the test process errors out before any assertion runs — a JVM-vendor compatibility problem, not a logic bug. Codex confirms the test itself is correct (it properly exercises the WebSocket/REST paths fixed by F-2); only the mocking mechanism is the blocker.

`SimpMessagingTemplate` is a plain concrete class with no final methods/static calls being mocked here, so the **subclass** mock maker (Mockito's other built-in maker, which works via plain bytecode subclassing and needs no agent/self-attach) should be sufficient — it's the safer, more portable choice for this specific mock and avoids the self-attach path entirely.

## Task

1. Force Mockito to use the subclass mock maker instead of the inline default, scoped to `order-service`'s test classpath: add `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` containing the single line `mock-maker-subclass`.
2. Run the full test class (`OrderEventIntegrationTest`) locally and confirm all three tests still pass with the subclass maker — specifically `shouldPushOrderStatusUpdateViaWebSocket`, which depends on `verify(messagingTemplate, ...)` actually intercepting the call.
3. If the subclass maker turns out insufficient for some reason (e.g. if a future test needs to mock a final class/method that only the inline maker supports), don't silently abandon this fix — report back with the specific failure rather than reverting to inline and leaving Codex's environment broken again.
4. Re-run the same test class under a normal (non-OpenJ9) Java 21 JDK too, to confirm the change doesn't regress the environment that already passed.

## Out of scope
- Don't touch `inventory-service` in this task — its Java-21 failure (G-3) has a different root cause (a JIT segfault during embedded-broker startup, not Mockito) and needs a different fix.
- Don't add `-Djdk.attach.allowAttachSelf=true` as an alternative fix unless the subclass-maker approach above genuinely doesn't work — switching mock makers is the more portable fix since it removes the self-attach dependency entirely rather than just papering over it with a JVM flag that may not be settable in every CI environment.

## Acceptance criteria
- `mvn -q test` (or `./mvnw -q test`) passes for `order-service` under a standard Java 21 JDK.
- The same command passes under IBM Semeru/OpenJ9 21 if that distribution is available to test with; if it genuinely isn't available in this environment, the report explains what was changed and why it should resolve the self-attach error specifically, so Codex's re-run is the actual confirmation.
- No change to the test's assertions or coverage — this is a mocking-mechanism fix only, not a test-logic change.
