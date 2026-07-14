# D-2 — Maven plugin/test-dependency patch bumps

**Sprint:** 21. **Type:** Maven config (4 poms). **Implementer:** opencode+DeepSeek (worktree).

## Why this exists
Dependabot flagged patch-level bumps across the 4 Maven modules:
- `org.apache.maven.plugins:maven-enforcer-plugin` `3.5.0` → `3.6.3` — pinned explicitly
  in all 4 modules (`shared-model`, `auth-server`, `order-service`, `inventory-service`).
- `org.testcontainers:kafka` `1.21.3` → `1.21.4` and `org.testcontainers:junit-jupiter`
  `1.21.3` → `1.21.4` — `order-service` and `inventory-service` only (test-scope).

Note: `org.testcontainers:testcontainers` itself (the core artifact) is requesting
`1.21.3` → **`2.0.5`** in Dependabot — that is a **major** version and is explicitly
**out of scope** for this task (and this sprint — see `sprint-21.md`). Do not bump the
core `testcontainers` artifact. Only bump `kafka` and `junit-jupiter`, both of which stay
within `1.21.x`.

These testcontainers artifacts aren't pinned by an explicit `<version>` today — they
inherit from Spring Boot's dependency-management BOM. To get `1.21.4` while staying on
Boot 4.1.0 (whose BOM manages `1.21.3`), add an explicit `<version>1.21.4</version>` to
just the `kafka` and `junit-jupiter` dependency entries — do not add one to the core
`testcontainers` entry (leave that BOM-managed).

## Deliverables
1. In all 4 `pom.xml` files, bump the `maven-enforcer-plugin` version from `3.5.0` to
   `3.6.3`.
2. In `order-service/pom.xml` and `inventory-service/pom.xml`, add explicit
   `<version>1.21.4</version>` to the `org.testcontainers:kafka` and
   `org.testcontainers:junit-jupiter` dependency entries only.
3. Do not touch `org.testcontainers:testcontainers` (core) — leave it BOM-managed.

## Acceptance criteria (observable outcomes)
1. `grep -A1 maven-enforcer-plugin */pom.xml | grep version` shows `3.6.3` in all 4
   modules.
2. `grep -B2 -A1 "artifactId>kafka</artifactId>\|artifactId>junit-jupiter</artifactId>" order-service/pom.xml inventory-service/pom.xml`
   shows an explicit `1.21.4` version tag on those two entries only.
3. `cd shared-model && ./mvnw clean install` succeeds, then `cd auth-server && ./mvnw clean verify`,
   `cd order-service && ./mvnw clean verify`, `cd inventory-service && ./mvnw clean verify`
   all succeed — **show the actual command output** (tail of each, including the final
   BUILD SUCCESS line and test summary), not just "Pass."
4. Maven Enforcer still fires as expected on a non-21 JDK — if you can't test this
   directly (no second JDK available), say so explicitly rather than asserting it.
5. `git diff` touches only the version lines described above — no incidental
   reformatting of surrounding XML.

## Related
[[feedback-pin-latest-versions]] — verify `3.6.3` and `1.21.4` are still actually the
current versions at implementation time (Dependabot's proposal may be superseded by a
newer patch by the time this runs).
