# B-2 — Observability: correlation IDs, structured logs, actuator

**Sprint:** 17 (Track B Sprint 4)
**Priority:** Should — Track B backlog
**Implementer:** Claude sonnet worktree agent (additive, read-verifiable). Apply the agent's *diff*, verify post-integration state (recent lesson). Branch from current `main`.
**Scope:** `auth-server`, `order-service`, `inventory-service` (+ their `application.yml`). No UI changes required for acceptance, but the correlation ID should reach the WebSocket payload the UIs already consume.

## Goal
Make the choreographed saga traceable end-to-end. One order's full lifecycle — `POST /api/orders` → `order-events` → inventory reserve → `inventory-events` → order status update → WebSocket push — must be greppable by a single correlation ID across both services' logs, in JSON, with actuator health/metrics exposed.

## What to do

### 1. Correlation ID — generate, propagate, log
- **Generate** a correlation ID (UUID) at the REST entry point `POST /api/orders` (order-service controller). If the request carries an `X-Correlation-Id` header, reuse it; else generate one. Put it in the SLF4J **MDC** (`correlationId`) for the request thread.
- **Propagate** it as a **Kafka header** (`X-Correlation-Id`) when publishing `OrderPlacedEvent` to `order-events` (`OrderEventPublisher` uses `KafkaTemplate` — send via a `ProducerRecord`/`Message` with the header, or a `RecordInterceptor`/`ProducerInterceptor`).
- **inventory-service** `OrderEventListener`: read the `X-Correlation-Id` header from the incoming record (`@Header` or `ConsumerRecord` headers), set it in MDC for the listener thread, and **re-attach** it as a header when publishing `InventoryReservationEvent` to `inventory-events` (`InventoryEventPublisher`).
- **order-service** `InventoryReservationListener`: read the header back, set MDC, and include the correlation ID in the WebSocket payload pushed to `/topic/messages` (so the UI could display it).
- Every log line on all three services must carry `correlationId` (via MDC + the structured log format below). Clear the MDC after each request/record (`MDC.clear()` in a finally / interceptor) so it doesn't leak across threads.
- Consider a small servlet `OncePerRequestFilter` for the REST side and a Kafka `RecordInterceptor` for the consumer side to keep MDC handling out of business code.

### 2. Structured (JSON) logging — use Spring Boot's native feature (no extra dependency)
Spring Boot 4.1 has built-in structured logging. In each service's `application.yml`:
```yaml
logging:
  structured:
    format:
      console: ecs   # Elastic Common Schema JSON (or "logstash")
```
Ensure `correlationId` (MDC) appears in the JSON output (Boot includes MDC by default; verify it shows). Do not add logstash-logback-encoder — Boot's native structured logging covers it.

### 3. Actuator on all three
- order-service + inventory-service already have `spring-boot-starter-actuator`. **auth-server does not — add it.**
- Expose health + metrics on all three: `management.endpoints.web.exposure.include: health,metrics,info`. Keep them open for the demo (or document if secured). Confirm the actuator paths aren't blocked by `SecurityConfig` (permit `/actuator/**` or at least `/actuator/health`).

## Acceptance criteria (observable)
1. Placing an order produces logs on **both** order-service and inventory-service that share the same `correlationId`; grepping that ID shows the full lifecycle (order placed → reserved/rejected → status updated) in causal order. Show a sample (the implementer can run the stack or simulate via a test; if the full stack can't run here, state it and provide a unit/slice test that asserts the header is propagated through publish→consume).
2. Backend logs are valid JSON lines (ECS/logstash) including `correlationId`.
3. `/actuator/health` and `/actuator/metrics` return 200 on all three services (auth-server now has actuator). State which checks are Codex-only (live HTTP) vs verified here.
4. `./mvnw verify` green in all three; existing Sprint 14/16 tests unaffected.

## Notes
- Keep the saga behavior identical — this is additive instrumentation.
- A Kafka `ProducerInterceptor`/`ConsumerInterceptor` or `RecordInterceptor` is the clean way to attach/read the correlation header without touching every publish/listen call — preferred over inlining header code in each publisher/listener.
- Micrometer Observation/Tracing could auto-propagate, but a manual `X-Correlation-Id` header + MDC is simpler and explicit for the demo — use the manual approach unless you have reason otherwise (state it).
