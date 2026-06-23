# Task I-3: Find why Mockito still self-attaches despite a classpath-confirmed override

**Resolves:** Should-fix in `reviews/sprint-4-track-a-review.md` ("the override file exists both in source and compiled test resources... But the actual OpenJ9/Semeru test report still logs inline self-attach... the runtime behavior still does not match the intended explanation").

## Context

Sprint 3's `H-4` brief (itself carried over from `G-2`) assumed the `mock-maker-subclass` override in `mockito-extensions/org.mockito.plugins.MockMaker` wasn't taking effect because it might not be deployed to the test classpath. Codex's Sprint 4 review **rules that out directly**: the file is present and correct both in `order-service/src/test/resources/` and the compiled `order-service/target/test-classes/`. Despite that, the Surefire report still shows Mockito self-attaching to the inline maker. The investigation needs to go further than "is the file there" — it is, and Mockito is still not using it.

## Task

1. Check for a **conflicting resource**. Mockito's plugin loader resolves `mockito-extensions/org.mockito.plugins.MockMaker` via the classloader, which can return multiple matches across different JARs on the classpath — and Mockito uses whichever one resolves first, which may not be this project's. Run:
   ```
   mvn dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
   # then, for every jar on that classpath:
   for j in $(cat /tmp/cp.txt | tr ':' '\n'); do
     unzip -l "$j" 2>/dev/null | grep -q 'mockito-extensions/org.mockito.plugins.MockMaker' && echo "$j"
   done
   ```
   If more than one JAR (or this project's own `target/test-classes` vs. some dependency JAR) provides that resource path, that's very likely the actual cause — Mockito is reading a different one than expected.
2. Check whether Mockito's **default plugin resolution order** changed in the resolved `mockito-core` version (confirm the exact version via `mvn dependency:tree | grep mockito-core`). Some Mockito 5.x releases changed default mock-maker selection logic in ways that may not simply defer to the extensions file the way older versions did — check the actual release notes for the resolved version rather than assuming behavior from general knowledge.
3. Check whether Spring Boot's own test infrastructure (`@MockBean`'s `MockitoTestExecutionListener`, or `spring-boot-test`'s own Mockito integration) configures or requests a specific mock maker through a path that bypasses the extensions-file mechanism entirely. If Spring Boot itself is forcing inline mode for `@MockBean`-managed mocks regardless of the project's override, that changes the fix: it would mean targeting Spring's own configuration surface instead of (or in addition to) the Mockito extensions file.
4. Once you find the actual cause, fix it directly rather than adding another file alongside the one that already isn't working. If no root cause can be conclusively found, the documented fallback from this brief's predecessor still applies: add `-Djdk.attach.allowAttachSelf=true` to `order-service/pom.xml`'s surefire `argLine` (already has `-Xmx512m`, append rather than replace) so self-attach itself succeeds reliably instead of depending on the OpenJ9 build's specific behavior — but only use this as a fallback after step 1-3 are actually attempted, and say explicitly that it's a fallback, not a root-cause fix.

## Out of scope
- Don't touch `inventory-service` — different module, different already-resolved issue (`I-1` is its own task).

## Acceptance criteria
- The actual cause of the self-attach is identified and explained with evidence (which JAR/resource won resolution, or which Spring Boot mechanism is forcing it), or the fallback `-Djdk.attach.allowAttachSelf=true` is applied with that being stated explicitly as a fallback.
- Running `OrderEventIntegrationTest` and inspecting the Surefire report/log output directly shows Mockito is no longer relying on inline self-attach (or, if the fallback was used, that self-attach now succeeds reliably) — show the actual log line, not just a claim.
- All three existing tests in `OrderEventIntegrationTest` still pass.
