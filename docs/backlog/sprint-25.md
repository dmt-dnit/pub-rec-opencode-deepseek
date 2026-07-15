# Sprint 25 — package rename com.example → be.dnit

**Track:** C — go-live (housekeeping, sequenced before Phase 4 resumes). **Theme:**
replace the placeholder `com.example` package/groupId with the project's real
reverse-domain namespace (`be.dnit`, matching the `dnit.be` domain already used
throughout Track C) before more code lands on top of the placeholder.

## Why this sprint exists
Dimitri's call: clean up the placeholder naming now, while the codebase is still small
enough that this is a same-day mechanical task, rather than letting more services/tests
accumulate on `com.example` first.

## Scope — verified by grep across the whole repo tree, not assumed

**Change (functional, live code):**
- All Java source (`main` + `test`) in all 4 Maven modules: `shared-model`,
  `auth-server`, `order-service`, `inventory-service` — package declarations, physical
  directory moves to match, and every `import com.example...` statement.
- `<groupId>com.example</groupId>` → `<groupId>be.dnit</groupId>` in all 4 `pom.xml`
  files, including the inter-module dependency declarations (`order-service` and
  `inventory-service` depend on `shared-model` by groupId+artifactId — this must be
  updated in lockstep or the build breaks).
- `application.yml` (all 3 services) and `application-test.yml` (`order-service`,
  `inventory-service`) — `spring.json.trusted.packages`, `spring.json.value.default.type`,
  and logging-level category keys that reference `com.example.*`.
- `.github/workflows/ci.yml` — 3 comment lines (not functional config) referencing
  `com.example:shared-model` as a Maven coordinate — update for accuracy since this is a
  live, actively-read file.

**Package mapping (exact):**
| Old | New |
|---|---|
| `com.example.sharedmodel` | `be.dnit.sharedmodel` |
| `com.example.authserver` | `be.dnit.authserver` |
| `com.example.orderservice` | `be.dnit.orderservice` |
| `com.example.inventoryservice` | `be.dnit.inventoryservice` |
| Maven `groupId: com.example` | `groupId: be.dnit` |

**Explicitly DO NOT touch — historical record, not living docs:**
- `docs/backlog/*.md` and `docs/backlog/tasks/**/*.md` (e.g. `sprint-1.md`,
  `A-1-rename-backend-modules.md`, `SB-3-boot4-cleanup-jackson3.md`, etc.) — these
  describe what was true at the time they were written; retroactively editing them
  misrepresents history.
- `reviews/*.md` — Codex's historical verdicts, same reasoning.
- `task001_share_flows_own_entities.md` (repo root) — an old planning doc that even
  predates the current module names (`kafkademo`/`kafkademo2`).
- Confirm this list is complete yourself with a fresh `grep -rl "com\.example" --include="*.java" --include="*.yml" --include="*.yaml" --include="*.xml" --include="*.md" --include="*.sh" .` (excluding `target/`) at the start — don't assume this brief's inventory is exhaustive if the repo has changed since it was written.

## Directory moves
For each module, every file under `src/{main,test}/java/com/example/<module-pkg>/...`
moves to `src/{main,test}/java/be/dnit/<module-pkg>/...`, preserving the subpackage
structure exactly (e.g. `com/example/inventoryservice/service/OutboxRelay.java` →
`be/dnit/inventoryservice/service/OutboxRelay.java`). Use `git mv` for the moves where
practical so the diff is easier to review, not a delete+recreate — if your tooling makes
that awkward for a bulk rename, a scripted move is fine, just verify the final `git
status` reflects the files as moved/renamed where git's own rename detection can show
it, not as pure delete+add pairs that hide the actual change.

## Acceptance criteria (observable outcomes)

1. **Zero remaining functional references:** `grep -rl "com\.example" --include="*.java" --include="*.yml" --include="*.yaml" --include="*.xml" --include="*.sh" .` (excluding `target/`, excluding this sprint's own doc/handoff files, excluding the explicitly-out-of-scope `docs/backlog/`, `reviews/`, and `task001_*.md` files) returns **empty**.
2. **Historical docs untouched:** `git diff --stat` shows **zero** changes under `docs/backlog/`, `reviews/`, or `task001_share_flows_own_entities.md`.
3. **Full build, correct order, from clean:** `cd shared-model && ./mvnw clean install`, then `auth-server`/`order-service`/`inventory-service` each `./mvnw clean verify` — **all four BUILD SUCCESS, actual output shown**, not asserted.
4. **Full test suite green** — show the real Surefire summary line for each of the 4 modules (`Tests run: X, Failures: 0, Errors: 0`), not just "BUILD SUCCESS" (a compile failure in main but a stale test artifact could otherwise mask a real problem — show the numbers).
5. **Kafka contract still works end-to-end in tests** — `order-service`'s and
   `inventory-service`'s Kafka/outbox integration tests (`OrderEventIntegrationTest`,
   `OutboxIntegrationTest`, etc.) passing is itself the proof that
   `spring.json.trusted.packages`/`spring.json.value.default.type` were updated
   correctly — if those are wrong, deserialization fails and those specific tests fail,
   not a generic compile error. Call this out explicitly in your report if any of them
   fail, don't just report the aggregate pass count.
6. `git status --short` clean after commit; no `target/` or other build artifacts
   accidentally staged.

## Loop note
This is a wide mechanical change touching most of the repo's Java files — genuinely
worth the full review cadence (implementer → coordinator verifies by rebuilding
independently, same as every logic-bearing sprint → Codex review) even though no single
change is complex, because the failure mode (a missed import, a stale YAML string) is a
silent breakage, not a compile error, in at least one case (the Kafka trusted-packages
config only fails at runtime/test-time, not compile-time).
