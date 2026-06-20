# ADR-0001: Event-driven order/inventory showcase on Kafka

**Status:** Accepted
**Decision makers:** Dimitri Gevers (architect/analyst role, this session) + co-founder
**Scope:** Defines the target architecture for the consultancy showcase repo and the boundary of "production-grade" for sprint 1. Feeds the sprint-1 backlog (`docs/backlog/sprint-1.md`) that gets decomposed into tasks for the implementation agent (DeepSeek) and reviewed by an independent agent (Codex/GPT).

## Context

The repo currently contains a working but artificial demo: two Spring Boot services (`kafka-demo`, `kafka-demo-2`) mirror-publish an `Article` event back and forth purely to exercise Kafka, fronted by two near-identical Angular apps and a shared JWT-issuing `auth-server`. It proves the plumbing works but tells no believable story — a prospective client watching the demo sees two services pretending to be each other, not a recognizable business process.

The stack (Java/Spring Boot, Angular) is fixed by company default and out of scope for this decision.

## Decision

### 1. Event-driven (Kafka), not synchronous REST mesh or a managed cloud event bus

Three realistic options for the showcase's communication style:

| Option | Why not chosen |
|---|---|
| Synchronous REST/RPC between services | Easiest to build, but demonstrates none of the resilience/decoupling/audit story clients are actually buying when they hire a consultancy for "event-driven architecture." Failure of one service cascades directly to callers. |
| Managed cloud event bus (e.g. a vendor pub/sub) | Ties the showcase to one cloud vendor and can't be demoed offline/on-prem, which matters for a consultancy whose clients run mixed environments. |
| Kafka (chosen) | Self-hostable, cloud-agnostic, and the specific skill set (topic design, consumer groups, replay, schema evolution, DLQ/retry semantics) is what's actually being screened for. The cost is real operational surface — a broker, schema governance, delivery semantics — but handling that surface *correctly* is the differentiator the showcase needs to prove. |

### 2. Domain: choreographed Order → Inventory saga, replacing the Article mirror

Reframe the existing two-service, two-topic topology (no new services needed) as a real distributed-systems pattern instead of an arbitrary mirror:

- **Order Service** (formerly `kafka-demo`): owns `Order` locally, exposes `POST /api/orders`, publishes `OrderPlacedEvent` to `order-events`.
- **Inventory Service** (formerly `kafka-demo-2`): owns `Product`/`Stock` locally, consumes `order-events`, attempts to reserve stock, publishes `InventoryReservationEvent` (`RESERVED` or `REJECTED`) to `inventory-events`.
- **Order Service** consumes `inventory-events` and updates its own order's status — closing the loop as a choreographed saga rather than orchestration, since no central coordinator is needed for a two-step flow.

This is the **minimal-diff pivot**: it reuses the existing bidirectional two-topic wiring almost exactly as already built, but the payloads and the consumer logic now mean something. `auth-server` is unchanged in role; its seeded roles become domain-realistic (`CUSTOMER`, `WAREHOUSE_STAFF`, `ADMIN`) instead of generic `USER`/`ADMIN`.

Rejected alternative: adding a third service (e.g. a notification service) for a linear three-hop flow. More realistic still, but it's new infrastructure rather than a reshape of what exists, and a two-step choreographed saga already demonstrates the pattern (event causation, eventual consistency, compensating status) the showcase needs. Promoted to the sprint-2+ backlog as a stretch item.

### 3. "Production-grade" scope boundary: polished local/CI grade, not cloud-deployable

Explicitly **out of scope** for this showcase: Kubernetes manifests/Helm, infrastructure-as-code, multi-environment config, managed cluster deployment. These are real consultancy deliverables but orthogonal to what this repo demonstrates (event-driven Java/Angular engineering quality), and adding them now would dilute sprint 1 into infra work instead of code quality.

**In scope** — the things that separate a tutorial from a showcase a client should trust with production code:

- Deterministic, one-command local environment (Docker Compose covering broker + all services)
- Automated tests beyond happy-path unit tests: Testcontainers-backed Kafka integration tests, contract tests on the shared event schemas
- Failure handling: retry with backoff, dead-letter topics, idempotent consumers (no silent message loss or duplicate processing)
- Observability: structured logs with correlation IDs propagated across the HTTP→Kafka→HTTP hop, Actuator health/metrics
- Documented API contracts (OpenAPI) for every REST-exposing service
- CI pipeline that builds and tests every module on every change
- Realistic but clearly fictitious seed/test data — no ambiguity about whether any data could be mistaken for a real client's

### 4. shared-model contract boundary gets enforced, not just documented

`task001_share_flows_own_entities.md` already specified that `shared-model` should hold contracts only and that `Article` (a JPA `@Entity`) didn't belong there. That refactor was never applied. The domain pivot is also the moment to finish it: `shared-model` ends up holding only `OrderPlacedEvent`, `InventoryReservationEvent`, `LoginRequest`, `LoginResponse`, `RegisterRequest` — no `@Entity` classes, no `jakarta.persistence` dependency.

## Consequences

- Every reference to "article" in code, topics, package names, and UI copy needs to be renamed — this is a large mechanical task, well suited to a workhorse implementation pass, but it touches nearly every file in `kafka-demo`, `kafka-demo-2`, `shared-model`, and both UIs. It should be done as its own task (sprint-1, Track A) before any hardening work lands on top of it, to avoid the review agent reviewing a moving target.
- `kafka-demo`/`kafka-demo-2`/`kafka-ui1`/`kafka-ui2` directories get renamed to `order-service`/`inventory-service`/`order-ui`/`inventory-ui`. This is a breaking rename for anyone with local checkouts in progress — acceptable now since the project is pre-showcase, would not be acceptable once a client has the repo.
- CLAUDE.md needs a rewrite once the rename lands; it currently documents the article-mirror topology and will be wrong. Tracked as the last task of sprint 1, not done now, so it reflects what actually shipped rather than what was planned.
- No schema registry (Avro/Protobuf) in sprint 1 — staying on JSON keeps the sprint focused; schema registry + Avro is a strong sprint-2 candidate once the domain and hardening are stable, since it's a natural "look how seriously we take contract evolution" upsell for the showcase.
