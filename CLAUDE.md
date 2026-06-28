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

### Current status snapshot (2026-06-28 — re-check before relying on this)
Sprint 1 (Track A: Order/Inventory domain pivot) was rejected by Codex — see `reviews/sprint-1-track-a-review.md`. Sprint 2 (Track A stabilization, tasks F-1–F-7) was rejected by Codex — all functional fixes (F-1–F-4, F-7) were confirmed correct in code, but the reviewer's Windows/OpenJ9 environment couldn't run the tests at all (`mvnw.cmd` missing, Mockito self-attach crash, @EmbeddedKafka JIT segfault). Sprint 3 (Track A close-out, tasks G-1–G-5) was rejected by Codex — `mvnw.cmd` was broken at runtime (only-script mode), inventory-service tests hard-failed without Docker, Angular advisories remained unresolved at 30 high. Sprint 4 (Track A close-out, round 2, tasks H-1–H-5) made real progress — `mvnw.cmd` fixed (classic jar mode), Angular upgraded to 21.2.x, Mockito/OpenJ9 fallback accepted — but did not fully close Track A. Sprint 5 (Track A close-out, round 3) was rejected by Codex — the Docker-optional test still reported `tests="0"` instead of `skipped="1"`, Angular lockfiles/config still diverged between UIs, and no browser smoke test existed. Sprint 6 (Track A close-out, round 4, tasks J-1–J-3) addresses the remaining gaps:

- **J-1** — Docker check moved from class-level `@BeforeAll` (which suppressed test registration) into the test method via `Assumptions.assumeTrue`; Surefire XML now reports `tests="1" skipped="1"` when no Docker is reachable.
- **J-2** — Both UIs normalized: lockfiles aligned (delete+reinstall from identical `package.json`), `angular.json` schematics/buildTarget blocks matched. `npm run build` passes in both. `npm audit`: 14 total, 6 high (all in transitive build tooling — `@angular-devkit/build-angular`, `http-proxy-middleware`, `piscina`, `vite`, `undici`; require Angular 22). Dev servers respond on 4200/4201; SPA routes confirmed.
- **J-3** — This status snapshot updated to accurately reflect Sprint 4's real progress, Sprint 5's rejection, and Sprint 6's outcome per local verification, pending Codex re-review.

Sprint 7 (Track A close-out, round 5) narrowed to a single open task: two of its three planned tasks were closed by direct work this session (outside the Codex implement/review loop), leaving only the browser smoke test.

- **K-1 (resolved this session, commit `ab37adc`)** — Both UIs upgraded to Angular **22.0.2** / TypeScript **6.0.3** / zone.js **0.16.2** (the versions Sprint 6's review named as current). `npm run build` passes in both; lockfiles stay twin-consistent. A fresh `npm audit` now reports **10 vulnerabilities (4 high, 3 moderate, 3 low)** in each UI — down from Sprint 6's 14/6-high. The remaining 4 highs are all `piscina` (advisory `GHSA-x9g3-xrwr-cwfg`, prototype-pollution→RCE) transitive under `@angular-devkit/build-angular`; npm's only offered remediation is a force-downgrade to `@angular-devkit/build-angular@0.802.2` (a breaking regression, not a fix). They are unresolved-at-current-floor **build-tooling** advisories, not runtime exposure. `npm outdated` shows both UIs at or ahead of the stable `latest` dist-tag — nothing newer to move to.
- **K-3 (resolved this session, commit `ab37adc`)** — The stale "Angular 18" text at `CLAUDE.md:12` and `:85` now reads "Angular 22"; the only remaining "Angular 18" mention is line 41's historical Dependency-currency note, which is correct as written.
- **K-2 (closed — Sprint 13, commit `d93c617`)** — Full Playwright smoke test passes end-to-end. Track A gate cleared by Codex on 2026-06-28.

**Track A is closed.** Sprint 13 (Q-1: `data-testid="order-card"` + smoke test locator fix) was approved by Codex on 2026-06-28. The full Playwright smoke test passed end-to-end against a non-empty local DB. Track B hardening is now unblocked.

All 4 Maven modules now have working `mvnw.cmd` (Windows) and `mvnw` (Unix) wrappers via `.mvn/wrapper/maven-wrapper.properties`. The Maven Enforcer plugin (`[21,22)`) fails fast if the active JDK is not Java 21.

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
