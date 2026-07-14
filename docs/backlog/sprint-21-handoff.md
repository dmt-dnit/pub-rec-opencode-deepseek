# Sprint 21 — Handoff to Codex

**Coordinator:** Claude Code. **Date:** 2026-07-14.
**Tasks:** D-1 (CI Actions version bumps), D-2 (Maven plugin/testcontainers patch bumps), D-3 (Angular patch bumps). All mechanical dependency-currency triage — no runtime/behavior change intended.
**Implementer:** **opencode + DeepSeek**, standing default, one isolated worktree per task (`pub-rec-opencode-deepseek-worktrees/sprint21-d{1,2,3}`). All three diffs coordinator-reviewed by reading against each task's own base commit (not against current `main`, to avoid stale-worktree diff noise — see note below); integrated onto `main` via `git cherry-pick`.
**Merge-gate tier:** coordinator-supervised (see `CLAUDE.md` "Merge gate"), pushed direct to `main` per the policy settled this sprint.
Review-Target-Commit: 67635c2

## Why this sprint exists
22 Dependabot PRs had piled up unreviewed since Sprint 16 (2026-06-29 through 2026-07-06). Full rationale and explicit exclusions (testcontainers core major bump, already-satisfied Spring Boot PRs) in `docs/backlog/sprint-21.md`.

## Commits (on `main`)
| SHA | Task | Summary |
|-----|------|---------|
| `b03f211` | D-1 | `.github/workflows/ci.yml`: `actions/checkout` v5→v7 (8×), `actions/upload-artifact` v4→v7 (4×) |
| `67635c2` | D-2 | 4 poms: `maven-enforcer-plugin` 3.5.0→3.6.3; `order-service`/`inventory-service`: `testcontainers:kafka`/`junit-jupiter` 1.21.3→1.21.4 (explicit override, core `testcontainers` left BOM-managed) |
| `8563b88` | D-3 | Both UIs: Angular family bumped to actual npm-registry latest at implementation time (`^22.0.6` core/forms/animations/common/compiler/platform-browser*/router/compiler-cli, `^22.0.4` material/cdk, `^22.0.6` cli/build-angular) |

(Two docs-only commits, `514d4ee` + `65c65c8`, sit between D-1 and D-3 — the merge-gate policy discussion; no code touched.)

## D-1 — CI Actions version bumps (`b03f211`)
`grep -c "actions/checkout@v7\|actions/upload-artifact@v7" .github/workflows/ci.yml` → **12** (8 checkout + 4 upload-artifact), `setup-java@v5`/`setup-node@v6` untouched. YAML validated (`python3 -c "import yaml..."` → valid). `actionlint` not available in this environment — **not asserted**, stated as a limitation. **Only fully provable on a live CI run (push)** — coordinator cannot claim more than "YAML valid, scope correct" from a local check.

## D-2 — Maven plugin/testcontainers patch bumps (`67635c2`)
Diffed against its own base (`33aa705`, not current `main`) to isolate the real change — `main` had already moved past D-2's branch point by the time D-1 landed, so a naive `git diff main HEAD` would have shown D-1's CI bumps as a spurious revert. Isolated diff: exactly `maven-enforcer-plugin` 3.6.3 in all 4 poms + `testcontainers:kafka`/`junit-jupiter` 1.21.4 in the two services; core `testcontainers` artifact untouched (BOM-managed, per brief — the major 2.0.5 bump is explicitly out of scope). All 4 modules `mvnw clean verify`: **BUILD SUCCESS** — shared-model 4/0/0/0 tests, auth-server no tests, order-service 9/0/0/2, inventory-service 8/0/0/1 (skips are pre-existing, unrelated to this change). JDK-21 enforcer-fires-on-wrong-JDK check: **not run** — no second JDK available in this environment, stated explicitly rather than assumed.

## D-3 — Angular patch bumps (`8563b88`)
Same stale-base isolation applied (diffed against `33aa705`). Registry check at implementation time found newer patches than the brief anticipated (`22.0.6`/`22.0.4` vs. the brief's `22.0.5`/`22.0.3`) — per the brief's own instruction to verify rather than trust the written numbers, the agent used the actual latest. Initial `npm install` hit a genuine `ERESOLVE` (compiler/compiler-cli peer-dependency conflict from bumping only the packages named in the brief) — resolved correctly by also bumping the sibling packages required to stay in lockstep (`compiler`, `compiler-cli`, `common`, `platform-browser`, `platform-browser-dynamic`, `router`), **not** via `--force`/`--legacy-peer-deps`. `npm run build`: succeeds both projects. `npm audit --omit=dev`: **0 vulnerabilities**, both projects (the sprint-rules-card pass/fail signal). Full `npm audit`: still 8 dev-only advisories (3 low/3 moderate/2 high) — same count as the standing caveat, but the specific set shifted: a new esbuild Windows-arbitrary-file-read advisory appeared, still entirely inside `@angular-devkit/build-angular`'s dev/build chain, still not in the shipped bundle. Browser smoke: **Codex-only verification** — no browser available in this environment.

## Coordinator verification notes
- All three worktrees branched from the same base (`33aa705`) and landed sequentially via `git cherry-pick` — each cherry-pick was checked post-integration (`grep` for the resulting version strings, `git status --short` clean) to confirm no task's change was silently clobbered by another's, per the stale-worktree-base lesson from Sprint 16.
- `git status --short` clean on `main` after all three cherry-picks.
- `bash scripts/pre-review-check.sh 21`: see below.

## Explicitly out of scope (unchanged from `sprint-21.md`)
- Testcontainers core major bump (1.21.3→2.0.5) — own future task, needs Boot-4.1 BOM compat check.
- Stale `spring-boot-starter-parent` PRs #2/#11/#12 — already satisfied on `main` since Sprint 16; administrative PR closure, not a code task.
- `actions/cache` PR #5 — no-op, action not referenced in the workflow.

## Loop note
Mechanical/dependency-currency sprint — coordinator-supervised, pushed direct to `main` per the merge-gate policy settled this sprint (`CLAUDE.md`, "Merge gate: direct-to-main vs. review-branch"). Reviewer must **clean-build first** ([[feedback-review-build-from-source]]) — a stale local `target/`/`node_modules` would silently hide whether these version bumps actually took effect.
