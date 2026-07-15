# Track C — go-live roadmap

**Written:** 2026-07-15. **Status:** proposed sequencing, not yet a sprint. Track B
(hardening) is functionally done; this is the answer to "what Track C is."

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

## A real conflict this surfaces — needs your decision before Phase 3

`order-service` defaults to port **8080**, `inventory-service` to **8081**. On
`dnit-vps`, `8080` is Pet Giftshop **production** and `8081` is the **file-upload API**
— both already serving real traffic. This project cannot bind those ports on that box.
Two independent things to decide, not just one remap:

1. **Ports.** Pick non-colliding internal ports for the VPS deployment only (local dev
   keeps today's 8080/8081/9000 unchanged) — e.g. `8090`/`8091`, `9000` looks free per
   the infra map but that should be confirmed with a live check on the box, not assumed
   from a doc that could be stale.
2. **Resource budget.** This box already runs a paying customer's production API.
   Adding a 3-JVM Spring stack + a 2-container Kafka/ZooKeeper broker is a real
   footprint, not a free lunch, even if the ports don't collide. I'm not going to assume
   "there's obviously enough headroom" — that's worth an explicit check (current
   `free -h`/`docker stats` on the box) and an explicit call from you on whether it runs
   continuously or only during active demos (see Phase 3).

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

### Phase 3 — backend hosting on `dnit-vps` (reuses the Pet Giftshop pattern)
- Resolve the port conflict + resource-budget question above.
- Three systemd units (`pubrec-auth`, `pubrec-order`, `pubrec-inventory`), each a jar
  drop + restart, mirroring `petgiftshop-backend.service`. Reuse the
  `vps-nginx-systemd-spring` pattern already established for Pet Giftshop rather than
  inventing a new deploy shape.
- **Kafka/ZooKeeper footprint decision:** this is a demo, not a 24/7 product — default
  recommendation is systemd units that are **enabled but not started by default**, so
  Kafka only runs when someone's about to demo/present, not burning RAM on the shared
  box year-round. Flip to always-on only if you want a permanently-live public URL to
  hand out. Optional, lower-priority follow-up: the current compose still runs separate
  ZooKeeper + Kafka containers (Confluent 7.8.0) — collapsing to single-process **KRaft
  mode** would shave one JVM off the footprint, worth doing once the on-demand-vs-always
  call is made, not before.
- Nginx vhosts + subdomains (pick a naming convention — e.g. `saga-auth.dnit.be`,
  `saga-orders.dnit.be`, `saga-inventory.dnit.be`, or nest under a single
  `saga-demo.dnit.be` path-based proxy) + TLS via the same Let's Encrypt/Nginx pattern
  already in use for `dnit.be`.
- GitHub Actions deploy workflow mirroring `deploy-backend-production.yml` — manual
  dispatch to start, not auto-deploy-on-push, given this is a demo app where you want to
  control exactly when the public instance changes.

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

## What I'd scope as the actual next sprint

Given the sequencing above, the next concrete sprint is **Phase 1 + the Phase 3
decisions** (ports, resource budget, on-demand-vs-always-on Kafka) — the latter needs
your input, not a task brief, since it's about a shared box that already serves a real
customer. Once those two decisions are made, Phase 3's systemd/Nginx work is a normal
sprint brief in the usual mold.
