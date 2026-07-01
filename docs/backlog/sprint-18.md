# Sprint 18 (Track B Sprint 5) — one-command containerized stack + close F2

**Track:** B — hardening. **Theme:** make the whole backend demo come up with one command, reliably (healthcheck-ordered), and finally pin the Sprint 17 **F2** carryover with a test that fails on a duplicate reservation feed.

## Why this sprint exists
Sprint 17 closed observability, real-Kafka tests, and the exactly-once outbox. It deferred **B-6** (Docker Compose full stack) as "its own sprint" and left one live-only carryover: **F2** — the containerized Playwright smoke must show the inventory-UI reservation feed does *not* duplicate after the outbox publish-path change. Both land here.

## Dependency currency (cadence step 1, 2026-07-01)
All current — no drift since the Sprint 17 check two days ago:
- Spring Boot **4.1.0**, Spring Security **7.1.0** (patched), Java **21** (LTS), springdoc 3.0.3, Snyk SCA green.
- Angular: latest **stable** is still **22.0.4** (verified against the npm registry this session; `22.1.0-next.3` is pre-release only). **No patched 22.x has shipped**, so the dev-only http-proxy-middleware CVE still has no forward fix. **Watch item continues — not a task.** Production `npm audit --omit=dev` = 0.
- GitHub Actions checkout@v5 / setup-java@v5 / setup-node@v6 / codeql-action@v4 — current.

## Tasks (1 — B-6 is cohesive and substantial; its parts are tightly coupled)

| ID | Title | Brief | Risk | Implementer (rec) |
|----|-------|-------|------|-------------------|
| B-6 | One-command Docker Compose full stack + close F2 (no-duplicate-feed smoke) | `tasks/sprint-18/B-6-docker-compose-full-stack.md` | Med (infra + one E2E assertion; no service main-code change) | Claude sonnet worktree agent; **live containerized smoke = Codex-only** |

Single-task sprint by design — CLAUDE.md always flagged B-6 as "its own sprint," and the other backlog items resolved to no-ops this cycle (see below). Compose hardening + startup script + the F2 smoke assertion are one tightly-coupled unit, so they share one brief per the sprint rules card (#4).

## Decisions taken at scoping (2026-07-01)
- **UIs stay on `npm start`, NOT containerized.** The frontends will deploy to **Vercel** (static SPA, edge TLS/security out of the box), so an nginx UI container in compose would model a deployment we won't use — throwaway work that duplicates what Vercel gives for free. Compose stays the *backend* stack; the UIs proxy to it via `ng serve` exactly as today. This matches the existing deliberate comment in `docker-compose.yml`.
- **Outbox `FOR UPDATE SKIP LOCKED` — DROPPED.** It targets multi-instance relay contention against a real RDBMS; inventory-service runs single-node **H2 in-memory** where it's a no-op, and Sprint 17's `@Lock(PESSIMISTIC_WRITE)` already closed the F2 race. Revisit only if the demo ever moves to Postgres + multiple relay instances.

## Deferred to future backlog (not scoped here)
- **Vercel frontend deploy prep** — Angular's `proxy.conf.json` only works under `ng serve`. Vercel needs `vercel.json` rewrites (or an env-based API base URL) **plus CORS on the three Spring services** (they currently rely on same-origin dev-proxying). Its own sprint when frontend deploy begins.
- **Real Google OAuth end-to-end + CI** — parked candidate (client-id/secret injected as GitHub Actions secrets). Collides with the repo's "no real secrets by design" policy; needs an explicit policy decision first. Not started.

## Acceptance (sprint-level)
1. `docker compose up -d --build` (or `scripts/startup-all.sh`) brings the whole backend stack up **healthy**, with `order-service`/`inventory-service` waiting on Kafka **and** auth-server being healthy (healthcheck + `depends_on: condition: service_healthy`), not just "started."
2. The containerized Playwright smoke passes **and** now asserts the inventory-UI reservation feed grows by **exactly one** item per single order (baseline + 1) — a duplicate would fail it. This is the F2 proof.
3. The smoke is **idempotent across repeated runs** (dirty DB): baseline-relative assertions, no dependence on an empty DB.
4. `scripts/startup-all.sh` works on both the compose path and the plain-podman fallback.
5. CI green (incl. Snyk); `bash scripts/pre-review-check.sh 18` passes.

## Loop note
Reviewer auto-pickup on handoff, as in Sprints 16–17. **The reviewer step must clean-build first** (`git clean -xfd` / `mvnw clean`) — Sprint 17 ate two extra rounds on stale git-ignored `target/` artifacts; that must not recur.
