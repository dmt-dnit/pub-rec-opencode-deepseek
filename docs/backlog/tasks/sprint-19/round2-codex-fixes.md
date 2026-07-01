# Sprint 19 round-2 — fix Codex B-8 blockers

**Review:** `reviews/sprint-19-track-b-review.md` (REJECT). B-9 (`e305061`, CI `e2e-smoke`) had **no source defect** and its live CI run is green — do not touch it. Both blockers are in **`scripts/startup-all.sh`** (B-8, `8d2bbd8`), bare-podman fallback only. Do **not** change `docker-compose.yml` or the compose path.

## P1 — auth-server JWKS wait must fail-fast (currently continues on failure)
The existing auth-server readiness loop (~lines 83–87) was kept as a plain `for … curl … && break` loop that, if `/oauth2/jwks` never comes up, just exhausts its 30 attempts and **falls through** — the script then builds and starts order-service/inventory-service against a dead auth server. Every other stage now uses the fail-fast `wait_for` helper (line ~35), so this one is inconsistent.

**Fix:** replace that hand-rolled auth loop with a `wait_for` call, matching the others:
```
wait_for "auth-server JWKS" 30 2 curl -sf http://localhost:9000/oauth2/jwks
```
(30 attempts × 2s keeps the existing budget.) On timeout it prints the failing probe and `exit 1`, so downstream services never start against an unavailable auth server.

## P2 — no-compose fallback must pick a *working* engine, preferring podman
Current engine selection: `ENGINE=podman` then `command -v docker >/dev/null && ENGINE=docker` — i.e. it switches to Docker merely because the `docker` **CLI** exists, even when the Docker daemon is dead and Podman is the working runtime. This is the "plain-podman fallback"; it should not mis-select a non-working Docker.

**Fix:** in the no-compose branch, choose the engine whose **daemon actually responds**, preferring podman. E.g.:
```
if podman info >/dev/null 2>&1; then
  ENGINE=podman
elif docker info >/dev/null 2>&1; then
  ENGINE=docker
else
  echo "ERROR: no working podman or docker runtime for the fallback path" >&2
  exit 1
fi
```
(Probe `info`, not just `command -v`. Preserve `set -euo pipefail`.)

## Acceptance criteria (observable)
1. `grep -n "wait_for \"auth-server JWKS\"" scripts/startup-all.sh` shows the auth wait now uses `wait_for`; the old hand-rolled auth loop is gone. No stage in the fallback continues silently on a failed readiness probe.
2. Engine selection in the no-compose branch probes `podman info` / `docker info` (a working daemon), prefers podman, and errors out if neither works — not `command -v docker`.
3. `shellcheck scripts/startup-all.sh` clean — **show the output**.
4. Compose path (top of script) and `docker-compose.yml` unchanged. Exec bit stays `100755`.
5. Live podman-fallback run = Codex/Dimitri-only (builds 3 images) — don't assert a live Pass you didn't run.

## Implementer / dispatch note
opencode+DeepSeek, **single run from the repo root** (no pre-made worktree, no concurrent runs — that tangled round 1; see [[feedback-opencode-worktree-sandbox]]). Coordinator reads the diff + re-checks before integrating.
