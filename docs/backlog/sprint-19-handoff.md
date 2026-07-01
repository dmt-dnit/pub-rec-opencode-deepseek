# Sprint 19 (Track B Sprint 6) — Handoff to Codex

**Coordinator:** Claude Code. **Date:** 2026-07-01.
**Tasks:** B-9 (CI-automate the containerized E2E smoke) + B-8 (harden the bare-podman fallback).
**Implementer:** **opencode + DeepSeek** (`deepseek-v4-pro`) for both, per the standing default. Diffs coordinator-reviewed by reading; integrated onto `main`.

## Round 2 (2026-07-01) — addresses Codex reject `reviews/sprint-19-track-b-review.md`
Codex round-1 REJECT: B-9 clean (no source defect, live CI green), **two B-8 blockers** in `scripts/startup-all.sh`, both now fixed in `9b8216e` (opencode+DeepSeek, single repo-root run; brief `docs/backlog/tasks/sprint-19/round2-codex-fixes.md`):
- **P1 (auth JWKS not fail-fast):** the kept hand-rolled auth loop exhausted 30 tries then *continued*, so downstream services could start against a dead auth server. **Fix:** replaced with `wait_for "auth-server JWKS" 30 2 curl -sf …` — now `exit 1` on timeout, consistent with every other gate. *(This was a coordinator brief error — the B-8 brief said keep it "as-is.")*
- **P2 (engine mis-selection):** fallback picked `docker` whenever the CLI existed even if only podman's daemon worked. **Fix:** now probes `podman info` (preferred) → `docker info` → `exit 1` if neither daemon responds.

**Coordinator verification:** read the diff; the fallback is now fully consistent — every readiness gate (ZK TCP, kafka `broker-api-versions`, auth JWKS, order/inventory `/actuator/health`) uses the fail-fast `wait_for`; no `sleep`-gate or silent-continue loop remains. shellcheck: opencode-reported clean (not installed in coordinator env). Exec bit `100755`. Compose path + `docker-compose.yml` untouched.

## Commits (on `main`)
| SHA | Task | Summary |
|-----|------|---------|
| `e305061` | B-9 | `ci.yml`: new `e2e-smoke` job — compose-up the stack, wait for healthy, start both UIs, run Playwright smoke |
| `8d2bbd8` | B-8 | `scripts/startup-all.sh`: replace fixed `sleep`s in the bare-podman fallback with bounded health-polls |
| `9b8216e` | B-8 r2 | round-2 fix: auth JWKS → fail-fast `wait_for`; engine selection probes a working daemon (prefer podman) |

## B-9 — CI containerized E2E smoke (`e305061`)
New additive `e2e-smoke` job in `.github/workflows/ci.yml` (8th job; **not** in any other job's `needs`, mirroring `snyk-security` isolation):
1. `docker compose up -d --build` (compose build is self-contained — Dockerfiles install shared-model inline).
2. **Waits for real health** — polls `docker inspect -f '{{.State.Health.Status}}'` for auth-server/order-service/inventory-service until `healthy`, 180s bound, dumps `docker compose logs` on timeout. (Not a fixed sleep.)
3. Starts `order-ui` + `inventory-ui` via `npm ci && npm start` (background), waits until `:4200`/`:4201` respond (120s bound, prints UI log on timeout).
4. `cd e2e && npm ci && npx playwright install --with-deps chromium && npx playwright test`.
5. On failure uploads Playwright HTML report, `test-results/` traces, `docker compose logs`, and UI logs (`upload-artifact@v4`, `if-no-files-found: ignore`). Teardown `docker compose down -v` on `always()`.

**Coordinator verification:** YAML parses; `jobs` = the 7 existing + `e2e-smoke`; the new job matches the existing style (checkout@v5, setup-node@v6 node 22). Reviewed the logic (health-wait, UI-wait, artifact upload) — sound. **`actionlint` is not installed in the coordinator env** → the workflow's real pass/fail is the **live CI run** (see below).

## B-8 — harden bare-podman fallback (`8d2bbd8`)
`scripts/startup-all.sh`, **plain-podman fallback section only** (compose path at top untouched):
- New `wait_for <desc> <max> <interval> <cmd…>` helper — bounded retry loop, prints which probe failed and `exit 1` on timeout (respects `set -euo pipefail`).
- `sleep 8` after zookeeper → `wait_for` TCP probe `$ENGINE exec zookeeper bash -c 'echo > /dev/tcp/localhost/2181'` (no `ruok` — disabled by default).
- `sleep 10` after kafka → `wait_for … kafka-broker-api-versions --bootstrap-server localhost:9092`.
- Added `/actuator/health` polls (host-side `curl -sf`) after order-service (8080) + inventory-service (8081) start. Existing auth-server JWKS poll kept as-is.
- Exec bit preserved (`git ls-files -s` = `100755`).

**Coordinator verification:** read the full diff — correct and minimal (20+/2-). `$ENGINE` used consistently; only the fallback changed. **`shellcheck` not installed in the coordinator env** — opencode reported it clean; the diff is simple and reads clean, but treat shellcheck as implementer-reported, not coordinator-reproduced.

## What I could NOT run here — Codex-only / live-only
- **B-9's pass signal is the live CI run.** GitHub Actions can't run locally. Once pushed, the `e2e-smoke` job is the automated B-6 runtime proof (stack healthy → saga → reservation feed grows by exactly one). **This handoff is being pushed so that run exists for review** — check its result. *(If the push was rejected for missing `workflow` OAuth scope, that's flagged in the loop message; Dimitri pushes it.)*
- **B-8's live path builds 3 images via podman** — not run here (slow). `shellcheck` + diff-read is the coordinator result; a live podman-fallback run is the Codex/Dimitri check.

## Process note — opencode worktree friction (transparency)
First dispatch ran both tasks concurrently, each in a pre-made worktree via `env -C`. opencode self-manages its own branch/worktree, which collided: B-8's opencode created a `/tmp` worktree its own headless sandbox then auto-rejected (**zero edits**), and B-9's run tangled the **main working-dir checkout** onto its auto-created branch. **No repo damage** — `main` ref stayed at `0f8c357` throughout; I restored the checkout, cherry-picked B-9's clean commit onto main, cleaned up stray worktrees/branches, and **re-ran B-8 alone from the repo root** (succeeded → `8d2bbd8`). Lesson banked: don't hand opencode a pre-made worktree or run instances concurrently on one repo.

## `git status --short`
```
(clean)
```
## Pre-review
`bash scripts/pre-review-check.sh 19` — passes (clean tree + this handoff). Reviewer: **clean-build first**, and for B-9 the signal is the live CI run, not local `target/`.
