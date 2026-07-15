# Sprint 22 (Track C Phase 3) — backend deploy artifacts for `dnit-vps`

**Track:** C — go-live. **Theme:** author the systemd/Nginx/CI artifacts that will host
`auth-server`/`order-service`/`inventory-service` on Dimitri's existing `dnit-vps`,
mirroring the proven Pet Giftshop pattern exactly rather than inventing a new one.

## Why this sprint exists
`docs/backlog/track-c-go-live-roadmap.md` Phase 3. Port conflict (this project's
8080/8081 defaults collide with Pet Giftshop prod + the file-upload API on that box) is
resolved and live-verified: VPS gets 8090 (order)/8091 (inventory)/9000 (auth), Kafka
stays on 9092/2181. Subdomains decided: `saga-auth.dnit.be`, `saga-orders.dnit.be`,
`saga-inventory.dnit.be`. Deploy-credential approach decided: mirror Pet Giftshop's SSH +
GitHub-Environment pattern exactly (Dimitri's explicit call — standardizing across a
growing project portfolio is the point, not a one-off).

**Reference templates pulled directly from `dmt-dnit/petgiftshop`** (read via `gh api`,
not reconstructed from memory) — every brief below says exactly where it deviates from
these and why:
- `.github/workflows/deploy-backend-production.yml` — workflow shape
- `scripts/deploy-backend.sh` — the parametrized remote deploy script (stop service, move
  jar, chown, start service)
- `infra/systemd/petgiftshop-backend-staging.service` — systemd unit shape
- `infra/nginx/petgiftshop-api-staging.conf` — Nginx vhost shape

## A real finding that shapes this sprint: H2 console is unconditionally enabled
All three services ship `spring.h2.console.enabled: true` in the base
`application.yml`, ungated by any profile. That's fine on `localhost` for a demo; it
cannot go out on a public subdomain unauthenticated. **V-1 disables it via
`EnvironmentFile` override, not a code change** — `SPRING_H2_CONSOLE_ENABLED=false` in
each service's environment file. No source edit needed; Spring Boot's relaxed binding
picks up the env var over the YAML default.

## Tasks (3, loosely coupled by file — systemd vs. Nginx vs. CI)

| ID | Title | Brief | Risk | Implementer (rec) |
|----|-------|-------|------|-------------------|
| V-1 | systemd units for the 3 services | `tasks/sprint-22/V-1-systemd-units.md` | Low (config artifacts, not applied live yet) | opencode+DeepSeek |
| V-2 | Nginx vhosts for the 3 subdomains | `tasks/sprint-22/V-2-nginx-vhosts.md` | Low | opencode+DeepSeek |
| V-3 | GitHub Actions deploy workflows (3) | `tasks/sprint-22/V-3-github-actions-deploy-workflows.md` | Low-Med (CI orchestration, only fully provable on a live dispatch) | opencode+DeepSeek |

**Sequencing:** independent files, parallelizable worktrees. All three reference each
other's naming (service names, ports, paths) — brief each one with the same naming table
below so they land consistent without needing a fixup pass.

## Naming/config table (all three briefs use this — keep it consistent)

| Service | systemd unit | Port | `/opt` path | Subdomain | App user |
|---|---|---|---|---|---|
| auth-server | `pubrec-auth.service` | 9000 | `/opt/pubrec/auth/backend.jar` | `saga-auth.dnit.be` | `pubrec` |
| order-service | `pubrec-order.service` | 8090 | `/opt/pubrec/order/backend.jar` | `saga-orders.dnit.be` | `pubrec` |
| inventory-service | `pubrec-inventory.service` | 8091 | `/opt/pubrec/inventory/backend.jar` | `saga-inventory.dnit.be` | `pubrec` |

One shared `pubrec` system user for all three (this is one project's three services,
unlike Pet Giftshop's single app) — **not** a decision to reuse across this VPS's other
apps; each hosted project gets its own dedicated user, matching the isolation Pet
Giftshop already has (`petgiftshop` user, not `administrator`).

## Explicitly out of scope this sprint — deferred to the live-apply step
Per the roadmap's scoping split: these briefs produce **artifacts committed to this
repo**, coordinator-reviewed the normal way. They do **not** include:
- Actually creating the `pubrec` system user, `/opt/pubrec/*` directories, or
  `/etc/pubrec/*.env` files on the live VPS.
- Actually copying the systemd units into `/etc/systemd/system/` or running
  `systemctl enable/start`.
- Actually installing the Nginx vhosts or requesting Let's Encrypt certs.
- Creating the GitHub repo secrets (`VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY_B64`) or the
  GitHub Environment.
- Deciding whether the deploy SSH key is dedicated-per-project or reuses an existing one
  — flagged as **Dimitri's call**, not an implementer decision, given it's a security
  posture question on a box serving a paying customer.

All of the above happen in one explicitly-confirmed live-apply session after this
sprint's artifacts are reviewed and (if this sprint goes through Codex) accepted — not
autonomously by a worktree agent with SSH access.

## Acceptance (sprint-level)
1. V-1: three systemd unit files, each following the Pet Giftshop shape, each pointing
   at the right port/path/user, each disabling the H2 console via `EnvironmentFile`.
2. V-2: three Nginx vhost configs, each following the Pet Giftshop shape (HTTP→HTTPS
   redirect + TLS server block), each proxying to the right local port; order/inventory
   vhosts additionally carry WebSocket upgrade headers for `/ws/**` (STOMP over
   SockJS/WebSocket — the Pet Giftshop template doesn't need this, Pet Giftshop has no
   WebSocket traffic, so this is a deliberate deviation, not a copy error).
3. V-3: three GitHub Actions workflows (`workflow_dispatch` only, no push-trigger — this
   is a demo app, deploys should be deliberate), each following the Pet Giftshop
   workflow shape, reusing one shared parametrized `scripts/deploy-backend.sh` (same
   script, different `SERVICE`/`JAR_DEST`/`APP_USER` per workflow, exactly like the
   Pet Giftshop original).
4. `git status --short` clean; `bash scripts/pre-review-check.sh 22` passes.
5. **No live infrastructure is touched by this sprint** — verify this explicitly in the
   handoff (no SSH commands run against `dnit-vps`, no GitHub secrets created).

## Loop note
Standard cadence: opencode implements each brief in its own worktree → coordinator
verifies diffs by reading (including diffing each artifact against the fetched
Pet Giftshop reference where useful) → handoff → Codex reviews → `verify-review.sh`
gates the close. The live-apply step is separately scheduled after this sprint closes,
with Dimitri in the loop.
