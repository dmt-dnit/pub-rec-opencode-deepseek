# B-8 — Harden the bare-podman fallback (health-polls, not sleeps)

**Sprint:** 19 (Track B Sprint 6)
**Priority:** Should — Dimitri approved "harden it when it fits."
**Implementer:** Claude sonnet worktree agent. Branch from current `main`; coordinator applies the diff + verifies.
**Scope:** `scripts/startup-all.sh` only (the plain-podman fallback section). Do **not** touch `docker-compose.yml` (its healthchecks already handle the compose path) or any service code.

## Goal
Bring the **no-compose-provider fallback path** up to the same readiness-gating the compose path got in Sprint 18. Right now that path (`podman run` per container) sequences with fixed `sleep`s; replace them with **bounded health-polls** so each stage starts only once the previous one is actually ready.

## Context (read before editing)
- `scripts/startup-all.sh`: if `docker compose` / `podman compose` exists it `exec`s `compose up -d --build` (that path is health-gated by the compose file — leave it alone). Otherwise it falls through to the **plain-podman fallback** (`ENGINE=podman|docker`, `podman run` per service).
- The fallback currently: starts zookeeper → `sleep 8` → starts kafka → `sleep 10` → builds+starts auth-server → **already polls** `curl -sf http://localhost:9000/oauth2/jwks` in a 30×2s loop → builds+starts order-service, then inventory-service (no readiness wait on those).
- The services now expose `/actuator/health` (Sprint 17 B-2), published on host ports 9000/8080/8081. Kafka readiness can be probed with `podman exec kafka kafka-broker-api-versions --bootstrap-server localhost:9092` (the same probe the compose healthcheck uses).

## What to do
Replace the fixed `sleep`s with readiness polls, keeping the same start order:
1. **After zookeeper starts:** poll ZK readiness before starting Kafka. A bounded TCP/port check (`podman exec zookeeper bash -c 'echo > /dev/tcp/localhost/2181'` or `nc -z`) is enough — ZK is quick. (Don't rely on `ruok`; it's disabled-by-default, per the Sprint 18 decision.) If a clean ZK probe is awkward, a short bounded wait is acceptable — but Kafka's probe below is the real gate.
2. **After kafka starts:** poll `podman exec kafka kafka-broker-api-versions --bootstrap-server localhost:9092` until exit 0, bounded (e.g. 30×3s), before building/starting the services.
3. **Keep the existing auth-server JWKS poll** (it's already correct); optionally also accept `/actuator/health`.
4. **After order-service and inventory-service start:** poll `curl -sf http://localhost:8080/actuator/health` and `http://localhost:8081/actuator/health` respectively, bounded, so the script only reports "up" once they're actually serving.
5. **Every poll:** bounded retry count + interval, and on timeout print a clear error (which service, which probe) and `exit 1` — don't hang forever or silently continue. Reuse a small helper function (e.g. `wait_for <desc> <cmd>`) rather than copy-pasting loops. Respect `set -euo pipefail` (already set).
6. Use `$ENGINE` (podman/docker) consistently for the `exec` probes, matching the existing fallback.

## Acceptance criteria (observable)
1. `scripts/startup-all.sh` fallback no longer uses fixed `sleep`s as the sole gate — each stage waits on a real readiness probe with a bounded timeout. Show the diff (the `wait_for` helper + each call site).
2. On success the script sequences zookeeper → (ZK ready) → kafka → (broker ready) → auth-server → (JWKS/health) → order/inventory → (health) and exits 0. **If a probe times out, it prints which service/probe failed and exits non-zero.**
3. `shellcheck scripts/startup-all.sh` is clean (or only pre-existing/justified warnings — list them). **Show the shellcheck output.**
4. The compose path (top of the script) is unchanged.
5. Live run on a real podman box: state whether you ran it end-to-end (it builds 3 images — slow) or whether that's the one live-only check left for the coordinator/Codex. **Do not assert a live "Pass" you didn't run** — `shellcheck` + a dry read is a fine, honest result to report if the full build didn't complete.

## Notes / guardrails
- The `.sh` keeps its exec bit (`100755`) — don't drop it.
- No behavior change to the compose path or to which containers/ports are started; only *how the script waits*.
- Keep it POSIX-bash portable to the WSL+podman environment this fallback exists for.
