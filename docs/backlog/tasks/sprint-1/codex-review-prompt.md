# Review prompt: Sprint 1, Track A — Order/Inventory domain pivot

You are reviewing a batch of changes made by an autonomous coding agent (DeepSeek, via opencode) to a Java/Spring Boot + Angular microservices showcase repo for an IT consultancy. **Do not modify any code.** Produce a written review report only — fixes go back through the task pipeline, not through you.

## Context

This repo demos an event-driven (Kafka) microservices pattern across `shared-model` (contracts library), `auth-server` (JWT/JWKS identity), and a pair of services that used to mirror-publish an artificial "Article" event purely to exercise Kafka. Sprint 1, Track A replaced that with a real choreographed saga: a customer places an order (`order-service`), which publishes `OrderPlacedEvent`; `inventory-service` consumes it, attempts to reserve stock, and publishes `InventoryReservationEvent`; `order-service` consumes that and updates the order's status. Two Angular apps (`order-ui`, `inventory-ui`) front the two services.

Read these before reviewing anything else, in this order:
1. `docs/adr/0001-event-driven-showcase-architecture.md` — why this architecture, why this domain, what "production-grade" means for this showcase (explicitly: polished local/CI grade, not cloud-deployable).
2. `docs/backlog/sprint-1.md` — the full Track A/B backlog and how the tasks decompose.
3. `docs/backlog/tasks/sprint-1/A-1-rename-backend-modules.md` through `A-8-auth-server-roles.md` — the actual task briefs given to the implementing agent. **Each one has its own acceptance criteria — that's your primary checklist, not general code-quality intuition.**
4. `docs/security/secrets-and-test-data.md` — the policy on secrets/dummy data this repo must follow.

## Your job

Go through tasks A-1 through A-8 **in order**. For each one:
- Quote or restate its acceptance criteria.
- Mark each criterion `PASS` / `FAIL` / `PARTIAL`, with a file:line reference as evidence — not a vibes-based judgment.
- Note anything the task's brief explicitly marked **out of scope** that got touched anyway (e.g. A-1/A-2 were renames only — any business-logic change smuggled into those diffs is a finding, even if the logic itself looks fine).

Then run a second pass across all of Track A looking specifically for these risks, which exist because of *how* this was implemented, not because of what was asked for:

### 1. Incomplete renames / leftover old-domain references
Multiple agent tasks ran against a shared working tree rather than isolated worktrees, so partial or interleaved edits are a real risk, not a theoretical one.
- `grep -ri "kafkademo\|kafka-demo\b"` across the repo (excluding `docs/`, `.git/`) should return nothing.
- `grep -ri "article"` across `order-service`, `inventory-service`, `order-ui`, `inventory-ui`, `shared-model` should return nothing (class names, topic names, UI copy, variable names — all of it).
- Confirm no module still references the deleted `com.example.sharedmodel.Article` / `ArticlePublishedEvent`.

### 2. The two cross-service contracts that were pinned down explicitly — verify both ends actually agree
These were locked to a specific shape precisely because separate task briefs (read by separate agent runs that can't see each other's work) depend on them. A mismatch here is a silent integration bug, not a compile error.
- `order-service`'s `InventoryReservationListener` must push the **full `Order` entity** (`{orderId, customerEmail, items, totalAmount, status, placedAt, updatedAt}`) over `/topic/messages`. Check `order-ui`'s `websocket.service.ts`/dashboard component parses that exact shape, not something else.
- `inventory-service`'s `OrderEventListener` must push the **`InventoryReservationEvent` itself** (`{orderId, status, reason, processedAt}`) over `/topic/messages`. Check `inventory-ui` parses that exact shape.

### 3. SKU catalog consistency
`order-service` (price catalog, task A-4) and `inventory-service` (stock seed, task A-5) must agree on exactly three SKUs: `SKU-001`/Widget, `SKU-002`/Gadget, `SKU-003`/Gizmo. Check both sides use identical SKU codes — a typo'd SKU in one service and not the other would make every order for that SKU silently fail in a way that's easy to miss in a quick test.

### 4. `shared-model` contract boundary
Confirm zero `@Entity`-annotated classes remain in `shared-model`, and `jakarta.persistence-api` is no longer a dependency in its `pom.xml` (task A-3). This was the whole point of the original `task001_share_flows_own_entities.md` plan — check it's actually done, not just that new records were added alongside the old `Article.java`.

### 5. Test infrastructure didn't get dropped during the rename
`order-service` and `inventory-service` each have a `src/test/resources/application-test.yml` and an `@EmbeddedKafka`-based integration test that disables Spring Security entirely for the test profile (no real JWT, no real `auth-server` needed). Confirm:
- These still exist and reference the new package names (`com.example.orderservice`/`com.example.inventoryservice`).
- `application-test.yml`'s `spring.json.value.default.type` was updated to the new event types (`InventoryReservationEvent` for order-service's test profile, `OrderPlacedEvent` for inventory-service's) — this was a known gap I had to patch into the briefs after the fact, so check it landed.
- `./mvnw test` (or `mvn test` if running against a global Maven install) actually passes in both modules.

### 6. Secrets/dummy-data policy
Per `docs/security/secrets-and-test-data.md`: any new seed data must use the `@example.test` domain (task A-8's three accounts), nothing resembling a real credential should appear anywhere, and no service should require a real external dependency to pass its tests.

## How to verify, not just read

For each backend module (`shared-model`, `auth-server`, `order-service`, `inventory-service`):
```
cd <module> && ./mvnw clean test
```
(The Maven wrapper was broken — CRLF line endings plus a missing `.mvn/wrapper/` directory — and has since been repaired; it should now work standalone. If it still doesn't, that's itself a finding.)

For each frontend (`order-ui`, `inventory-ui`):
```
cd <app> && npm install && npm run build
```

Where feasible, bring up the real flow: `docker compose up` (Kafka, from `kafka-demo`'s — now `order-service`'s — `docker-compose.yml`) plus both backend services, place an order via REST against `order-service`, and confirm the saga completes (order ends up `CONFIRMED` or `REJECTED` per the seeded stock levels) end to end. If that's not practical in your environment, say so explicitly rather than assuming it works.

## Output format

A single report, structured as:
1. **Per-task scorecard** (A-1 through A-8): acceptance criteria with PASS/FAIL/PARTIAL + evidence.
2. **Cross-cutting findings** (the six risk areas above), each tagged **Blocker** / **Should-fix** / **Nit**.
3. **Anything you noticed that isn't covered by the above** — don't suppress a real finding just because it doesn't fit one of these buckets.

Don't pad the report with praise or restate the brief back at me — assume the next reader is doing a sprint retrospective off this report and needs to know what to put back in the backlog, not how the architecture works.
