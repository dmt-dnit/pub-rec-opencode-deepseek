# Sprint 22 (Track C Phase 3) — Handoff to Codex

**Coordinator:** Claude Code. **Date:** 2026-07-15.
**Tasks:** V-1 (systemd units), V-2 (Nginx vhosts), V-3 (GitHub Actions deploy workflows + shared deploy script). All deploy-*artifact*-authoring only — no live infrastructure touched.
**Implementer:** **opencode + DeepSeek** for all three, standing default, one isolated worktree per task. Diffs coordinator-reviewed by reading; integrated onto `main`.
**Merge-gate tier:** coordinator-supervised, pushed direct to `main` per the policy settled in Sprint 21.
Review-Target-Commit: 341554b

## Round 2 (2026-07-15) — addresses Codex round-1 REJECT `reviews/sprint-22-track-c-review.md`

Codex round-1 verdict was REJECT, mechanically verified as a genuine fresh blocker
(`bash scripts/verify-review.sh 22` → exit 3), and both findings were independently
confirmed by the coordinator reading the actual files before any fix was briefed:

1. **SSH host key not pinned (MITM risk):** all three deploy workflows ran
   `ssh-keyscan -H "$VPS_HOST" >> ~/.ssh/known_hosts` at runtime — blind trust-on-first-use
   with no verification. Inherited verbatim from the fetched Pet Giftshop reference
   template, not introduced by the implementer. **Fix (`341554b`):** the real `dnit-vps`
   host key (RSA/ECDSA/Ed25519) was captured from an already-trusted SSH session this
   session and committed to `deploy/ssh/dnit-vps.known_hosts` — host keys are public by
   design, so committing the pinned value is correct practice, unlike the SSH *private*
   key which stays a GitHub secret. All three workflows now `cat` that file into
   `known_hosts` instead of scanning at runtime; `ssh-keyscan` no longer appears anywhere
   in the three workflow files (confirmed via `grep -rn`).
2. **Nginx WebSocket headers on the catch-all `location /`, not scoped to `/ws`:** in
   `saga-orders.conf`/`saga-inventory.conf`, the single `location /` block forced
   `Connection: upgrade` semantics onto every proxied request, not just the actual
   WebSocket handshake. **This was a coordinator brief error in round 1** — the V-2 brief
   told the implementer to put the headers in `location /`, mirroring the Pet Giftshop
   template's single-block shape without accounting for this project's WebSocket traffic
   living at one specific path. The implementer followed that instruction correctly; the
   instruction was wrong. **Fix (`341554b`):** both vhosts now have a dedicated
   `location /ws` carrying the upgrade headers, and `location /` reverted to plain HTTP
   proxying (same shape as the already-correct `saga-auth.conf`, which is untouched —
   confirmed via `git diff` showing zero changes to that file).

**Coordinator verification:** read the full diff (6 files, 28 insertions/5 deletions —
scope matches exactly the two findings, no drift into V-1's systemd units or anything
else). Confirmed: no `ssh-keyscan` remains; `location /ws` appears exactly once per
changed vhost file; `Connection "upgrade"` appears exactly once per file (inside
`location /ws`, not `location /`); `saga-auth.conf` diff is empty. YAML re-validated for
all three workflows post-edit. `nginx -t`/`actionlint` still not available in this
environment — same stated limitation as round 1, not silently dropped.

## Round 1 (2026-07-15, superseded by round 2 above where noted)

## Why this sprint exists
`docs/backlog/track-c-go-live-roadmap.md` Phase 3 — the artifact-authoring half of hosting the three Spring services on Dimitri's existing `dnit-vps`. Every naming/port/subdomain choice was decided and (for ports) live-verified against the real box before this sprint was briefed — see the roadmap doc's "Port conflict — resolved and live-verified 2026-07-15" section.

**Reference templates were pulled directly from `dmt-dnit/petgiftshop` via `gh api`** (not reconstructed from memory) — the proven, already-running deploy pattern for another project on the same VPS. All three briefs cite exact deviations from those templates and why.

## Commits (on `main`)
| SHA | Task | Summary |
|-----|------|---------|
| `ddde900` | V-1 | `deploy/systemd/pubrec-{auth,order,inventory}.service` + 3 `.env.example` files |
| `6bfba06` | V-2 | `deploy/nginx/saga-{auth,orders,inventory}.conf` |
| `b5fb2d3` | V-3 | `.github/workflows/deploy-{auth,order,inventory}.yml` + `deploy/scripts/deploy-backend.sh` |

## A real finding this sprint acts on: H2 console was unconditionally enabled
All three services ship `spring.h2.console.enabled: true` in their base `application.yml`
with no profile gate (verified: `auth-server:14`, `order-service:11`,
`inventory-service:33`). Fine on `localhost`, not acceptable on a public subdomain. **No
source change** — V-1's `.env.example` files set `SPRING_H2_CONSOLE_ENABLED=false`,
which Spring Boot's relaxed environment-variable binding overrides the YAML default
with, once the real `EnvironmentFile` is populated on the VPS.

## V-1 — systemd units (`ddde900`)
Three units mirroring the fetched Pet Giftshop template's exact shape (`Type=simple`,
`Restart=on-failure`/`RestartSec=5`, `SuccessExitStatus=143`, journal logging), one
shared `pubrec` user, ports/paths per the sprint's naming table (9000/8090/8091 →
`/opt/pubrec/{auth,order,inventory}/backend.jar`). **Coordinator verification:** diffed
structurally against the fetched reference — identical section order and directives,
only the substituted values differ. `git status --short` after cherry-pick showed only
the intended `deploy/systemd/` files.

## V-2 — Nginx vhosts (`6bfba06`)
Three vhosts mirroring the fetched template (HTTP→HTTPS redirect + TLS server block,
same proxy header set, `client_max_body_size 25m`, `proxy_read_timeout 90s`). **Correct,
deliberate deviation:** `saga-orders.conf` and `saga-inventory.conf` — and only those two
— carry `proxy_set_header Upgrade $http_upgrade;`/`proxy_set_header Connection
"upgrade";`, because `order-service`/`inventory-service` expose a STOMP-over-WebSocket
endpoint at `/ws` (confirmed in source: `WebSocketConfig.java` →
`registry.addEndpoint("/ws")`; `SecurityConfig.java` permits `/ws/**`). `saga-auth.conf`
has no WebSocket traffic and correctly omits these headers — the implementer stated this
asymmetry was checked deliberately, not an omission, and the diff confirms it.
`nginx -t` **was not available in this environment** — the implementer fell back to
manual brace/statement-termination verification and said so explicitly rather than
skipping the check silently. This should be re-run with a real `nginx -t` at live-apply
time.

## V-3 — GitHub Actions deploy workflows + deploy script (`b5fb2d3`)
Three `workflow_dispatch`-only workflows (no push-trigger — deploys stay deliberate) plus
one shared, parametrized `deploy/scripts/deploy-backend.sh` (stop service → move jar →
chown → start service → status), all mirroring the fetched Pet Giftshop
workflow/script shape. Each workflow inserts a "Build shared-model" step
(`cd shared-model && ./mvnw --batch-mode -DskipTests clean install`) before its own
module's build — required because this repo (unlike Pet Giftshop) has a shared-lib
dependency; confirmed present in all three via `grep`. Jar filenames
(`auth-server-0.0.1-SNAPSHOT.jar`, `order-service-0.0.1-SNAPSHOT.jar`,
`inventory-service-0.0.1-SNAPSHOT.jar`) were checked against each service's actual
`pom.xml` `artifactId`/`version`, not assumed. Exec bit on the deploy script confirmed
`100755` via `git ls-files -s` both before and after integration onto `main`.

**Coordinator-requested correction, applied before integration:** the first draft used
`actions/checkout@v4`/`actions/setup-java@v4`, copied verbatim from the Pet Giftshop
reference. This repo's own `ci.yml` is already on `@v7`/`@v5` (Sprint 21, `b03f211`) —
introducing an older pin in brand-new files the same week would be exactly the drift
`[[feedback-pin-latest-versions]]` warns against. Sent back to the same implementer
worktree (`opencode run --continue`, not a coordinator-direct edit, since this is a
config change) — bumped to `@v7`/`@v5` in all three files, YAML re-validated, commit
amended (`0a563cd` → `d9171a6` in-worktree, landed as `b5fb2d3` on `main`).
`actionlint` was not available in this environment for either pass — stated explicitly,
not skipped silently.

## Coordinator verification notes
- All three worktrees branched from the same base; landed sequentially via
  `git cherry-pick`, each checked post-integration (`git status --short` clean,
  `git ls-files -s` for the exec bit, `find deploy .github/workflows` for the full
  artifact listing, `git diff` confirming `ci.yml` untouched) per the stale-worktree-base
  lesson from Sprint 16.
- `git status --short` clean on `main` after all three cherry-picks.
- `bash scripts/pre-review-check.sh 22`: see below.

## What this sprint deliberately does NOT include
Per the roadmap's artifact-authoring/live-apply split — **no live infrastructure was
touched**: no SSH commands run against `dnit-vps`, no `pubrec` user/directories/env
files created anywhere (including the coordinator's machine), no systemd units
installed, no Nginx vhosts installed, no Let's Encrypt certs requested, no GitHub
secrets or Environment created, no workflow dispatched. That's the separately-scheduled
live-apply session with Dimitri in the loop.

## Loop note
Reviewer: these are deploy-config artifacts for a system that doesn't exist live yet —
"verify against the real target" here means checking the artifacts are internally
consistent and correctly derived from the repo's actual source (ports, jar names, H2
config, WebSocket paths), not exercising a live deploy. State explicitly what you did
and didn't verify, same standard as every other sprint.
