# Sprint 34 — pin the 4 transitive CVEs blocking Snyk's blocking-gate promotion

**Track:** C — go-live, Phase 5 (`docs/backlog/track-c-go-live-roadmap.md`). **Date:** 2026-07-17.

## Why this sprint exists

`CLAUDE.md`'s caveats said Snyk's CI gate stays report-only (`continue-on-error: true`)
"until the dev-only Angular CVEs clear." That precondition turned out to be **wrong,
verified against a real live CI run** (`gh run view 29595651473 --log`, the most recent
`main` push): `snyk test` for both `order-ui` and `inventory-ui` already reports
`Tested 18 dependencies for known issues, no vulnerable paths found` — zero issues.
Snyk's npm scan doesn't include `devDependencies` by default (no `--dev` flag in
`ci.yml`'s `snyk test --all-projects` command), and `@angular-devkit/build-angular` (the
package that owns the whole vulnerable dev-toolchain chain — webpack-dev-server,
http-proxy-middleware, etc.) is a `devDependency` — confirmed directly:
`grep '"@angular-devkit/build-angular"' order-ui/package.json` shows it under
`devDependencies`, not `dependencies`. Snyk never walks into that subtree at all. The
Angular dev-CVE caveat is real for `npm audit` (which includes dev deps by default) but
was never actually the thing gating Snyk's own severity threshold.

**The actual blocker, found by reading the same CI run's Snyk output directly:** all 4
Maven modules have real HIGH-severity findings — `auth-server`, `order-service`,
`inventory-service` each report 5 issues, `shared-model` reports 2 (subset, since it's a
plain library with no webmvc/springdoc/kafka-test dependency). All 4 distinct CVEs
already have patched versions released upstream — this isn't blocked on any external
release, just on us pinning versions above what Spring Boot 4.1.0's own BOM currently
manages.

## The 4 findings (verified against Maven Central `maven-metadata.xml`, not approximated)

| Artifact | Currently resolves to (Boot 4.1.0 BOM) | Snyk's minimum fix | Latest release | Pin to |
|---|---|---|---|---|
| `ch.qos.logback:logback-core` | 1.5.34 | 1.5.36 | 1.5.38 | **1.5.38** |
| `org.apache.tomcat.embed:tomcat-embed-core` | 11.0.22 | 11.0.23 | 11.0.24 | **11.0.24** |
| `com.fasterxml.jackson.core:jackson-databind` (classic Jackson 2, pulled in transitively via `springdoc-openapi`→`swagger-core-jakarta` and, test-scope only, `spring-kafka-test`→`kafka-server` — **not** Boot-managed, Boot 4 dropped classic Jackson 2 as its primary stack per Sprint 16) | 2.21.4 | 2.18.9 / 2.21.5 / 2.22.1 | 2.22.1 | **2.22.1** |
| `tools.jackson.core:jackson-databind` (Jackson 3, Boot's actual primary Jackson since Sprint 16, managed via the `jackson-bom.version` property) | 3.1.4 | 3.1.5 / 3.2.1 | 3.2.1 (jackson-bom) | **3.1.5** (see note) |

**Why `jackson-bom.version` → `3.1.5`, not the newer `3.2.1`:** 3.1.5 is the minimal patch
within the same `3.1.x` line Spring Boot 4.1.0 was actually built and tested against —
matches this project's established small-incremental-bump philosophy (e.g. Sprint 21
deliberately deferred a Testcontainers *major* bump rather than force it in alongside a
routine patch round). Jumping the whole BOM to `3.2.x` would shift many Jackson3 module
versions at once for a fix that a `3.1.x` patch already resolves. If the full
`mvn clean verify` regression suite doesn't pass cleanly on `3.1.5` for some
unanticipated reason, `3.2.1` is the fallback — note which one was actually used in the
handoff.

Confirmed via `grep -n "com.fasterxml.jackson.core" spring-boot-dependencies-4.1.0.pom`
(empty) that Boot's own BOM has no property for the classic Jackson 2 artifact — it must
be pinned via an explicit `<dependencyManagement>` entry in whichever pom(s) actually
pull it in transitively, not a property override.

## What to change

In **`auth-server/pom.xml`**, **`order-service/pom.xml`**, **`inventory-service/pom.xml`**
(all three currently show all 5 findings) and **`shared-model/pom.xml`** (shows only the
2 findings that don't need webmvc/springdoc/kafka-test — `logback-core` and the Jackson 3
`jackson-databind`, both Boot-BOM-managed):

1. Add to each pom's `<properties>` block (currently just `<java.version>21</java.version>`
   in each — confirm current content per-file rather than assuming, they may differ
   slightly):
   ```xml
   <logback.version>1.5.38</logback.version>
   <tomcat.version>11.0.24</tomcat.version>
   <jackson-bom.version>3.1.5</jackson-bom.version>
   ```
   Omit `tomcat.version` from `shared-model` (it doesn't pull in `tomcat-embed-core` at
   all — verify with `mvn dependency:tree` before adding an unnecessary property).

2. For the classic Jackson 2 `jackson-databind` (needed in `auth-server`,
   `order-service`, `inventory-service` — **verify per-module** whether `shared-model`
   actually needs it too, since its 2-issue list didn't show it, don't add it there if
   `dependency:tree` doesn't show it resolving), add an explicit
   `<dependencyManagement>` block:
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

3. **Verify each override actually took effect** — don't just add the property/entry and
   assume it worked. Run `mvn dependency:tree | grep -E "logback-core|tomcat-embed-core|jackson-databind"`
   per module, before and after, and show the actual before/after output in the handoff.
   A property name that doesn't match what Boot's BOM actually exposes silently does
   nothing (Maven doesn't error on an unused property) — this is exactly the kind of
   "looks right, isn't verified" failure this project's verification standards exist to
   catch.

## Explicitly out of scope

- **Do not touch `ci.yml`'s `continue-on-error` lines in this sprint.** Flipping the
  Snyk gate to blocking is a separate, tiny follow-up sprint that happens only *after*
  a live CI run on `main` (post-merge) confirms Snyk actually reports 0 issues across
  all 4 Maven modules — this sprint's local `mvn dependency:tree` check is necessary but
  not sufficient proof (Snyk's own resolution logic could differ subtly from Maven's
  `dependency:tree` view; a real CI run with the real `SNYK_TOKEN` is the actual proof).
- No dependency version bumps beyond exactly these 4 artifacts — this is a targeted CVE
  fix, not a general dependency-currency sweep.
- No change to `springdoc`, `spring-kafka-test`, or any other dependency's own declared
  version — only the transitively-pulled vulnerable leaves.

## Acceptance criteria (show real output, don't assert "Pass")

1. For each of the 4 modules: `./mvnw dependency:tree | grep -E "logback-core|tomcat-embed-core|jackson-databind"` showing the new pinned versions actually resolving (not the old vulnerable ones) — show real command output, not a summary.
2. `./mvnw clean verify` — BUILD SUCCESS, 0 test failures/errors, for all 4 modules (build `shared-model` first, per this repo's standard build order).
3. Show the actual `git diff` of all changed poms.
4. `git status --short` clean after commit.

## Loop note

Reviewer/coordinator: after this merges to `main`, the actual proof this sprint worked
is the *next* live CI run's Snyk job output — re-run
`gh run list --workflow=ci.yml --limit 3` and `gh run view <id> --log | grep -A5 "Tested .* dependencies"`
for each of the 4 Maven target files, confirm `found 0 issues` (or the "no vulnerable
paths found" success line) for all of them. Only once that's genuinely confirmed live
should the follow-up sprint remove `continue-on-error` from `ci.yml`'s `snyk-security`
job.
