# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

A demo/learning monorepo of independent services that together illustrate JWT-based SSO + a choreographed saga on Kafka:

- `shared-model` — Maven library of cross-service **contracts** (DTOs, Kafka events). No service-specific code.
- `auth-server` — Spring Boot SSO/auth service (port **9000**). Issues RSA-signed JWTs, exposes JWKS, email/password + Google OAuth2 login.
- `order-service` (port **8080**) and `inventory-service` (port **8081**) — two Spring Boot services that implement a **choreographed saga**: Order Service publishes `OrderPlacedEvent`, Inventory Service reserves stock and responds with `InventoryReservationEvent`, Order Service consumes the response and updates order status. No central orchestrator.
- `order-ui` (port **4200**) and `inventory-ui` (port **4201**) — two Angular 18 SPAs, each paired 1:1 with its corresponding backend service.

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

### Dependency currency
Angular 18.2.x (this repo's original pin) was very likely the correct LTS choice when the project was set up — Angular's LTS window is 18 months from release, and 18 was current/active at that time. The problem that produced sprints F-6/G-4/H-3 wasn't the initial pick, it was two sprints' worth of silent drift afterward: nobody checked dependency currency until `npm audit` forced the question, by which point the project was several majors behind and facing one large, risky `ng update` migration instead of several small, cheap ones along the way. Checking EOL/LTS status at the start of each sprint (step 1 above) turns that into routine hygiene instead of a late, compounded surprise — this applies to the Java version pin and Spring Boot/Kafka client versions too, not just Angular.

### Verification standards for implementers
Sprints 1-3 showed a recurring failure mode: a task report claims a check "Pass" based on a proxy signal (a file matching a known-good template, an unrelated test going green, general knowledge of a package's patched version) rather than actually exercising the real check on the real target. Concrete examples Codex caught after a "Pass" was reported: `mvnw.cmd` matched the genuine Maven Wrapper template byte-for-byte but still errored in real PowerShell; a test suite passed while the logs showed the intended fix (a Mockito mock-maker override) wasn't actually the reason it passed; a specific patched-version number was reported without re-checking current advisory data.

To avoid a fourth round of this: when a task brief's acceptance criteria includes running a command or reproducing a result, **show the actual command output**, don't just assert "Pass/Fail." If the target platform/environment for a check isn't available to you (e.g. no Windows/PowerShell, no Docker/Podman daemon, no access to the specific JDK vendor a reviewer uses), **say so explicitly** rather than inferring success from whatever you can run instead. A stated limitation is useful information for the next reviewer; an unverified "Pass" that turns out false costs a whole sprint round.

### Current status snapshot (2026-06-22 — re-check before relying on this)
Sprint 1 (Track A: Order/Inventory domain pivot) was rejected by Codex — see `reviews/sprint-1-track-a-review.md`. Sprint 2 (Track A stabilization, tasks F-1–F-7) was rejected by Codex — all functional fixes (F-1–F-4, F-7) were confirmed correct in code, but the reviewer's Windows/OpenJ9 environment couldn't run the tests at all (`mvnw.cmd` missing, Mockito self-attach crash, @EmbeddedKafka JIT segfault). Sprint 3 (Track A close-out, tasks G-1–G-5) was rejected by Codex — `order-service`'s OpenJ9 portability was confirmed as real progress, but `mvnw.cmd` was broken at runtime (only-script mode), inventory-service tests hard-failed without Docker, and Angular advisories remained unresolved at 30 high. Sprint 4 (Track A close-out, round 2, tasks H-1–H-5) addresses those regressions:

- **H-1** — `mvnw.cmd` regenerated in classic jar-based mode (`distributionType=bin`) across all 4 Maven modules, replacing the broken only-script mode; `.\mvnw.cmd -v` reports Maven 3.9.9 on PowerShell.
- **H-2** — `InventoryIntegrationTest` gated on `DockerClientFactory.isDockerAvailable()` via JUnit 5 `Assumptions.assumeTrue`; test skips gracefully (exit 0) when no Docker/Podman is reachable.
- **H-3** — Angular upgraded from 18.2.x → 21.2.x across both UIs (`ng update` stepped through majors 19, 20, 21). High-severity advisories: 30 → 6. Remaining 6 are in transitive build tooling requiring Angular 22 (pre-release). Both UIs build clean.
- **H-4** — Mockito maker override file exists on classpath but is not being read at runtime by Mockito 5.14.2's plugin loader (no conflicting resource found in any jar). Fallback: added `-Djdk.attach.allowAttachSelf=true` to surefire `argLine` in `order-service/pom.xml`. All 3 tests pass on OpenJ9 (Semeru 21.0.6).
- **H-5** — This status snapshot updated; Track B pointer fixed to `docs/backlog/sprint-1.md`; snapshot accurately reflects Sprint 4's outcome per local verification, pending Codex re-review.

Track B hardening (`docs/backlog/sprint-1.md`) remains gated — does not start until Sprint 4 is verified and re-reviewed by Codex.

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
`order-ui` and `inventory-ui` are structurally identical Angular 18 + Angular Material SPAs (compare `proxy.conf.json` in each to see which backend port they target). Both proxy `/api/auth/**`, `/oauth2/**`, `/login/**` to `auth-server` (9000). `order-ui` proxies `/api/orders/**` + `/ws/**` to `order-service` (8080); `inventory-ui` proxies `/api/inventory/**` + `/ws/**` to `inventory-service` (8081). `auth.interceptor.ts` attaches the JWT as a Bearer token on outgoing requests; `auth.guard.ts` gates routes on `AuthService.isLoggedIn()`.

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
