# Sprint 18 (Track B Sprint 5) — Handoff to Codex

**Coordinator:** Claude Code. **Date:** 2026-07-01.
**Task:** B-6 — one-command Docker Compose full stack (healthchecks + health-gated ordering) + close Sprint 17 **F2** (no-duplicate reservation-feed smoke assertion).
**Implementer:** Claude **sonnet** worktree agent (`worktree-agent-af3d804c59c668f0e`). Diff applied onto `main` by the coordinator (see integration note); **not** a whole-branch merge — the branch was stale-based (branched from `13b3a0f`, before the Sprint 18 doc commit `3f4df95`), so a merge would have reverted the CLAUDE.md snapshot and deleted the sprint-18 docs. Only the 5 real code files were taken.

## Commits (on `main`)
| SHA | Summary |
|-----|---------|
| `3f4df95` | docs: scope Sprint 18 (sprint-18.md + B-6 brief + CLAUDE.md snapshot) |
| `03c963d` | feat(B-6): compose healthchecks + health-gated ordering + close F2 |

## Files changed (`03c963d`)
| File | Change |
|------|--------|
| `docker-compose.yml` | healthchecks on kafka + auth-server + order-service + inventory-service; order/inventory `depends_on` kafka **and** auth-server with `condition: service_healthy` |
| `auth-server/Dockerfile`, `order-service/Dockerfile`, `inventory-service/Dockerfile` | install `curl` in the `eclipse-temurin:21-jre` runtime stage (needed by the healthcheck probe; base image has no curl) |
| `e2e/smoke.spec.ts` | baseline feed count before order + `toHaveCount(initialFeedCount + 1)` after saga (F2) |

## Coordinator integration decision — dropped the implementer's zookeeper healthcheck
The agent additionally added a `zookeeper` healthcheck (`echo ruok | nc … | grep imok`) and gated `kafka` on it with `condition: service_healthy`. **I removed that during integration** and reverted `kafka` to the plain `depends_on: [zookeeper]`:
- `cp-zookeeper` **disables the `ruok` four-letter-word by default** (needs `ZOOKEEPER_4LW_COMMANDS_WHITELIST`), and `nc`'s presence in the image is unverified. If either is wrong, **kafka never becomes un-gated and the whole stack deadlocks** — the exact opposite of this sprint's "reliable one-command startup" goal, and I have no Docker daemon to prove it either way.
- It was **out of the brief's scope** (the brief asked for healthchecks on kafka + the 3 services; not zookeeper) and **buys nothing**: Kafka retries its ZK connection internally, and **Kafka's own healthcheck still gates order/inventory**, so the readiness chain the brief required is fully intact without it.

## What's done
| Sprint-level AC | Status |
|-----------------|--------|
| 1. Stack comes up healthy; order/inventory wait on kafka+auth `service_healthy` | Compose **structurally verified** (YAML parses; conditions + healthchecks present — output below). **Live `up` = Codex-only** (no Docker here). |
| 2. Smoke asserts feed grows by exactly +1 (F2) | **Done in source** (`toHaveCount(initialFeedCount + 1)`). Live browser run = **Codex-only**. |
| 3. Smoke idempotent on dirty DB | **Done** — assertion is baseline-relative (`initialFeedCount`), never `toHaveCount(1)`. Live repeat-run = Codex-only. |
| 4. `scripts/startup-all.sh` still works both paths | **Unchanged** (0 diff lines) — compose-level healthchecks don't affect the sleep-based podman fallback. |
| 5. CI green (incl. Snyk); `pre-review-check.sh 18` passes | pre-review-check: **passes** (below). CI = live on push. |

## Actual output

**Compose parse (coordinator, `python3 -c yaml.safe_load`):**
```
kafka              healthcheck=['CMD','kafka-broker-api-versions','--bootstrap-server','localhost:9092']
auth-server        healthcheck=['CMD','curl','-f','http://localhost:9000/actuator/health']
order-service      healthcheck=['CMD','curl','-f','http://localhost:8080/actuator/health']
inventory-service  healthcheck=['CMD','curl','-f','http://localhost:8081/actuator/health']
zookeeper          healthcheck=None
kafka              depends_on=['zookeeper']
order-service      depends_on={'kafka':{'condition':'service_healthy'}, 'auth-server':{'condition':'service_healthy'}}
inventory-service  depends_on={'kafka':{'condition':'service_healthy'}, 'auth-server':{'condition':'service_healthy'}}
YAML VALID
```

**F2 assertion (`e2e/smoke.spec.ts`):**
```
93:    const initialFeedCount = await feedItems.count();
163:   await expect(feedItems).toHaveCount(initialFeedCount + 1, { timeout: 20_000 });
```

## What I could NOT run here (environment) — Codex-only
This repo runs on **podman**, not docker. Coordinator env: **podman 4.9.3 present and functional** (rootless, native WSL — `podman info` returns cleanly), but **no compose provider is installed** (`podman compose` → "looking up compose provider failed"; no `podman-compose`). So:
- **No compose front-end** → `podman compose config` / `up` / `ps` (and the docker equivalents) can't run here; compose validated by **YAML parse** instead (output above). Live compose-path bring-up + health states = **Codex-only** (Codex has a compose provider).
- **No browser** → the live Playwright smoke (incl. the negative test that a duplicated feed entry fails `toHaveCount(+1)`, and the dirty-DB second-run) is **Codex-only**.
- **Java 25 in this env** (enforcer requires `[21,22)`) → `mvnw verify` is **Codex-only / CI**. B-6 changes no Spring main code, so no module behavior changed; the live CI build is the authoritative check.
- Implementer reported `npx tsc --noEmit` on the smoke test = 0 errors (Node 24) before commit.

### Scope nuance — healthchecks apply only on the compose path
The healthchecks + `condition: service_healthy` gating live in `docker-compose.yml`, so they take effect only when a **compose provider** runs the stack (`docker compose` or `podman compose`). `scripts/startup-all.sh`'s **plain-podman fallback** (bare `podman run`, no `--health-cmd`) — the path that runs in a no-compose-provider env like this coordinator box — **does not use them**; it still sequences via its hardcoded `sleep`s + `/oauth2/jwks` poll (unchanged, still works). Hardening that fallback (e.g. `podman run --health-cmd` or replacing sleeps with health-polls) is a reasonable **follow-up**, out of B-6's asked scope. Flagging so it's a conscious decision, not an oversight.

**Browser status:** Codex-only verification (no browser in coordinator env).

## Reviewer instructions (loop note)
**Clean-build first** — `git clean -xfd` / `mvnw clean` before running anything, then build from source or read the push-triggered live CI. Do **not** run any pre-existing `target/` jar (Sprint 17 lost two rounds to stale git-ignored artifacts). Then bring the stack up **via a compose provider** (`docker compose up -d --build` or `podman compose up -d --build` — the health-gating needs compose; the bare-podman fallback in `startup-all.sh` bypasses it, see the scope nuance above) → `… ps` shows all healthy with order/inventory healthy only after kafka+auth; run the Playwright smoke against the stack and confirm the reservation feed gains exactly one item (F2).

## `git status --short`
```
(clean)
```
## Pre-review
`bash scripts/pre-review-check.sh 18` — passes (clean tree + this handoff present). Output in the handoff commit session.
