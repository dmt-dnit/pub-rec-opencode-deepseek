# Sprint 2 handoff — Track A stabilization

Snapshot date: 2026-06-20. DeepSeek is still actively working at the time this was written — this is a point-in-time status check, not a final report. Re-verify against the acceptance criteria in `docs/backlog/tasks/sprint-2/F-*.md` before treating anything below as closed.

Source of truth for what each task was supposed to do: `docs/backlog/sprint-2.md` and the individual `docs/backlog/tasks/sprint-2/F-1` through `F-7` briefs. This file is a status check against those, verified by reading the actual code (file:line), not by asking the implementing agent whether it's done.

## Status by task

| Task | Status | Evidence |
|---|---|---|
| F-1 — frontend proxy routing | **Done** | `order-ui/proxy.conf.json` and `inventory-ui/proxy.conf.json` now route `/api/orders/**` and `/api/inventory/**` respectively, replacing the old `/api/articles/**` rule. |
| F-2 — order entity serialization | **Done** | `InventoryReservationListener.java` is now `@Transactional`, maps through `OrderResponse.from(saved)` before the WebSocket push. `OrderController.getAllOrders()` returns `List<OrderResponse>` (also `@Transactional(readOnly = true)`); `placeOrder()` returns `OrderResponse.from(order)`. Both call sites identified in the F-2 brief are fixed, not just the one Codex's test caught. |
| F-3 — inventory-service test wiring | **Done** | `InventoryIntegrationTest.java` now uses two embedded topics (`test-orders-in` / `test-reservations-out`) and a dedicated `testReservationContainerFactory` bean (`@TestConfiguration`, separate `JsonDeserializer<InventoryReservationEvent>`), referenced by `TestReservationConsumer`'s `@KafkaListener(containerFactory = "testReservationContainerFactory")`. This matches the brief's specified fix, not just the surface-level topic split. |
| F-4 — customerEmail ownership | **Done** | `OrderController.OrderRequest` no longer has a `customerEmail` field (`record OrderRequest(List<Item> items)`); `customerEmail` is still derived from the JWT subject. `order-ui`'s dashboard no longer has an editable `customerEmail` input or component field; `order.service.ts:placeOrder()` no longer sends it. The order list still *displays* `order.customerEmail` (the response field) — that's correct per the brief, only the request path changed. |
| F-7 — Java toolchain pinning | **Done** | `maven-enforcer-plugin` with a `requireJavaVersion` rule present in all 4 modules' `pom.xml` (`shared-model`, `auth-server`, `order-service`, `inventory-service`). |
| F-5 — rename cleanup + `CLAUDE.md` rewrite | **Not started** | Confirmed still present: `shared-model/pom.xml:18` description still says "kafka-demo monorepos"; `order-ui`/`inventory-ui`'s `auth.service.ts:10` still use `tokenKey = 'kafka-ui-token'`; `inventory-service/src/main/resources/application.yml:19` still has `group-id: article-demo-group-2`. `CLAUDE.md` still describes the old topology. This is sequenced last per the sprint-2 plan (it documents the true end state, so it can't run until everything else lands) — its absence right now is expected, not a regression. |
| F-6 — Angular dependency remediation | **Not verified** | Not checked in this pass. `package.json` version ranges (`^18.2.0`) are unchanged, which doesn't by itself tell us whether `npm update`/audit work happened — caret ranges don't change even after a successful in-range update. Whoever picks this up next should re-run `npm audit` in both UIs before assuming either outcome. |

## What's left

1. Confirm with DeepSeek directly whether F-6 was attempted — don't infer from `package.json` alone.
2. Once F-6's status is confirmed and F-5 is implemented, re-run the full verification sweep before sending this back to Codex:
   - `./mvnw clean test` in all 4 backend modules
   - `npm run build` in both UIs
   - The repo-wide leftover-reference grep from the F-5 brief
   - A real browser smoke test of order placement end to end (this still hasn't been done by anyone, agent or human, since Sprint 1 — every check so far has been code-level or single-module test-level)
3. Re-review by Codex against the same `reviews/sprint-1-track-a-review.md` blockers, to confirm each one is actually closed rather than just no longer visible to the specific checks already run.

## Known infrastructure issue (separate from task status above)

`./mvnw` has been throwing errors during DeepSeek's runs. This is being actively investigated as a follow-up to this handoff — see the repo's `.mvn/wrapper/maven-wrapper.properties` files (added 2026-06-19) and check back here or in commit history for the resolution.
