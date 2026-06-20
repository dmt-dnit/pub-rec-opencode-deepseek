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
1. Claude writes/refines task briefs for the sprint.
2. opencode+DeepSeek implement each brief — prefer isolated worktrees/branches per task. Sprint 1 ran concurrent edits on one shared tree, which produced incomplete renames across files; don't repeat that.
3. Claude verifies against acceptance criteria by reading the actual diffs/code, then writes a handoff doc.
4. Codex reviews independently and gives a verdict.
5. Reject → blockers become the next sprint's backlog. Accept → next sprint/track starts.

### Current status snapshot (2026-06-20 — re-check before relying on this)
Sprint 1 (Track A: Order/Inventory domain pivot) was rejected by Codex — see `reviews/sprint-1-track-a-review.md`. Sprint 2 (Track A stabilization, tasks F-1–F-7) is in progress; latest status in `docs/backlog/sprint-2-handoff.md`. Per that snapshot: F-1, F-2, F-3, F-4, F-7 done; F-6 (Angular dependency remediation) unverified; F-5 (rename cleanup + rewriting this file's architecture sections to drop old `kafka-demo`/`article` naming) not started — it's sequenced last since it documents the true end state. Track B hardening (`docs/backlog/sprint-1.md`) does not start until Sprint 2 is fully verified and re-reviewed by Codex.

Known infra issue: `./mvnw` has been throwing errors during DeepSeek's runs. Check `.mvn/wrapper/maven-wrapper.properties` (added 2026-06-19) and commit history for resolution status before assuming it's fixed.

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
