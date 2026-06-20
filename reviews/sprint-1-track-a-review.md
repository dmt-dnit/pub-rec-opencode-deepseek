# Sprint 1 Track A Review Handoff

Date: 2026-06-20
Reviewer: Codex
Repo reviewed: `C:\projects\pub-rec-opencode-deepseek`
Scope: `docs/backlog/tasks/sprint-1/A-1` through `A-8`

## Verdict

Reject for now.

Track A is not ready to treat as complete. The main blockers are:

1. Both Angular UIs still proxy only `/api/articles/**`, while the rewritten clients call `/api/orders` and `/api/inventory`, so the browser flow behind `npm start` will not reach the new APIs.
2. `order-service` throws a real runtime error when sending updated orders over WebSocket because it serializes a JPA entity with a lazy `items` collection outside an active session.
3. `inventory-service` still fails its required test suite on Java 21 because the embedded-Kafka test wiring is wrong after the domain rewrite.
4. The UI dependency tree is behind current releases and `npm audit` reports 50 vulnerabilities, including 30 high.

## Highest-Priority Findings

### Blocker 1: stale frontend proxy config breaks the renamed APIs

- `order-ui` still proxies only `/api/articles/**` to port 8080, but the app now calls `/api/orders`.
- `inventory-ui` still proxies only `/api/articles/**` to port 8081, but the app now calls `/api/inventory`.

Evidence:

- `order-ui/proxy.conf.json:17`
- `order-ui/src/app/services/order.service.ts:8`
- `inventory-ui/proxy.conf.json:17`
- `inventory-ui/src/app/services/inventory.service.ts:8`

Impact:

- `npm start` may serve successfully, but real browser requests for the new APIs will miss the intended backend routes.
- A-6 and A-7 acceptance are not actually met end to end.

### Blocker 2: `order-service` WebSocket payload fails at runtime

- `InventoryReservationListener` saves the updated `Order` and sends that entity directly over STOMP.
- `Order.items` is an `@ElementCollection`, and serialization trips a `LazyInitializationException` outside the Hibernate session.

Evidence:

- `order-service/src/main/java/com/example/orderservice/model/Order.java:19`
- `order-service/src/main/java/com/example/orderservice/receiver/InventoryReservationListener.java:41`
- `order-service` `mvn -q test` under Java 21 reproduces the failure in `OrderEventIntegrationTest`

Impact:

- The DB row can update while the live UI update fails.
- The required “PENDING -> CONFIRMED/REJECTED without reload” path is not reliable.

### Blocker 3: `inventory-service` test infrastructure is still broken

- The test profile publishes and consumes on the same topic.
- `TestReservationConsumer` listens for `InventoryReservationEvent`, but the shared topic also carries `OrderPlacedEvent`.
- Under Java 21 the module still fails `mvn -q test`.

Evidence:

- `inventory-service/src/test/resources/application-test.yml:33`
- `inventory-service/src/test/java/com/example/inventoryservice/InventoryIntegrationTest.java:31`
- `inventory-service/src/test/java/com/example/inventoryservice/TestReservationConsumer.java:21`

Observed failure:

- `Cannot convert from [com.example.sharedmodel.OrderPlacedEvent] to [com.example.sharedmodel.InventoryReservationEvent]`
- test assertion failure: expected reservation event but got `null`

Impact:

- A-5 acceptance is not met.
- The “test infra survived the rename” check fails.

### Blocker 4: Angular dependency tree has unresolved vulnerabilities

Representative run from `order-ui`:

- `npm.cmd audit --json`
- Result: 50 vulnerabilities total
- Breakdown: 30 high, 13 moderate, 7 low

Representative outdated check from `order-ui`:

- Angular runtime packages installed at `18.2.14`
- Angular CLI/build tooling installed at `18.2.21`
- Registry reports newer current majors, and advisories already affect the installed versions

Examples reported by audit:

- multiple Angular `@angular/common`, `@angular/core`, `@angular/compiler` advisories
- vulnerable transitive tooling via `vite`, `webpack-dev-server`, `rollup`, `tar`, `picomatch`, `piscina`

Impact:

- The “dependencies up to date and have no vulnerabilities” review requirement is not met.
- This should be treated as explicit backlog work, not a follow-up nice-to-have.

## Other Important Findings

### Should-fix 1: request-body `customerEmail` is ignored

- The task/API contract says `POST /api/orders` accepts `customerEmail` in the request.
- `OrderController` ignores that value and overwrites it with the JWT subject.
- The Order UI still exposes an editable customer-email field, so frontend and backend semantics now diverge.

Evidence:

- `order-service/src/main/java/com/example/orderservice/controller/OrderController.java:45`
- `order-ui/src/app/pages/dashboard/dashboard.component.ts:38`

### Should-fix 2: rename cleanup is incomplete

Old-domain references still remain outside `docs/`, including:

- `inventory-service/src/main/resources/application.yml:19` uses `article-demo-group-2`
- `shared-model/pom.xml:18` still says `Shared model classes for kafka-demo monorepos`
- `CLAUDE.md` still documents the old `kafka-demo` / `article` topology throughout

Impact:

- Leaves the repo in a half-renamed state.
- Makes next-sprint agent context less trustworthy.

### Should-fix 3: both UIs still use the old auth localStorage token key

Evidence:

- `order-ui/src/app/services/auth.service.ts:10`
- `inventory-ui/src/app/services/auth.service.ts:10`

Impact:

- Not a blocker by itself, but it is another leftover rename/domain artifact.

### Nit: CommonJS websocket libraries cause optimization bailouts

Angular build warnings showed:

- `@stomp/stompjs` is not ESM
- `sockjs-client` is not ESM

Impact:

- Not a sprint blocker for demo scale.
- Worth tracking if frontend optimization/perf polish matters in the next pass.

## Per-Task Status Summary

### A-1 Rename backend modules

Status: `PARTIAL`

What passed:

- renamed module directories exist as `order-service` and `inventory-service`
- Java packages/artifact IDs/application names were updated

What failed or regressed:

- acceptance required tests to still pass; `inventory-service` no longer passes its test suite
- leftover rename artifacts remain in runtime/config text

### A-2 Rename frontend apps

Status: `PARTIAL`

What passed:

- renamed directories/package names/project names/titles landed
- both apps build successfully

What failed or regressed:

- dev proxy routing still points only at old article endpoints, so renamed runtime flow is broken

### A-3 Redesign `shared-model`

Status: `PASS`

Confirmed:

- `Article.java` removed
- `ArticlePublishedEvent.java` removed
- `OrderPlacedEvent`, `OrderItem`, `InventoryReservationEvent` added as records
- no `@Entity` remains in `shared-model`
- `jakarta.persistence-api` removed from `shared-model/pom.xml`

### A-4 Order Service domain rewrite

Status: `PARTIAL`

What passed:

- local `Order` entity/repository exist
- price catalog matches expected SKUs/prices
- `POST /api/orders` and `GET /api/orders` exist
- listener consumes `InventoryReservationEvent`

What failed or regressed:

- WebSocket update path fails at runtime due to lazy collection serialization
- backend ignores request-body `customerEmail`

### A-5 Inventory Service domain rewrite

Status: `FAIL`

What passed:

- `Product` entity/repository exist
- seeded catalog matches required SKUs and stock levels
- reservation logic and `GET /api/inventory` exist

What failed:

- module test suite still fails on Java 21
- test topic wiring/consumer type assumptions are incorrect

### A-6 Order UI

Status: `FAIL`

What passed:

- form/list/websocket/dashboard rewrites are present
- app builds successfully

What failed:

- runtime proxy path does not match the new API
- live status update path is not reliable because backend WebSocket payloading fails

### A-7 Inventory UI

Status: `FAIL`

What passed:

- stock table and reservation feed implementation exist
- app builds successfully

What failed:

- runtime proxy path does not match the new API

### A-8 Auth server roles

Status: `PARTIAL`

What passed:

- enum now uses `CUSTOMER`, `WAREHOUSE_STAFF`, `ADMIN`
- seeded accounts use `@example.test`
- register flow defaults new users to `CUSTOMER`
- `auth-server` tests pass

What remains unverified:

- no end-to-end admin/customer authorization test coverage is present for the acceptance cases

## Contract Checks

### Full `Order` over `/topic/messages`

Expected:

- `order-service` should push full `Order` payload
- `order-ui` should parse full `Order` payload

Result:

- shape agreement is present in code
- runtime delivery is still broken by the lazy-loading serialization failure

Relevant files:

- `order-service/src/main/java/com/example/orderservice/receiver/InventoryReservationListener.java:41`
- `order-ui/src/app/services/websocket.service.ts:21`

### `InventoryReservationEvent` over `/topic/messages`

Expected:

- `inventory-service` should push `InventoryReservationEvent`
- `inventory-ui` should parse that exact shape

Result:

- shape agreement is present in code

Relevant files:

- `inventory-service/src/main/java/com/example/inventoryservice/receiver/OrderEventListener.java:37`
- `inventory-ui/src/app/services/websocket.service.ts:21`

## SKU Consistency Check

Passed.

Shared SKU set is consistent between price catalog and inventory seeding:

- `SKU-001` / `Widget`
- `SKU-002` / `Gadget`
- `SKU-003` / `Gizmo`

Evidence:

- `order-service/src/main/java/com/example/orderservice/service/PriceCatalog.java:12`
- `inventory-service/src/main/java/com/example/inventoryservice/config/InventoryDataSeeder.java:21`

## Secrets / Dummy Data Check

Mostly passed.

Confirmed:

- auth seed users use `@example.test`
- OAuth placeholders remain placeholders
- no obvious real credentials were introduced

Relevant files:

- `auth-server/src/main/java/com/example/authserver/DataSeeder.java:24`
- `auth-server/src/main/resources/application.yml:26`

Minor note:

- `inventory-service` integration test uses `customer@example.test`, which is still safely fictitious, though the backlog had standardized on `customer1@example.test`

## Verification Runs

### Backend

Runs performed:

- `shared-model`: `mvn -q install` -> passed
- `auth-server`: `mvn -q test` -> passed
- `order-service`: `mvn -q test` with Java 21 -> fails on lazy-loading websocket serialization
- `inventory-service`: `mvn -q test` with Java 21 -> fails on test-topic/type mismatch

Environment note:

- sandboxed Maven could not read the active JDK security file
- I reran Maven outside the sandbox
- host default Java 25 also exposed separate Mockito/Byte Buddy incompatibility
- rerunning with Java 21 was necessary to isolate real project failures from host-tooling noise

### Frontend

Runs performed:

- `order-ui`: local Angular build passed via `.\node_modules\.bin\ng.cmd build`
- `inventory-ui`: local Angular build passed via `.\node_modules\.bin\ng.cmd build`

Environment note:

- global `npm` shim invoked as `npm` was broken in this shell
- `npm.cmd` worked

### Dependency audit

Representative run:

- `order-ui`: `npm.cmd audit --json`
- `order-ui`: `npm.cmd outdated --json`

Reasoning:

- both UIs use the same Angular stack and very similar dependency trees
- one audited tree was enough to establish that dependency hygiene is not currently acceptable

## Recommended Next-Sprint Task Breakdown

1. Fix frontend dev/proxy routing for `order-ui` and `inventory-ui`, then smoke-test real browser requests.
2. Fix `order-service` WebSocket payload serialization so the live status path works without Hibernate session leakage.
3. Repair `inventory-service` embedded-Kafka test wiring so `mvn test` passes on Java 21.
4. Clean leftover rename/domain artifacts (`CLAUDE.md`, descriptions, old group IDs, stale naming constants).
5. Decide and implement the intended `customerEmail` ownership rule: request-body value vs JWT subject.
6. Create a dependency remediation task for both Angular apps, including vulnerability reduction and version policy.
7. Add explicit Java runtime/toolchain pinning so agents do not silently run this repo on Java 25 and get misleading failures.

## Minimal Claude Prompt Seed

Use this file as the review baseline. Prioritize fixes in this order:

1. unblock real runtime flows (`proxy.conf.json`, `order-service` WebSocket serialization)
2. restore green backend test suites on Java 21
3. remove leftover rename/domain contamination
4. address Angular dependency vulnerabilities and upgrade plan

Do not treat the previous sprint as complete until the blocker section above is resolved and the verification commands are green on Java 21.
