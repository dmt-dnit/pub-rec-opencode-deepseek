# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

A demo/learning monorepo of independent services that together illustrate JWT-based SSO + a choreographed saga on Kafka:

- `shared-model` — Maven library of cross-service **contracts** (DTOs, Kafka events). No service-specific code.
- `auth-server` — Spring Boot SSO/auth service (port **9000**). Issues RSA-signed JWTs, exposes JWKS, email/password + Google OAuth2 login.
- `order-service` (port **8080**) and `inventory-service` (port **8081**) — two Spring Boot services that implement a **choreographed saga**: Order Service publishes `OrderPlacedEvent`, Inventory Service reserves stock and responds with `InventoryReservationEvent`, Order Service consumes the response and updates order status. No central orchestrator.
- `order-ui` (port **4200**) and `inventory-ui` (port **4201**) — two Angular 22 SPAs, each paired 1:1 with its corresponding backend service.

See `docs/adr/0001-event-driven-showcase-architecture.md` for the canonical architecture description.

There is no root build — each subdirectory is built/run independently. Build artifacts (`target/`, `dist/`) are committed to git — there is no `.gitignore`. Don't be alarmed by `git status` showing changes under those paths after a build; check whether the user actually wants them committed before doing so.

## Multi-agent development workflow

This is the showcase project for Dimitri and his son's IT startup, built with a multi-agent loop rather than a single developer:

- **opencode + DeepSeek** — implementers. Each picks up one task brief at a time and turns it into code changes.
- **Claude Code (this assistant)** — sprint coordinator. Writes per-task briefs, verifies completed work against acceptance criteria by reading the actual code/diffs (never by trusting the implementing agent's self-report), and writes a handoff doc for Codex.
- **Codex** — reviewer. Reads the handoff, re-verifies independently, and issues an accept/reject verdict with blockers. That verdict gates whether the sprint closes and the next one starts.

### File conventions
- `docs/backlog/sprint-N.md` — sprint overview: why it exists, task list, recommended order.
- `docs/backlog/tasks/sprint-N/<ID>-<slug>.md` — one brief per task (e.g. `F-1-fix-frontend-proxy-routing.md`), each with acceptance criteria.
- `docs/backlog/sprint-N-handoff.md` — point-in-time status check written by Claude before sending the sprint to Codex, verified against acceptance criteria by reading code.
- `reviews/sprint-N-track-X-review.md` — Codex's review verdict. If rejected, its blockers become the next sprint's backlog (e.g. `sprint-2.md`'s "Why this sprint exists" cites `reviews/sprint-1-track-a-review.md` directly).
- `docs/demo-notes-sprint-N.md` — Codex's short presentation-prep note after each review: demo-worthy story beats, screenshots/video captured or missing, commands worth replaying, and any evidence the conference-prep session should ingest.

### Sprint cadence
1. Claude writes/refines task briefs for the sprint. Before writing a new sprint's briefs, check whether any major framework/runtime dependency (Angular, Spring Boot, the pinned Java version, Kafka client libs) is approaching or past its LTS/support window — don't wait for `npm audit` or a security review to surface it. See "Dependency currency" below for why this matters.
2. opencode+DeepSeek implement each brief — prefer isolated worktrees/branches per task. Sprint 1 ran concurrent edits on one shared tree, which produced incomplete renames across files; don't repeat that.
3. Claude verifies against acceptance criteria by reading the actual diffs/code, then writes a handoff doc.
3.5. Before telling Codex the sprint is ready: run `scripts/pre-review-check.sh <sprint-number>`. It fails closed if the working tree isn't committed or the handoff doc is missing — exactly the gap that let Sprint 4 round 2 reach Codex with 2 of 5 tasks unstarted and no commit checkpoint. Don't skip this because "DeepSeek says it's done" — that self-report is the thing this whole cadence exists to not trust.
4. Codex reviews independently and gives a verdict.
5. Reject → blockers become the next sprint's backlog. Accept → next sprint/track starts.

### Who implements what

Default: opencode+DeepSeek implement all code changes via isolated worktrees. Claude is coordinator, not implementer.

**Coordinator-direct** is acceptable only for changes where the diff is trivially obvious, non-controversial, and fully verifiable by reading alone: updating a snapshot paragraph in CLAUDE.md, fixing a doc typo, removing a clearly dead import. Not for logic, type, or config changes — even one-liners. Sprint 11 O-1/O-2 went coordinator-direct and passed Codex, but the precedent is bad: it skips the agent self-report / coordinator diff-check / Codex code-review chain that the whole workflow depends on.

**Claude agent (worktree) as fallback** — if opencode is unavailable or repeatedly failing, spawn a Claude subagent with `isolation: "worktree"` as a stand-in implementer. Use `model: "haiku"` for mechanical tasks (lockfile edits, selector fixes, single-import tweaks) and `model: "sonnet"` or `model: "opus"` for logic-heavy work. The same rules still apply: worktree isolation, coordinator reads the actual diff to verify, handoff doc written before Codex sees it. A Claude agent is not exempt from the "show actual output, not asserted Pass" standard.

### Dependency currency
Angular 18.2.x (this repo's original pin) was very likely the correct LTS choice when the project was set up — Angular's LTS window is 18 months from release, and 18 was current/active at that time. The problem that produced sprints F-6/G-4/H-3 wasn't the initial pick, it was two sprints' worth of silent drift afterward: nobody checked dependency currency until `npm audit` forced the question, by which point the project was several majors behind and facing one large, risky `ng update` migration instead of several small, cheap ones along the way. Checking EOL/LTS status at the start of each sprint (step 1 above) turns that into routine hygiene instead of a late, compounded surprise — this applies to the Java version pin and Spring Boot/Kafka client versions too, not just Angular.

### Verification standards for implementers
Sprints 1-3 showed a recurring failure mode: a task report claims a check "Pass" based on a proxy signal (a file matching a known-good template, an unrelated test going green, general knowledge of a package's patched version) rather than actually exercising the real check on the real target. Concrete examples Codex caught after a "Pass" was reported: `mvnw.cmd` matched the genuine Maven Wrapper template byte-for-byte but still errored in real PowerShell; a test suite passed while the logs showed the intended fix (a Mockito mock-maker override) wasn't actually the reason it passed; a specific patched-version number was reported without re-checking current advisory data.

To avoid a fourth round of this: when a task brief's acceptance criteria includes running a command or reproducing a result, **show the actual command output**, don't just assert "Pass/Fail." If the target platform/environment for a check isn't available to you (e.g. no Windows/PowerShell, no Docker/Podman daemon, no access to the specific JDK vendor a reviewer uses), **say so explicitly** rather than inferring success from whatever you can run instead. A stated limitation is useful information for the next reviewer; an unverified "Pass" that turns out false costs a whole sprint round.

### Sprint rules card (coordinator + reviewer reference)

This is the compact ruleset distilled from the Track A retrospective. Full context: `docs/backlog/track-a-retro.md`.

**Coordinator (Claude) — every sprint:**
1. Acceptance criteria = observable outcome, not implementation step. Rewrite if it can only be verified by reading intent.
2. Read the actual template/DOM before writing any E2E selector. Include the relevant snippet in the brief.
3. E2E tasks assume dirty DB. Idempotency across repeated runs is a first-class criterion.
4. Max ~3 loosely coupled tasks per sprint. Tightly coupled tasks go in one brief. Break large tasks before briefing.
5. Note agent choice in every handoff. Default: haiku for mechanical, sonnet/opus for logic. OpenCode+DeepSeek only on explicit trigger (two failed rounds, ambiguous architecture, security/performance, genuine second-opinion value).
6. Worktree agent: verify the branch has new commits. If not, apply diff manually to main and note it in handoff.
7. `bash scripts/pre-review-check.sh <N>` must pass before handing to Codex. No exceptions.
8. Handoff must include: exact commits · done/not-done · actual command output · what couldn't run and why · browser status ("passed" or "Codex-only") · `git status --short` confirming clean tree.
9. `npm audit --omit=dev` is the pass/fail signal. Full audit goes in caveats, not the scorecard.
10. Update CLAUDE.md status snapshot every sprint.

**Reviewer (Codex) — every review:**
1. Check acceptance criteria format — flag "use this locator" criteria, don't just test against them.
2. Browser tasks must say "browser accepted" or "Codex-only verification." No silent hand-offs.
3. E2E tasks: clean or dirty DB? Dirty is the default; clean-only is a weaker result.
4. `git status --short` must be clean. Generated artifacts in the diff are a flag.
5. Standing caveats: don't re-litigate. Check only if the signal changed. Reference the caveats table in this file.
6. Write the sprint review to `reviews/*` and also write `docs/demo-notes-sprint-N.md` for the conference-prep thread. If there is nothing presentation-worthy, say that explicitly in the note.
7. No source edits from the reviewer path.

### Current status snapshot (2026-06-28)

**Current track:** Track B — hardening (unblocked as of 2026-06-28)  
**Last approved sprint:** Sprint 14 (Track B Sprint 1) — approved by Codex on 2026-06-29 (round 2, `reviews/sprint-14-track-b-round-2-review.md`); live CI green (Actions run `28354413116`, all 6 jobs). Delivered B-5 (CI pipeline) + B-1 (retry/DLQ/idempotency). Round-1 blockers P1 (mvnw exec bit `adaf54e`) + P2 (outcome-idempotency `a6d71b6`) closed; first live run's artifact-handoff failure fixed inline `f5d81b4`.  
**Next sprint entry point:** Sprint 15 (Track B Sprint 2) — briefs not yet written. Backlog: remaining Track B (`docs/backlog/sprint-1.md` §Track B) B-2 (observability), B-3 (Testcontainers + contract tests), B-4 (OpenAPI), B-6 (Docker Compose, likely its own sprint) — **plus 3 non-blocking follow-ups from the Sprint 14 round-2 review** (`reviews/sprint-14-track-b-round-2-review.md`): (1) **CI dependency currency** — bump `actions/checkout` v4→v7, `setup-java` v4→v5, `setup-node` v4→v6, verify via live run; (2) **inventory exactly-once publish hardening** — outbox / transactional-producer / persisted publish-state (current no-op idempotency is crash-safe only for normal duplicate delivery); (3) **server-side vuln visibility** — enable Dependabot alerts and/or add a Maven vuln scan (e.g. OWASP dependency-check) with a fail/pass policy. Apply the dependency-currency check (cadence step 1) when scoping. B-7 already complete.  
**Pre-review command:** `bash scripts/pre-review-check.sh <sprint-number>`

**Active caveats (do not re-litigate each sprint — update only when signal changes):**
- `npm audit --omit=dev` → 0 vulnerabilities in both UIs (production clean)
- Full `npm audit` → 8 dev-tooling advisories per UI (piscina/vite under `@angular-devkit/build-angular`); no forward fix at Angular 22.0.4 floor
- Angular webpack builder deprecation warning — not a blocker yet; track at next Angular version upgrade
- Mockito/Java agent self-attach warning (OpenJ9 environments) — accepted, documented in Sprint 2 review
- Testcontainers/Surefire shutdown warning in inventory-service — exit 0, noisy; clean up in B-3
- GitHub Actions Node 20 deprecation: `actions/checkout@v4`/`setup-java@v4`/`setup-node@v4` auto-forced onto Node 24, jobs pass. Newer majors exist (checkout v7, setup-java v5, setup-node v6) — scheduled as Sprint 15 follow-up 1 (added Sprint 14)

**Track A history (for reference):** 13 sprints, Sprints 1–13, closed 2026-06-28. Full Playwright smoke test (login → order placement → Kafka saga → live status update → inventory decrement) passes against a non-empty local DB. See `docs/backlog/track-a-retro.md` for lessons learned.

All 4 Maven modules have working `mvnw.cmd` (Windows) and `mvnw` (Unix) wrappers. Maven Enforcer requires Java 21 (`[21,22)`) and fails fast otherwise.

## Architecture

### Auth flow
`auth-server` is the single source of truth for identity. It signs JWTs with an RSA key pair and publishes the public key via JWKS (`/oauth2/jwks`). `order-service` and `inventory-service` are pure JWT **resource servers** — they have no user store of their own; they validate tokens by fetching `auth-server`'s JWKS at `http://localhost:9000/oauth2/jwks` (see each service's `config/SecurityConfig.java`).

Registration is gated: new users land in `UserEntity.Status.PENDING` and cannot log in until an admin flips them to `ACTIVE` via `AdminController` (`PUT /api/admin/users/{id}/approve`, requires `ROLE_ADMIN`). Roles are carried in the JWT's `role` claim and mapped to Spring `ROLE_*` authorities by `JwtAuthenticationConverter` in each service's security config. Seeded roles: `ADMIN`, `CUSTOMER`, `WAREHOUSE_STAFF`.

`auth-server` uses an in-memory H2 database (`DataSeeder` seeds initial data on startup) and also supports Google OAuth2 login as an alternate path into the same JWT issuance flow.

### Kafka choreographed saga
`order-service` and `inventory-service` implement a two-step choreographed saga over Kafka:

- `order-service` owns `Order` locally, exposes `POST /api/orders`, publishes `OrderPlacedEvent` to `order-events`, consumes from `inventory-events`.
- `inventory-service` owns `Product`/`Stock` locally, consumes from `order-events`, attempts to reserve stock, publishes `InventoryReservationEvent` (`RESERVED` or `REJECTED`) to `inventory-events`.
- `order-service` consumes `inventory-events` and updates order status, closing the saga loop.

Each service's listener pushes received events to its own connected browsers over STOMP/WebSocket (`SimpMessagingTemplate` → `/topic/messages`), which the corresponding Angular UI subscribes to via `websocket.service.ts`.

The shared event contracts live in `shared-model`: `OrderPlacedEvent`, `InventoryReservationEvent`, `OrderItem`. Both services trust this package for JSON deserialization (`spring.json.trusted.packages` in each `application.yml`).

A local Kafka broker is required to run the publish/consume flow; `order-service/docker-compose.yml` provides a single-node Kafka+Zookeeper stack on the default ports (`2181`, `9092`).

### shared-model contract boundary
`shared-model` holds only things that cross a service boundary on the wire: DTOs (`LoginRequest`, `LoginResponse`, `RegisterRequest`) and Kafka events (`OrderPlacedEvent`, `InventoryReservationEvent`, `OrderItem`). Each service owns its JPA-persisted entities locally (`order-service` owns `Order`, `OrderLineItem`; `inventory-service` owns `Product`; `auth-server` owns `UserEntity`).

### Frontend
`order-ui` and `inventory-ui` are structurally identical Angular 22 + Angular Material SPAs (compare `proxy.conf.json` in each to see which backend port they target). Both proxy `/api/auth/**`, `/oauth2/**`, `/login/**` to `auth-server` (9000). `order-ui` proxies `/api/orders/**` + `/ws/**` to `order-service` (8080); `inventory-ui` proxies `/api/inventory/**` + `/ws/**` to `inventory-service` (8081). `auth.interceptor.ts` attaches the JWT as a Bearer token on outgoing requests; `auth.guard.ts` gates routes on `AuthService.isLoggedIn()`.

## Common commands

### Java services (Maven)
Build order matters — `shared-model` must be installed to the local Maven repo before the services that depend on it will compile:

```bash
cd shared-model && ./mvnw clean install
cd ../auth-server && ./mvnw clean compile
cd ../order-service && ./mvnw clean compile
cd ../inventory-service && ./mvnw clean compile
```

The Maven Enforcer plugin is configured in each module to require Java 21 (`<requireJavaVersion>[21,22)</requireJavaVersion>`). Builds fail fast if you're not on JDK 21.

Run a single service: `./mvnw spring-boot:run` from within that service's directory.

Run tests for one module: `./mvnw test` (from `order-service` or `inventory-service`; e.g. `./mvnw -Dtest=OrderEventPublisherTest test` for a single test class).

Start the local Kafka broker needed by `order-service`/`inventory-service`: `cd order-service && docker compose up -d`.

### Angular UIs
Each UI is a separate npm project — there's no root `package.json`:

```bash
cd order-ui && npm install && npm start     # ng serve, port 4200
cd inventory-ui && npm install && npm start # ng serve, port 4201
```

`npm start` runs `ng serve` with `proxyConfig: proxy.conf.json`, so the backing Java services must already be running for API/WebSocket calls to succeed.

### Full local stack
To exercise the whole demo end-to-end: start the Kafka broker (`order-service/docker-compose.yml`), then `auth-server` (9000), then both `order-service` (8080) and `inventory-service` (8081), then `order-ui` (4200) and/or `inventory-ui` (4201).
