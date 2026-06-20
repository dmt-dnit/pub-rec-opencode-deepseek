# Task F-7: Pin the Java toolchain across all 4 Maven modules

**Resolves:** a verification-environment risk flagged in `reviews/sprint-1-track-a-review.md` ("sandboxed Maven could not read the active JDK security file... host default Java 25 also exposed separate Mockito/Byte Buddy incompatibility... rerunning with Java 21 was necessary to isolate real project failures from host-tooling noise").

## Context
Every `pom.xml` in this repo (`shared-model`, `auth-server`, `order-service`, `inventory-service`) declares `<java.version>21</java.version>`, which `spring-boot-starter-parent` translates into a compiler `--release` flag — but that only constrains what bytecode gets *produced*, not which installed JDK Maven itself *runs under*. If `mvn`/`./mvnw` is invoked with a Java 25 (or any non-21) JDK on the `PATH`/`JAVA_HOME`, the build can fail with confusing, unrelated-looking errors (the Mockito/Byte Buddy incompatibility Codex hit) instead of a clear "wrong JDK" message. This already cost the reviewer real time distinguishing real project bugs from host-tooling noise, and the same thing will happen to the next agent or developer who runs this repo on whatever JDK happens to be on their `PATH`.

## Task
In each of the 4 modules' `pom.xml` (`shared-model`, `auth-server`, `order-service`, `inventory-service`), add the Maven Enforcer Plugin with a `requireJavaVersion` rule pinned to 21, bound to the `validate` phase so it fails fast before compilation even starts:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-enforcer-plugin</artifactId>
      <version>3.5.0</version>
      <executions>
        <execution>
          <id>enforce-java-version</id>
          <goals>
            <goal>enforce</goal>
          </goals>
          <configuration>
            <rules>
              <requireJavaVersion>
                <version>[21,22)</version>
              </requireJavaVersion>
            </rules>
          </configuration>
        </execution>
      </executions>
    </plugin>
    <!-- existing spring-boot-maven-plugin entry stays as-is -->
  </plugins>
</build>
```
(`shared-model/pom.xml` has no existing `<build>` block — add one. The other three already have a `<build><plugins>` block with `spring-boot-maven-plugin` in it — add the enforcer plugin alongside it, don't replace it.)

Use `[21,22)` (allows any Java 21.x patch release, rejects 20 and 22+) rather than pinning an exact patch version — the goal is catching "wrong major version" mistakes, not forcing everyone onto one exact JDK build.

## Acceptance criteria
- Running `mvn -v` confirms which JDK is active before testing this.
- Running `./mvnw compile` (or `mvn compile`) under a Java 21 JDK succeeds in all 4 modules.
- Running the same command under a deliberately wrong JDK (e.g. Java 17 or 25, whatever's available to test with) fails immediately with the enforcer's clear `RULE FAILED` message naming the required version range — not a downstream compiler or test-framework error.
