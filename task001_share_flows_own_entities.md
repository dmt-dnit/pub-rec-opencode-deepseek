# Task001: Share Flows, Own Entities

**Goal:** Refactor `shared-model` to contain only cross-service **contracts** (DTOs and domain events). Move `@Entity` classes to the services that actually persist them. This adheres to: **"Share what flows between services, own what you persist."**

---

## Current State (Before)

```
shared-model/src/.../sharedmodel/
├── Article.java              ← @Entity (should NOT be shared)
├── ArticlePublishedEvent.java ← domain event (should be shared)
├── LoginRequest.java         ← DTO (should be shared)
├── LoginResponse.java        ← DTO (should be shared)
├── RegisterRequest.java      ← DTO (should be shared)

auth-server/src/.../model/
├── UserEntity.java           ← @Entity (owned by auth-server, stays)
```

## Target State (After)

```
shared-model/src/.../sharedmodel/       ← CONTRACTS ONLY
├── ArticlePublishedEvent.java          ← Event that flows between services
├── LoginRequest.java                   ← Wire DTO
├── LoginResponse.java                  ← Wire DTO
├── RegisterRequest.java                ← Wire DTO

auth-server/src/.../model/
├── UserEntity.java                     ← Owned entity (stays)

kafka-demo/src/.../model/               ← NEW: each service owns its entities
├── Article.java                        ← Moved from shared-model
├── ArticleRepository.java              ← NEW: Spring Data repo (if not already exists)

kafka-demo-2/src/.../model/             ← NEW: each service owns its entities
├── Article.java                        ← Copy from shared-model (services may diverge)
├── ArticleRepository.java              ← NEW: Spring Data repo
```

---

## Step-by-Step Plan

### Step 1: Create per-service `model/` packages

**kafka-demo** (`kafka-demo/src/main/java/com/example/kafkademo/model/`):
- Copy `Article.java` from shared-model, update package to `com.example.kafkademo.model`
- Create `ArticleRepository.java` (Spring Data JPA `JpaRepository<Article, Long>`)

**kafka-demo-2** (`kafka-demo-2/src/main/java/com/example/kafkademo2/model/`):
- Copy `Article.java` from shared-model, update package to `com.example.kafkademo2.model`
- Create `ArticleRepository.java`

### Step 2: Remove `Article.java` from shared-model

### Step 3: Update imports in kafka-demo

Files that reference `com.example.sharedmodel.Article`:
- `ArticleController.java`
- `ArticlePublisherService.java`
- `ArticleReceiver.java`
- `TestArticleConsumer.java`
- `ArticlePublisherServiceTest.java`
- `ArticleReceiverIntegrationTest.java`

Update each to import `com.example.kafkademo.model.Article`.

### Step 4: Update imports in kafka-demo-2

Same pattern as Step 3 for kafka-demo-2's files.

### Step 5: `ArticlePublishedEvent` ↔ `Article` decoupling check

Verify that `ArticlePublishedEvent` (in shared-model) does NOT import `Article` directly. If it does, refactor so the event is a standalone DTO (it likely already is — `ArticlePublishedEvent` has `id`, `title`, `author`, `publishedAt` and no JPA annotations).

### Step 6: Remove `jakarta.persistence` from shared-model if no longer needed

- Check if any remaining shared-model class uses `@Entity` annotations
- If none, remove `jakarta.persistence-api` dependency from `shared-model/pom.xml`

### Step 7: Build & verify

```bash
# Order matters: shared-model first, then services
cd shared-model && ./mvnw clean install
cd ../kafka-demo && ./mvnw clean compile
cd ../kafka-demo-2 && ./mvnw clean compile
cd ../auth-server && ./mvnw clean compile

# Run tests
cd ../kafka-demo && ./mvnw test
cd ../kafka-demo-2 && ./mvnw test
```

---

## What Stays in shared-model (Contract Library)

| Class | Rationale |
|-------|-----------|
| `LoginRequest` | Auth flow DTO — consumed by auth-server, produced by UI |
| `LoginResponse` | Auth flow DTO — consumed by UI |
| `RegisterRequest` | Auth flow DTO — consumed by auth-server |
| `ArticlePublishedEvent` | Cross-service event — kafka-demo *emits* → kafka-demo-2 *consumes* |

## What Leaves shared-model

| Class | New Home | Rationale |
|-------|----------|-----------|
| `Article` (entity) | `kafka-demo/src/.../model/` and `kafka-demo-2/src/.../model/` | Each service owns its persistence model; they may diverge |

## What Was Never In shared-model (Correctly)

| Class | Location | Rationale |
|-------|----------|-----------|
| `UserEntity` | `auth-server/src/.../model/` | Auth-server owns users; no other service persists user data |

---

## Why This Architecture

```
                    ┌──────────────────┐
                    │   shared-model   │
                    │ ┌──────────────┐ │
                    │ │ DTOs         │ │ ← wire contracts
                    │ │ Events       │ │ ← service boundary
                    │ └──────────────┘ │
                    └────┬───────┬─────┘
                         │       │ (consumes)
                    ┌────▼──┐ ┌──▼─────┐
                    │kafka- │ │kafka-  │
                    │demo   │ │demo-2  │
                    │ ┌────┐│ │ ┌────┐ │
                    │ │Art ││ │ │Art │ │ ← each owns its entity
                    │ └────┘│ │ └────┘ │
                    └───────┘ └────────┘
```

- **Shared = contract** → safe to evolve independently as long as the wire format is stable
- **Owned = entity** → each service can add columns, change indexes, use different DBs without breaking the other
