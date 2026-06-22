# Task H-4: Find why `order-service`'s Mockito subclass-mock-maker override isn't taking effect

**Resolves:** Should-fix 1 in `reviews/sprint-3-track-a-review.md` ("the latest `OrderEventIntegrationTest` report still logs `Mockito is currently self-attaching to enable the inline-mock-maker`, so the runtime behavior does not match the documented explanation").

## Context

Sprint 3's `G-2` added `order-service/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` containing `mock-maker-subclass`, intending to force Mockito off the inline mock maker (which needs JVM self-attach, and fails on some OpenJ9 builds) and onto the subclass mock maker (plain bytecode subclassing, no self-attach needed). The test suite does pass on Codex's OpenJ9 machine now — but Codex's own log output from that run still shows Mockito self-attaching to enable the *inline* maker, meaning the override file isn't actually controlling which maker gets selected. The fix works by accident (self-attach apparently succeeds often enough on that machine) rather than by design, which means it's not actually portable — it'll fail again on whatever OpenJ9 build self-attach doesn't work on.

## Task

1. Confirm the override file is actually on the test runtime classpath where Mockito's plugin loader looks for it: after `mvn test-compile`, check `target/test-classes/mockito-extensions/org.mockito.plugins.MockMaker` exists with the right content.
2. Mockito's plugin resolution (`org.mockito.internal.configuration.plugins.PluginLoader` / `DefaultMockitoPlugins`) uses the classloader to look up `mockito-extensions/org.mockito.plugins.MockMaker` as a resource — if **more than one** jar/classpath entry provides that resource path, the JVM's resource-loading order decides which one wins, and it might not be this project's. Check whether any transitive test dependency (look specifically at what `spring-boot-starter-test` and `mockito-junit-jupiter` pull in) ships its own conflicting `mockito-extensions/org.mockito.plugins.MockMaker` resource — `mvn dependency:tree` plus inspecting jars under `~/.m2/repository` for that resource path will tell you.
3. If a conflicting resource is the cause, the fix is to either exclude whatever dependency provides it (only if safe to do so) or force resolution order — Mockito actually only reads the *first* one your classloader resolves, so check classpath ordering in the surefire plugin config too (`maven-surefire-plugin`'s classpath construction order generally follows declaration order in `pom.xml`, but test-scoped vs main-scoped deps and Spring Boot's own test-starter aggregation can reorder things).
4. Alternative if the conflict can't be cleanly resolved: explicitly disable inline mock maker's self-attach requirement as a belt-and-suspenders fallback by adding `-Djdk.attach.allowAttachSelf=true` to the surefire `argLine` in `order-service/pom.xml` (already has `<argLine>-Xmx512m</argLine>` at line ~106 — append, don't replace). This doesn't fix *why* the override isn't sticking, but it does make the self-attach path itself succeed reliably rather than depending on the OpenJ9 build's specific behavior. If you go this route, say so explicitly and note it's a fallback, not a root-cause fix — ideally still pursue step 2/3 first.
5. Whichever fix you land on, confirm via the actual test run's log output (not just exit code) that Mockito reports using the **subclass** maker, not inline-with-self-attach.

## Out of scope
- Don't touch `inventory-service` — unrelated module, different Java-21 portability problem already addressed by `G-3`/`H-2`.

## Acceptance criteria
- Running `OrderEventIntegrationTest` with verbose/debug logging shows Mockito using the subclass mock maker (no "self-attaching to enable the inline-mock-maker" message), or — if the `-Djdk.attach.allowAttachSelf=true` fallback was used instead — the self-attach succeeds reliably and the report explains why the override-file approach didn't work.
- All three existing tests in `OrderEventIntegrationTest` still pass.
- The fix is verified on at least one non-standard-HotSpot JDK if available (Semeru/OpenJ9, since that's what triggered this in the first place) — if none is available in this environment, say so plainly.
