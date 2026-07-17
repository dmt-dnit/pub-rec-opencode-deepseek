# Sprint 34 Handoff — pin 4 vulnerable transitive Maven deps (Track C Phase 5)

**Coordinator:** Claude Code. **Implementer:** opencode+DeepSeek (worktree). **Date:** 2026-07-17.
Review-Target-Commit: fbabf35

## What was done

Full context: `docs/backlog/sprint-34.md`, `docs/backlog/tasks/sprint-34/S-1-pin-vulnerable-transitive-deps.md`.

opencode pinned the 4 vulnerable transitive dependencies identified from a live CI Snyk
run, across all 4 Maven modules:

| Artifact | Before | After |
|---|---|---|
| `ch.qos.logback:logback-core` | 1.5.34 | 1.5.38 |
| `org.apache.tomcat.embed:tomcat-embed-core` | 11.0.22 | 11.0.24 |
| `com.fasterxml.jackson.core:jackson-databind` (classic Jackson 2) | 2.21.4 | 2.22.1 |
| `tools.jackson.core:jackson-databind` (Jackson 3, via `jackson-bom.version`) | 3.1.4 | 3.1.5 |

Per-module scoping was done correctly, not applied blanket: `auth-server`,
`order-service`, `inventory-service` each get all 4 (via `logback.version` /
`tomcat.version` / `jackson-bom.version` properties + an explicit
`dependencyManagement` override for the classic Jackson 2 artifact, which isn't
Boot-managed). `shared-model` only gets `logback.version` + `jackson-bom.version` —
correctly omitting `tomcat.version` and the classic Jackson 2 override, matching its
narrower 2-issue (not 5-issue) Snyk finding, since it has no webmvc/springdoc/kafka-test
dependency to pull those in.

## Coordinator verification (independent, not the implementer's self-report)

Ran directly, not trusted from the commit message:

- **`./mvnw dependency:tree | grep -E "logback-core|tomcat-embed-core|jackson-databind"`**
  in each of the 4 modules, confirming the new pinned versions genuinely resolve:
  - `auth-server`: `logback-core:1.5.38`, `tools.jackson.core:jackson-databind:3.1.5`,
    `tomcat-embed-core:11.0.24`, `com.fasterxml.jackson.core:jackson-databind:2.22.1` — all 4 present.
  - `order-service`: identical 4-line result.
  - `inventory-service`: identical 4-line result.
  - `shared-model`: only `logback-core:1.5.38` and `tools.jackson.core:jackson-databind:3.1.5` — correctly no tomcat/classic-Jackson2 line, confirming the narrower scope was intentional and correct, not an omission.
- **`./mvnw clean verify` on all 4 modules** (built `shared-model` first, per this repo's
  standard order):
  - `shared-model`: BUILD SUCCESS, Tests run: 4, Failures: 0, Errors: 0.
  - `auth-server`: BUILD SUCCESS, Tests run: 1, Failures: 0, Errors: 0 (`JwtConfigTest`, Sprint 32's test — still passing, confirming the dependency pin didn't disturb it).
  - `order-service`: BUILD SUCCESS, Tests run: 9, Failures: 0, Errors: 0, Skipped: 2 (matches this module's known baseline — the noisy `KafkaStorageException`/"log dir already offline" WARN spam during embedded-broker teardown is pre-existing benign shutdown noise, not a new failure).
  - `inventory-service`: BUILD SUCCESS, Tests run: 9, Failures: 0, Errors: 0, Skipped: 1 (includes `OutboxRelayScheduledInvocationTest`, Sprint 24's regression test — still passing).
- Reviewed the actual `git diff` (via `git show 87f3943` before cherry-pick): exactly the
  4 poms changed, 41 insertions, 0 deletions — no unrelated changes.

## Explicitly out of scope (unchanged from the brief)

- **`.github/workflows/ci.yml`'s `continue-on-error` lines were not touched.** Flipping
  the Snyk gate from report-only to blocking is a deliberate, separate follow-up, gated
  on a live post-merge CI run actually confirming 0 Snyk issues across all 4 Maven
  modules — this sprint's local `dependency:tree` check is necessary but not sufficient
  proof on its own (Snyk's resolution/reporting could in principle differ from Maven's
  own tree view, however unlikely).
- No other dependency version changes.

## What Codex should check independently

Same two items flagged in the sprint brief:
1. Whether the `jackson-bom.version` → `3.1.5` pin (minimal patch within the tested
   3.1.x line, not the newer 3.2.1) was the right conservative call, or whether a wider
   verification is warranted.
2. That the classic Jackson 2 `dependencyManagement` override doesn't shadow or conflict
   with anything relying on the previous 2.21.4 behavior (none found in this repo's own
   code — the artifact is purely a transitive dependency of `springdoc-openapi` and
   `spring-kafka-test`, never referenced directly).

## Next step (not part of this sprint)

Once this merges and a live CI run on `main` shows Snyk's Maven-module output for all 4
modules reporting `no vulnerable paths found` (or equivalent), the fast follow-up sprint
removes `continue-on-error: true` from both spots in `ci.yml`'s `snyk-security` job,
promoting the gate to blocking. Do not do this before that live confirmation.
