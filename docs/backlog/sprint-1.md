# Sprint 1 backlog

Implements ADR-0001 (`docs/adr/0001-event-driven-showcase-architecture.md`). Two tracks: A reshapes the existing demo into the Order/Inventory domain, B hardens it to the agreed "polished local/CI grade" bar. **Track A must land and be reviewed before Track B starts** — hardening logic written against the old Article domain is wasted work.

Each task below is sized to be a single, self-contained brief for the implementation agent. When you turn these into actual DeepSeek prompts: state the acceptance criteria verbatim, and supply the dummy/fictitious data called out in A-4/A-6 rather than letting the agent invent its own (keeps data consistent across tasks written by different prompts).

---

## Track A — Domain reshape (Order → Inventory saga)

### A-1: Rename and re-package the backend modules
- Rename directories: `kafka-demo` → `order-service`, `kafka-demo-2` → `inventory-service`.
- Rename Java packages: `com.example.kafkademo` → `com.example.orderservice`, `com.example.kafkademo2` → `com.example.inventoryservice`.
- Update `artifactId`, `name`, `description` in each `pom.xml`; update `spring.application.name` in each `application.yml`.
- **Acceptance:** both modules compile (`./mvnw clean compile`) under the new package/artifact names; no leftover `kafkademo`/`kafkademo2` references (`grep -r kafkademo` returns nothing outside docs/ADR history).

### A-2: Rename the frontend apps
- Rename directories: `kafka-ui1` → `order-ui`, `kafka-ui2` → `inventory-ui`.
- Update `package.json` `name`, `angular.json` project name, `index.html` `<title>`.
- **Acceptance:** `npm install && npm start` works from each renamed directory at its existing port (4200 / 4201).

### A-3: Redesign `shared-model` contracts
- Remove `Article.java` and `ArticlePublishedEvent.java` entirely.
- Add `OrderPlacedEvent` (record): `orderId` (String, UUID), `customerEmail` (String), `items` (List of `{sku, quantity}`), `totalAmount` (BigDecimal), `placedAt` (Instant).
- Add `InventoryReservationEvent` (record): `orderId` (String), `status` (enum `RESERVED` / `REJECTED`), `reason` (String, nullable — populated on `REJECTED`), `processedAt` (Instant).
- Remove the `jakarta.persistence-api` dependency from `shared-model/pom.xml` if nothing left in the module uses it.
- **Acceptance:** `shared-model` has zero `@Entity` classes; `./mvnw clean install` succeeds; `LoginRequest`/`LoginResponse`/`RegisterRequest` are untouched.

### A-4: Order Service — own the `Order` entity, expose placement API, publish/consume events
- New local `model/Order.java` (`@Entity`, JPA, H2): `id`, `customerEmail`, `items` (embedded or separate table), `totalAmount`, `status` (`PENDING`, `CONFIRMED`, `REJECTED`), `placedAt`, `updatedAt`. Plus `OrderRepository`.
- `POST /api/orders` — accepts `{customerEmail, items[]}`, computes total from a hardcoded/seeded price list (see dummy data below), persists `Order` with status `PENDING`, publishes `OrderPlacedEvent` to topic `order-events`, returns the created order.
- `@KafkaListener` on `inventory-events`: on receipt of `InventoryReservationEvent`, look up the `Order` by `orderId`, set status to `CONFIRMED` or `REJECTED`, save, and push the update over the existing WebSocket channel (`/topic/messages`) so the UI updates live.
- **Dummy data:** seed a fixed in-memory price list — `SKU-001 Widget €9.99`, `SKU-002 Gadget €24.50`, `SKU-003 Gizmo €4.25`. No real product or customer names.
- **Acceptance:** placing an order via REST results in an `OrderPlacedEvent` on `order-events` (verify via a test consumer or `kafka-console-consumer`); order status transitions correctly once a matching `InventoryReservationEvent` is consumed.

### A-5: Inventory Service — own `Product`/`Stock`, consume orders, decide reservation, publish outcome
- New local `model/Product.java` (`@Entity`, H2): `sku`, `name`, `quantityOnHand`. `ProductRepository`.
- Seed the same SKUs as A-4's price list with starting stock quantities (e.g. `SKU-001: 50`, `SKU-002: 5`, `SKU-003: 0`) so that some orders succeed and some realistically get rejected (insufficient stock) — this matters for demoing the rejection path, don't seed all SKUs with plentiful stock.
- `@KafkaListener` on `order-events`: for each line item, check `quantityOnHand`; if all items satisfiable, decrement and publish `InventoryReservationEvent{status: RESERVED}`; otherwise publish `InventoryReservationEvent{status: REJECTED, reason: "Insufficient stock for SKU-00X"}` to `inventory-events`, with no partial decrement on rejection.
- **Acceptance:** an order for `SKU-001` quantity 1 reserves successfully and decrements stock; an order for `SKU-003` (zero stock) is rejected and stock is unchanged; both produce a visible `InventoryReservationEvent`.

### A-6: Order UI — order placement + live status
- Replace the article-publish form with an order placement form (customer email, line items from the SKU list above) calling `POST /api/orders`.
- Replace the article feed with a live order list (initial REST fetch + WebSocket updates) showing status transitions (`PENDING` → `CONFIRMED`/`REJECTED`).
- **Acceptance:** placing an order in the browser shows it as `PENDING`, then flips to `CONFIRMED` or `REJECTED` within a couple of seconds without a page reload.

### A-7: Inventory UI — stock dashboard + live reservation feed
- Replace the article feed with a stock table (SKU, name, quantity on hand) and a live feed of incoming `InventoryReservationEvent`s.
- **Acceptance:** stock quantity shown updates after an order reserves against it; reservation feed shows new entries live via WebSocket.

### A-8: auth-server — realistic roles
- Update `DataSeeder` to seed users with roles `CUSTOMER`, `WAREHOUSE_STAFF`, `ADMIN` (replacing generic `USER`) and fictitious seed accounts (e.g. `customer1@example.test`, `warehouse1@example.test`) — use the `.test` TLD or clearly fake domain so seed data can never be mistaken for a real address.
- **Acceptance:** existing login/JWT/role-claim flow is unaffected; `AdminController`'s `hasRole('ADMIN')` check still passes for the seeded admin.

---

## Track B — Hardening to "polished local/CI grade"

Start only after Track A is implemented and reviewed.

### B-1: Failure handling — retry, DLQ, idempotency
- Configure `DefaultErrorHandler` with exponential backoff on both services' Kafka listeners; route exhausted retries to a dead-letter topic (`order-events.DLT`, `inventory-events.DLT`) via `DeadLetterPublishingRecoverer`.
- Make both listeners idempotent: dedupe on `orderId` (Inventory Service should not double-decrement stock if `order-events` redelivers; Order Service should not reprocess a status update it already applied).
- **Acceptance:** a test that forces a processing exception confirms the message lands on the DLT after retries exhaust, not lost silently; a test that redelivers the same event confirms no double-processing.

### B-2: Observability — correlation IDs, structured logs, actuator
- Generate a correlation ID at the REST entry point (`POST /api/orders`), propagate it as a Kafka header through `order-events` → `inventory-events`, and include it in every log line and the WebSocket payload.
- Structured (JSON) logging on all three backend services.
- Confirm `/actuator/health` and `/actuator/metrics` are exposed and reachable on all three services (actuator dependency already present on the two Kafka services; add it to `auth-server` if missing).
- **Acceptance:** grepping logs across both services for one correlation ID shows the full order lifecycle in causal order.

### B-3: Testing — Testcontainers + contract tests
- Replace/augment existing Kafka tests with Testcontainers-backed integration tests (real Kafka in a container) for both the producer and consumer side of each service.
- Add a contract test asserting `OrderPlacedEvent` and `InventoryReservationEvent` JSON (de)serialize identically on both sides of the wire (catches accidental field drift between services consuming the shared contract).
- **Acceptance:** `./mvnw test` runs the Testcontainers suite without a manually-started broker; CI (B-5) runs it successfully.

### B-4: API contracts — OpenAPI
- Add `springdoc-openapi` to `auth-server`, `order-service`, `inventory-service`; expose Swagger UI for each.
- **Acceptance:** `/swagger-ui.html` on each service lists its actual endpoints with request/response schemas matching the real DTOs.

### B-5: CI pipeline
- GitHub Actions workflow: build+test `shared-model` first, then the three Maven modules in parallel, then `npm ci && npm run build` for both Angular apps. Fail fast on any module.
- **Acceptance:** workflow passes on a clean PR; a deliberately broken test in any module fails the corresponding job only.

### B-6: One-command local environment
- Extend the existing `kafka-demo/docker-compose.yml` (move it to repo root) to add `auth-server`, `order-service`, `inventory-service` as containers (multi-stage Dockerfiles per service), alongside Zookeeper/Kafka. Angular apps stay on `npm start` for local dev (dev-server proxy is the intended workflow), but document the container-based path as the "just run this to see it work" option.
- **Acceptance:** `docker compose up` from repo root brings up a fully working backend stack reachable from `npm start`'d UIs with no manual port/env wiring.

### B-7: Documentation refresh
- Rewrite `CLAUDE.md` to describe the shipped Order/Inventory architecture (not the old Article-mirror one).
- Add an architecture diagram (Mermaid, in the root README — create one if it doesn't exist) showing the saga flow: Order UI → Order Service → `order-events` → Inventory Service → `inventory-events` → Order Service → Order UI.
- **Acceptance:** a new reader of the README understands the saga flow and the role of each of the 5 runnable components without reading code.

---

## Explicitly deferred to sprint 2+ backlog (do not start in sprint 1)
- Notification Service as a third hop (linear 3-step saga) — see ADR-0001 §2.
- Schema Registry + Avro/Protobuf contracts — see ADR-0001 §4.
- Kubernetes/Helm/IaC and multi-environment deployment — explicitly out of scope per ADR-0001 §3.
