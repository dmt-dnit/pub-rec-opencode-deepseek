# S-1 — pin 4 vulnerable transitive dependencies across the 4 Maven modules

**Sprint:** 34. **Track:** C — go-live, Phase 5. **Implementer:** opencode+DeepSeek (worktree).

## Why

A live CI run's Snyk job (`gh run view 29595651473 --log`, most recent `main` push)
shows real HIGH-severity findings in all 4 Maven modules — `auth-server`,
`order-service`, `inventory-service` (5 issues each), `shared-model` (2 issues). All 4
distinct CVEs already have patched versions released upstream (verified against Maven
Central's `maven-metadata.xml`, not approximated) — this is fixable now, not blocked on
any external release. Full context: `docs/backlog/sprint-34.md`.

## The 4 findings and what to pin

| Artifact | Currently resolves to | Pin to |
|---|---|---|
| `ch.qos.logback:logback-core` | 1.5.34 | **1.5.38** |
| `org.apache.tomcat.embed:tomcat-embed-core` | 11.0.22 | **11.0.24** |
| `com.fasterxml.jackson.core:jackson-databind` (classic Jackson 2 — pulled in transitively via `springdoc-openapi`→`swagger-core-jakarta`, and test-scope-only via `spring-kafka-test`→`kafka-server`; **not** Boot-managed) | 2.21.4 | **2.22.1** |
| `tools.jackson.core:jackson-databind` (Jackson 3, Boot's actual primary Jackson since Sprint 16, managed via the `jackson-bom.version` property) | 3.1.4 | **3.1.5** (minimal patch within Boot 4.1.0's tested 3.1.x line — do not jump to 3.2.x unless 3.1.5 fails verification, see sprint doc for reasoning) |

## What to change, per module

`auth-server/pom.xml`, `order-service/pom.xml`, `inventory-service/pom.xml`, `shared-model/pom.xml`:

1. **Before touching anything**, run `./mvnw dependency:tree | grep -E "logback-core|tomcat-embed-core|jackson-databind"` in each module and record the actual current output — this tells you which of the 4 artifacts each specific module actually pulls in (don't assume all 4 apply to `shared-model`; its Snyk finding list only showed 2, not 5 — likely no `tomcat-embed-core` and no classic Jackson 2 there, but verify, don't guess).

2. Add to each pom's `<properties>` block (only for artifacts that module's `dependency:tree` actually shows):
   ```xml
   <logback.version>1.5.38</logback.version>
   <tomcat.version>11.0.24</tomcat.version>
   <jackson-bom.version>3.1.5</jackson-bom.version>
   ```

3. For the classic Jackson 2 `jackson-databind` (Boot's own BOM has no property for it —
   confirmed via `grep -n "com.fasterxml.jackson.core" ~/.m2/repository/org/springframework/boot/spring-boot-dependencies/4.1.0/spring-boot-dependencies-4.1.0.pom`,
   empty), add an explicit override in a `<dependencyManagement>` block in whichever
   module(s) `dependency:tree` shows it resolving in:
   ```xml
   <dependencyManagement>
       <dependencies>
           <dependency>
               <groupId>com.fasterxml.jackson.core</groupId>
               <artifactId>jackson-databind</artifactId>
               <version>2.22.1</version>
           </dependency>
       </dependencies>
   </dependencyManagement>
   ```

4. **Re-run `dependency:tree` after the change** and confirm the new versions actually
   resolve — a property name that doesn't match what Boot's BOM exposes silently does
   nothing (Maven doesn't error on an unused property). Show real before/after output
   in your task report, not an assertion that it worked.

## Explicitly out of scope

- Do **not** touch `.github/workflows/ci.yml` — flipping the Snyk gate to blocking is a
  separate follow-up sprint, gated on a live post-merge CI run proving 0 issues.
- No other dependency version changes — this is a targeted CVE fix, 4 artifacts only.

## Acceptance criteria (show real output, don't assert "Pass")

1. Build `shared-model` first (`./mvnw clean install`), then for each of the 4 modules:
   `./mvnw dependency:tree | grep -E "logback-core|tomcat-embed-core|jackson-databind"`
   — real output, before and after.
2. `./mvnw clean verify` — BUILD SUCCESS, 0 failures/errors, all 4 modules.
3. Show the actual `git diff` of every changed pom.
4. `git status --short` clean after commit.
