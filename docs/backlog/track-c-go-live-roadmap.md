# Track C — go-live roadmap

**Written:** 2026-07-15. Track B (hardening) is functionally done; this is the answer to
"what Track C is."

**Status update (2026-07-15):** Phase 1 done (AUTO-1 landed + header-contract reminder
delivered to Codex). Phase 3 is **fully done, live-verified** — Sprint 22's artifacts
cleared Codex round 2 (`341554b`), then the live-apply session (`docs/backlog/sprint-22-live-apply-runbook.md`)
put all three services on `dnit-vps`: `https://saga-{auth,orders,inventory}.dnit.be` are
all publicly reachable, correctly identified via their own logs (not just "active"), H2
console confirmed blocked. Two real bugs found only by actually running the live-apply
(neither catchable by artifact review alone): a sudoers exact-match mismatch, and a
shared-temp-path race condition across the three deploy workflows that briefly caused
`pubrec-auth` to run inventory-service's jar — both fixed, both documented in the
runbook.

**Kafka gap — CLOSED 2026-07-15.** Sprint 23 delivered `deploy/docker-compose/kafka-vps.yml`
+ `deploy/systemd/pubrec-kafka.service`, cleared Codex round 1 (`1728fcd`) — loopback-only
port binding confirmed both by the coordinator (direct diff against
`order-service/docker-compose.yml`) and independently by Codex (cross-checked against
both services' actual `application.yml` client config). Live-applied same day: images
pulled, `docker compose config` confirmed `host_ip: 127.0.0.1` on both ports, externally
confirmed unreachable from outside the VPS, `order`/`inventory` restarted and both
connected (`"Discovered group coordinator localhost:9092"`, no more `Rebootstrapping`).

**Bonus finding from the live-apply: a real application bug (Sprint 24).** Once Kafka
was reachable, `inventory-service`'s `OutboxRelay` revealed a pre-existing Spring
self-invocation `@Transactional` bug that had silently broken the saga's return leg
since the class was written — `@Scheduled scheduledRelay()` called `processPending()`
via `this.`, bypassing the proxy that makes `@Transactional` take effect. Fixed
(`9cf3f7f`, Codex ACCEPT), redeployed, and **live-verified with a real order placed
through the public API**: `POST https://saga-orders.dnit.be/api/orders` → inventory
log shows `"Relayed outbox event id=1 orderId=<real-id> status=RESERVED"` → order status
closed the loop `PENDING`→`CONFIRMED`. This is the first genuine end-to-end saga proof
on live infrastructure, not just individually-healthy services.

**Phase 4 (Vercel frontends) is now genuinely unblocked** — there's a working saga behind
it to demo.

## Ground truth this roadmap is built on

- All three Spring services use **in-memory H2** (`orderdb`, `inventorydb`, and
  auth-server's `authdb`) — no persistent database anywhere in this repo. That removes
  an entire category of go-live concern (no DB provisioning, no backups, no migration).
- `order-service`/`inventory-service` call auth-server's JWKS at a **hardcoded
  `http://localhost:9000/oauth2/jwks`** — fine as long as all three Spring services run
  on the same host; only the browser-facing endpoints need public HTTPS.
- Dimitri already runs a paid VPS (**`dnit-vps`**, DatabaseMart, `93.127.142.134`,
  Ubuntu 24.04, Nginx 1.24, **OpenJDK 21** and **Node 22** already installed — matches
  this repo's pins exactly) hosting Pet Giftshop production/staging + a file-upload API
  via **systemd units + Nginx reverse-proxy vhosts**, and deploys those via GitHub
  Actions → SSH → drop a jar → systemd restart. Frontends for other `dnit.be` projects
  already go to **Vercel**, wired to `dnit.be` subdomains.
- **This is the "cheap" path**: reusing an already-paid-for VPS and an already-free
  Vercel tier costs nothing incremental except the VPS's spare capacity, and reuses a
  deploy pattern that's already proven rather than inventing a new one.

## Port conflict — resolved and live-verified 2026-07-15

`order-service` defaults to port **8080**, `inventory-service` to **8081**. On
`dnit-vps`, `8080` is Pet Giftshop **production** and `8081` is the **file-upload API**
— both already serving real traffic, so this project can't bind those ports on that box.
Decided: local dev keeps today's 8080/8081/9000 unchanged; the VPS deployment uses a
non-colliding set instead of moving or touching any existing service:

| Service | Local dev | `dnit-vps` |
|---|---|---|
| `order-service` | 8080 | **8090** |
| `inventory-service` | 8081 | **8091** |
| `auth-server` | 9000 | **9000** |
| Kafka | 9092 | **9092** |
| ZooKeeper | 2181 | **2181** |

Only the two that actually collided move. **Live-verified via `ss -tlnp` on `dnit-vps`
(2026-07-15):** confirmed listeners are only `80`/`443`/`22`/`5432`/`5433`/`8080`/`8081`/
`8082` — nothing on `9000`, `9092`, `2181`, `8090`, or `8091`, and no systemd unit named
anything Kafka/ZooKeeper/auth-related. This scheme is cleared to use as-is, no further
verification needed before wiring the deploy workflow.

(Side finding, unrelated to this project: an unidentified listener on port `4310` exists
on the box that isn't documented in `DNIT_INFRASTRUCTURE.md` — flagged to Dimitri, no
action needed here.)

**Resource budget:** Dimitri's read is the box has enough headroom — running continuously
alongside the existing services, not on-demand. Existing services are untouched; nothing
about them changes for this project to land.

## Phased sequence

### Phase 1 — close the loose ends already in flight (no new scope)
- Land **AUTO-1** (Codex-app dispatcher round-aware fix) — your action, already briefed.
- Bundle in the **machine-header reminder** surfaced by Sprint 21 (Codex's `Verdict:`
  line needs the literal `ACCEPT`/`REJECT` token) — same prompt-edit session as AUTO-1.
- These are process-loop fixes, not go-live blockers, but they're small and already
  understood — clear them before adding new surface area.

### Phase 2 — lock the secrets/OAuth policy (decision, not code)
- `docs/security/secrets-and-test-data.md` currently guarantees **no real secrets
  anywhere in this repo** — real Google OAuth credentials break that invariant the
  moment they exist. Before any OAuth work starts, decide *where* a real client
  id/secret would live (VPS-only systemd `EnvironmentFile`, never committed, never in
  CI logs) and who owns rotating it if the repo or talk ever goes public.
- This phase blocks Phase 6, not Phases 3–5 — sequenced early because it's a policy call
  that shouldn't be made under the time pressure of "OAuth is half-wired and blocking a
  demo."

### Phase 3 — backend hosting on `dnit-vps` (reuses the Pet Giftshop pattern) — **DONE 2026-07-15**
Sprint 22 (artifacts, Codex-reviewed) + live-apply session (`docs/backlog/sprint-22-live-apply-runbook.md`).
All three Spring services live, publicly reachable, correctly verified by identity not
just uptime. Two real live-ops bugs found and fixed (sudoers exact-match, shared-tmp-path
race). **What this phase did NOT cover, discovered only once live: Kafka.** See Phase 3.5.
- Use the port scheme above (8090/8091/9000/9092/2181) — live-verify the three unclaimed
  ports are actually free before wiring the deploy workflow.
- Three systemd units (`pubrec-auth`, `pubrec-order`, `pubrec-inventory`), each a jar
  drop + restart, mirroring `petgiftshop-backend.service`. Reuse the
  `vps-nginx-systemd-spring` pattern already established for Pet Giftshop rather than
  inventing a new deploy shape.
- **Kafka/ZooKeeper: always-on**, per the resource-budget call above — no on-demand
  start/stop needed. Optional, lower-priority follow-up unrelated to that decision: the
  current compose still runs separate ZooKeeper + Kafka containers (Confluent 7.8.0) —
  collapsing to single-process **KRaft mode** would shave one JVM off the permanent
  footprint, worth doing opportunistically, not blocking.
- **Subdomains — decided 2026-07-15:** `saga-auth.dnit.be`, `saga-orders.dnit.be`,
  `saga-inventory.dnit.be` (mirrors the existing `app.petshop.dnit.be` pattern) + TLS via
  the same Let's Encrypt/Nginx pattern already in use for `dnit.be`.
- GitHub Actions deploy workflow mirroring `deploy-backend-production.yml` — manual
  dispatch to start, not auto-deploy-on-push, given this is a demo app where you want to
  control exactly when the public instance changes.
- **Scoping split for the actual sprint brief:** implementers (opencode+DeepSeek, normal
  worktree pipeline) author the deploy *artifacts* in-repo — systemd unit files, Nginx
  vhost templates, the GitHub Actions workflow — coordinator-reviewed the same way as
  any other logic-bearing task. The first **live application** of those onto `dnit-vps`
  (copying units, `systemctl enable/start`, live Nginx reload, requesting the Let's
  Encrypt certs) is a separate, explicitly-confirmed step done with Dimitri's sign-off in
  the loop — not something a worktree agent does autonomously against a box that already
  serves a paying customer.

### Phase 3.5 — deploy a Kafka broker on `dnit-vps` — **DONE 2026-07-15, live-verified**
Sprint 23 (Codex-reviewed, `1728fcd`) delivered `deploy/docker-compose/kafka-vps.yml`
(loopback-bound, `restart: unless-stopped`, byte-identical Kafka/ZooKeeper config to
local dev otherwise) + `deploy/systemd/pubrec-kafka.service`. Live-applied same day:
port binding verified three ways (`docker compose config` shows `host_ip: 127.0.0.1`,
`ss -tlnp` confirms loopback-only, external TCP probe from outside the VPS confirms
unreachable), `order`/`inventory` restarted and connected. Sprint 24's OutboxRelay fix
(found during this live-apply) then let a real order close the full saga loop
end-to-end on the public endpoints — see the roadmap status block above for the proof.
- KRaft single-process mode (shaving one JVM off the footprint) remains an optional,
  lower-priority future optimization, not part of this phase — deferred deliberately,
  not by oversight.
- This blocks Phase 4 in practice, if not formally: there's nothing worth demoing on the
  Vercel-hosted frontends without a working saga behind them.

### Phase 4 — frontend hosting on Vercel (reuses the existing pattern exactly)
- `vercel.json` rewrites + env-based API base URL pointing at Phase 3's subdomains.
- CORS allow-list on all three Spring services for the Vercel origins (currently CORS is
  presumably permissive/localhost-only for dev — needs an explicit prod origin list).
- Two Vercel projects (`order-ui`, `inventory-ui`), deploy-hook-triggered from GitHub
  Actions, same shape as the existing `vercel-deploy.yml` pattern.
- **Sequenced after Phase 3**, not parallel — the frontend env config needs Phase 3's
  real subdomains to point at; building it first just means redoing it.

### Phase 5 — security gate promotion (independent, can slot in anywhere)
- Promote the Snyk CI gate from report-only (`continue-on-error`) to blocking, once the
  dev-only Angular CVEs clear (standing caveat, recheck each sprint per the dependency-
  currency practice). Low coupling to everything else — can land whenever the CVE
  condition clears, doesn't need to wait for Phase 3/4.

### Phase 6 — real Google OAuth end-to-end + CI
- Gated on Phase 2 (secrets policy) **and** Phase 3/4 (OAuth redirect URIs need a real,
  stable HTTPS origin — testing this meaningfully against `localhost` or ephemeral CI
  doesn't prove much). This is why it's sequenced last among the code-facing items, not
  because it's unimportant.

### Phase 7 — operational polish
- Build the **AUTO-3 operational driver** (interval runner for `review-intake.sh` +
  push + notify) — the piece the merge-gate policy conversation was specifically about.
  Needs your sign-off per that agreement; natural to build once the loop is otherwise
  quiet (post Phase 1) rather than while other infra work is also landing.
- Optional Testcontainers major-version bump (1.21.3→2.0.5) — pure tech debt, no
  go-live dependency, can happen whenever there's a quiet sprint.

### Phase 8 — real database instead of in-memory H2 (added 2026-07-16, deferred/optional)
All three Spring services currently use in-memory H2 (`orderdb`, `inventorydb`,
`authdb`) — a deliberate simplification from earlier in this roadmap (removes DB
provisioning/backups/migration as a go-live concern entirely), not an oversight. Real
consequence surfaced live: every backend restart wipes all data (users, orders,
approvals), which is why re-approving a registered user was needed again after a
redeploy. Dimitri's call (2026-07-16): make this **optional, scope it when we actually
get here** — not blocking any current phase. When it is scoped, open questions to
resolve then, not now: which service(s) actually need persistence for the demo to still
make sense (all three vs. just the ones where wiped state is actually annoying),
engine choice (Postgres is the obvious default given `dnit-vps` already runs Postgres
for other projects per the roadmap's port table), and whether this is a shared instance
or one per service given each currently owns its schema independently.

## Current state (updated 2026-07-16)

Phases 1–4 are done (Phase 4 live-verified end-to-end: real account registered, admin-
approved, login, orders placed for SKU 1–3, reserved/rejected statuses both confirmed
working in the UI on the actual public `*.vercel.app` + `saga-*.dnit.be` endpoints).
Phase 5 (Snyk gate promotion) and Phase 6 (real Google OAuth) remain open, gated as
described above. Phase 7 (AUTO-3 operational driver) still needs Dimitri's sign-off to
start. Phase 8 (real DB) is new, deliberately deferred/optional per the note above.
