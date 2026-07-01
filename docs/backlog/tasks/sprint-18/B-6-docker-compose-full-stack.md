# B-6 — One-command Docker Compose full stack + close F2 (no-duplicate-feed smoke)

**Sprint:** 18 (Track B Sprint 5)
**Priority:** Should — last Track B hardening item
**Implementer:** Claude sonnet worktree agent. Branch from current `main`; apply diff + verify state (grep the compose for the healthcheck/condition, run the changed test). **Live containerized smoke = Codex-only** (coordinator has no Docker daemon / browser).
**Scope:** `docker-compose.yml` (root), `scripts/startup-all.sh`, `e2e/smoke.spec.ts`, and — only if a service exposes no usable health endpoint — a `HEALTHCHECK` in the relevant `*/Dockerfile`. **No Spring service main-code changes.** Do NOT add UI Dockerfiles.

## Goal
Make the backend demo come up with one command, reliably ordered by health (not just "container started"), and add the missing assertion that proves the inventory-UI reservation feed does **not** duplicate — the Sprint 17 **F2** carryover.

## Context (read before editing)
- Root `docker-compose.yml` already defines: `zookeeper`, `kafka`, `auth-server`, `order-service`, `inventory-service`. It is **backend-only by deliberate design** — the Angular UIs stay on `npm start` and proxy to the published container ports. **Keep it that way** (the frontends deploy to Vercel; a UI container would be throwaway). Do not add `order-ui`/`inventory-ui` services.
- Current `depends_on` uses the **short form** (no health condition): `order-service`/`inventory-service` list `kafka` + `auth-server` but can start before either is actually ready. Kafka in particular takes several seconds; a service that boots first may fail its initial broker connection.
- **B-2 (Sprint 17) added `spring-boot-starter-actuator` to all three services** with `/actuator/health` exposed and permitted in each `SecurityConfig`. Use `/actuator/health` as the service healthcheck endpoint — it already exists, no code change needed. auth-server also serves `/oauth2/jwks` (the startup script already polls it).
- `scripts/startup-all.sh` mirrors the compose file and has a **plain-podman fallback** (for bare WSL+podman with no compose plugin) that already `sleep`s between zookeeper→kafka→auth-server and polls `/oauth2/jwks`. Keep both paths working.
- The Playwright smoke is `e2e/smoke.spec.ts`. It logs into both UIs, places one SKU-001 order, and at **lines 152–155** asserts a reservation feed item is *visible* and shows a `RESERVED` chip — but it **never asserts the feed item count**, so a duplicate feed entry (the F2 bug) would pass today. That is the hole to close.

## What to do

### 1. Healthchecks + health-gated ordering (compose)
- Add a `healthcheck` to `kafka` (e.g. a broker-API/`kafka-topics --bootstrap-server localhost:9092 --list` probe, or a TCP/`nc` check on 9092 — pick one that actually reflects broker-ready, and note which), to `auth-server` (`/oauth2/jwks` or `/actuator/health`), and to `order-service`/`inventory-service` (`/actuator/health`).
- Change `order-service` and `inventory-service` `depends_on` to the **long form** with `condition: service_healthy` for `kafka` and `auth-server`. Give auth-server (and order/inventory) sane `start_period`/`interval`/`retries` so a slow first boot doesn't false-fail.
- If a container image lacks a shell tool the healthcheck needs (`curl`/`wget`/`nc`), prefer a `HEALTHCHECK` baked into that service's Dockerfile using whatever the base image has, rather than assuming a tool is present. State what you chose.

### 2. Close F2 — assert no duplicate reservation feed (E2E)
In `e2e/smoke.spec.ts`, before placing the order, capture the **baseline** count of reservation feed items (`inventoryPage.locator('mat-list-item')`), the same baseline-relative pattern the test already uses for order cards (`initialOrderCount`, lines 86–87). After the saga completes, assert the feed count is **exactly baseline + 1**, not just "visible":
- Keep the existing "visible" + `RESERVED` chip assertions.
- Add: `await expect(feedItems).toHaveCount(baselineFeedCount + 1, { timeout: … })`.
- This must hold on a **dirty DB / repeated runs** — hence baseline-relative, never `toHaveCount(1)`.
- Do not weaken any existing assertion to make this pass. A genuine duplicate must fail this test.

### 3. Keep the one-command paths working
- Verify `scripts/startup-all.sh` still works on both the `docker compose` / `podman compose` path and the plain-podman fallback after the compose changes. If healthchecks let you drop or shorten the fallback's hard-coded `sleep`s, that's a welcome improvement but not required — correctness first.

## Acceptance criteria (observable)
1. `docker compose config` is valid and shows `order-service`/`inventory-service` depending on `kafka` **and** `auth-server` with `condition: service_healthy`. **Show the relevant `docker compose config` (or the file) excerpt.**
2. `docker compose up -d --build` brings all five services to **healthy** (`docker compose ps` shows `healthy`, not just `running`/`started`), with order/inventory transitioning to healthy only after kafka+auth are healthy. **Show `docker compose ps` output.** *(If you have no Docker daemon, state so explicitly — this criterion is then Codex-only; still show `docker compose config` validation, which needs no daemon.)*
3. The updated `e2e/smoke.spec.ts` passes against the running stack **and** fails if the reservation feed gains two items for one order. Show the passing run, and describe (don't commit) a local check that a duplicated feed item makes it fail. *(Live browser run = Codex-only if no browser here — say so.)*
4. The smoke passes on a **second consecutive run without resetting the DB** (idempotent / dirty-DB safe). State whether you verified this or it's Codex-only.
5. `scripts/startup-all.sh` unchanged in intent and still valid for both compose and podman-fallback paths (a shellcheck/dry read is fine if you can't run podman).
6. Existing `./mvnw verify` per module still green; `bash scripts/pre-review-check.sh 18` passes.

## Notes / guardrails
- **Environment honesty (CLAUDE.md verification standard):** if you can't run Docker/podman or a browser in the worktree, do **not** assert a "Pass" for those — mark them Codex-only and show what you *could* run (`docker compose config`, the compiled/lint-clean test, `mvnw verify`). An unverified "Pass" that's false costs a whole review round.
- Do **not** containerize the UIs, and do **not** add real secrets — `GOOGLE_CLIENT_ID/SECRET` stay `${...:-placeholder}`.
- Do **not** touch Spring service main code; this is compose + script + one test.
- Kafka healthcheck: confluentinc images ship `kafka-topics` / `kafka-broker-api-versions` — a `kafka-broker-api-versions --bootstrap-server localhost:9092` exit-0 is a solid readiness signal. Whatever you pick, make sure "healthy" genuinely means "accepts client connections," or the ordering gate is theater.
