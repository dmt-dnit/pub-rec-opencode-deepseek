# Sprint 21 — routine dependency currency (CI actions, Maven plugins, Angular patch)

**Track:** B — hardening (maintenance sub-track). **Theme:** clear the routine, low-risk
half of the 22 open Dependabot PRs that piled up since Sprint 16 (2026-06-29 through
2026-07-06) while the project was between check-ins. This is exactly the "routine
hygiene instead of a late compounded surprise" case the dependency-currency practice
exists for — catch drift now, in small pieces, instead of it becoming one big jump.

## Why this sprint exists
Nobody triaged Dependabot output for ~2 weeks. Most of what it opened is safe,
mechanical patch/minor bumps. A few are not:
- **Spring Boot 4.0.7→4.1.0** (PRs #2/#11/#12) is **already on main** (Sprint 16, SB-3) —
  these PRs are stale no-ops against an old base and should just be closed, not merged.
  Verified directly: `grep spring-boot-starter-parent -A1 */pom.xml` shows `4.1.0`
  already in place on all three services.
- **`org.testcontainers:testcontainers` 1.21.3→2.0.5** (PRs #10/#14) is a **major**
  version bump, and testcontainers is not independently pinned in these poms — it's
  BOM-managed via `spring-boot-dependencies`. Overriding it to 2.0.5 while staying on
  Spring Boot 4.1.0's BOM (which manages 1.21.3) is untested territory and needs its own
  task with real acceptance criteria (Testcontainers 2.0's breaking-change notes,
  Boot-4.1 compat), not a blind bump. **Explicitly out of scope for Sprint 21.**
- `actions/cache` (PR #5) is closed/unmerged and **not referenced anywhere** in
  `.github/workflows/ci.yml` — no action needed, nothing to bump.

What's left after excluding the above is genuinely routine: GitHub Actions versions,
Maven build-plugin/test-dependency patch bumps, and Angular patch-level bumps. All three
are small, mechanical, and independently verifiable by a clean build.

## Dependency currency (cadence step 1, 2026-07-14)
- Java **21** — still LTS, no action.
- Spring Boot **4.1.0** — current (already latest per Sprint 16), no action this sprint.
- Angular **22.0.4 → 22.0.5** available (registry re-checked) — patch bump, in scope
  (D-3). Still no 22.x release outside the range flagged in Sprint 16-19's dev-CVE
  caveat (`http-proxy-middleware` etc. remain dev-only, unchanged).
- Testcontainers **1.21.3 → 2.0.5** — major, flagged, deferred to its own future task
  (not this sprint).

## Tasks (3, loosely coupled — parallelizable, independent files)

| ID | Title | Brief | Risk | Implementer (rec) |
|----|-------|-------|------|-------------------|
| D-1 | CI Actions version bumps (`checkout` v5→v7, `upload-artifact` v4→v7) | `tasks/sprint-21/D-1-ci-actions-version-bumps.md` | Low | opencode+DeepSeek |
| D-2 | Maven plugin/test-dependency patch bumps (enforcer-plugin, testcontainers-kafka/junit-jupiter) | `tasks/sprint-21/D-2-maven-plugin-patch-bumps.md` | Low | opencode+DeepSeek |
| D-3 | Angular patch bumps (`@angular/*` 22.0.4→22.0.5, Material/CDK 22.0.2→22.0.3) | `tasks/sprint-21/D-3-angular-patch-bumps.md` | Low | opencode+DeepSeek |

**Sequencing:** fully independent (CI workflow vs. Java poms vs. two npm projects) —
run as 3 parallel worktrees.

## Acceptance (sprint-level)
1. D-1: workflow still lints clean (`actionlint` if available, else manual read); next
   live CI run on push is green on the bumped action versions.
2. D-2: all 4 modules `mvnw clean verify` green with plugin/dependency versions bumped;
   `mvnw -version`/enforcer gate still fires on non-21 JDKs (no behavior change expected).
3. D-3: both UIs `npm install` + `npm run build` clean, `npm audit --omit=dev` still 0
   vulnerabilities (the pass/fail signal per the sprint rules card).
4. `git status --short` clean, `bash scripts/pre-review-check.sh 21` passes.
5. After landing, the corresponding Dependabot PRs close on Dependabot's next scan
   (not manually merged — this repo's cadence lands dependency bumps as coordinator
   commits, same as every prior sprint).

## Explicitly out of scope (do not touch this sprint)
- Testcontainers major bump 1.21.3→2.0.5 — own future task, needs Boot-4.1 BOM compat
  check.
- Stale `spring-boot-starter-parent` PRs #2/#11/#12 — already satisfied on main; close
  manually (administrative, not a code task) once this sprint lands.
- `actions/cache` PR #5 — no-op, not referenced in the workflow.

## Loop note
Standard cadence: opencode implements each brief in its own worktree → coordinator
verifies diffs + build output → handoff → Codex reviews → `verify-review.sh` gates the
close.
