# Task A-1: Rename and re-package the backend Kafka modules

## Context
This repo is a showcase for an IT consultancy. Two Spring Boot modules currently demo Kafka pub/sub using an artificial "article" domain where each service mirror-publishes the other's events. We are repurposing them into a realistic Order → Inventory choreographed saga (see `docs/adr/0001-event-driven-showcase-architecture.md` if you want the full rationale — not required reading to do this task). This task is a pure rename/re-package — **do not change any business logic, payload shape, or behavior**. Later tasks (A-3, A-4, A-5) will rewrite the actual domain logic on top of this renamed structure.

## Current state
- `kafka-demo/` — Maven module, artifactId `kafka-demo`, root package `com.example.kafkademo`. Will become the **Order Service**.
- `kafka-demo-2/` — Maven module, artifactId `kafka-demo-2`, root package `com.example.kafkademo2`. Will become the **Inventory Service**.
- Both depend on `shared-model` (artifactId `com.example:shared-model`) — leave that dependency declaration as-is.

## Task

### 1. `kafka-demo` → `order-service`
- Rename the directory `kafka-demo/` to `order-service/`.
- In `order-service/pom.xml`: change `<artifactId>` from `kafka-demo` to `order-service`, `<name>` to `order-service`, `<description>` to something like "Order service — publishes order events, consumes inventory outcomes".
- Move/rename all Java source under `com.example.kafkademo` to `com.example.orderservice` (directory move + package declaration + all imports referencing the old package within this module).
- Rename `KafkaDemoApplication.java` → `OrderServiceApplication.java` (class name to match).
- In `order-service/src/main/resources/application.yml`: change `spring.application.name` from `kafka-demo` to `order-service`. Leave `server.port: 8080` and all Kafka/security config values unchanged.
- Update `logging.level.com.example.kafkademo` → `logging.level.com.example.orderservice`.
- Update test sources under `order-service/src/test/java/com/example/kafkademo/...` to the new package similarly.

### 2. `kafka-demo-2` → `inventory-service`
- Same pattern: directory → `inventory-service/`, artifactId → `inventory-service`, package `com.example.kafkademo2` → `com.example.inventoryservice`, `KafkaDemo2Application.java` → `InventoryServiceApplication.java`, `spring.application.name` → `inventory-service`, logging package updated. `server.port: 8081` stays.

## Out of scope
- Do not touch `shared-model`, `auth-server`, `kafka-ui1`, `kafka-ui2` in this task.
- Do not change topic names (`app.kafka.topic` / `app.kafka.listen-topic` values), Kafka listener logic, security config, or any DTO/event field — that's deliberately deferred to later tasks so this rename can be reviewed in isolation.
- Do not change `docker-compose.yml` (kafka-demo's compose file) — leave it where it is and as-is for this task.

## Acceptance criteria
- `grep -rE "kafkademo2?\b" order-service inventory-service` returns no matches (besides this task brief / docs, which aren't part of the grep scope).
- `cd order-service && ./mvnw clean compile` succeeds.
- `cd inventory-service && ./mvnw clean compile` succeeds.
- `cd order-service && ./mvnw test` and `cd inventory-service && ./mvnw test` still pass (the existing article-domain tests should still pass unmodified except for their package/import paths — don't change their assertions).
- No references anywhere in the repo to `com.example.kafkademo` or `com.example.kafkademo2` remain outside of `docs/` (git history aside).
