# Sprint 25 — Handoff to Codex

**Coordinator:** Claude Code. **Date:** 2026-07-15.
**Task:** package/groupId rename `com.example` → `be.dnit` across all 4 Maven modules. Mechanical but wide — full review cadence warranted (see brief's "Loop note").
**Implementer:** opencode+DeepSeek, standing default, isolated worktree. Required a redispatch — first attempt hit a git `index.lock` collision (see below) and produced nothing; second attempt succeeded cleanly.
**Merge-gate tier:** coordinator-supervised, pushed direct to `main` per the Sprint 21 policy.
Review-Target-Commit: 0b28ec5

## Why this sprint exists
Dimitri's call, ahead of resuming Track C Phase 4 (Vercel frontend deploy): replace the placeholder `com.example` package/groupId with the project's real reverse-domain namespace (`be.dnit`, matching the `dnit.be` domain already used throughout Track C) before more code lands on top of the placeholder.

## First attempt failed cleanly — no residual risk
The implementer's first attempt ran the same "mkdir + `git mv` loop" sequence twice without confirming the first had finished, causing a genuine `index.lock` collision between two concurrent git operations (the headless agent's own sandboxing correctly refused to clear the lock file itself, since it lives outside the worktree's own directory). **Nothing was ever committed from that attempt** — confirmed by the coordinator (`git status --short` on the worktree showed clean, `git log` showed no new commit; the only artifact left behind was harmless empty scaffold directories from `mkdir -p`, which the coordinator removed before redispatching). The retry included explicit instructions not to repeat the double-run pattern and to checkpoint the physical moves before running the build — it succeeded in a single pass.

## What was done (`0b28ec5`, single commit)
- 65 Java files moved via `git mv` (`git log` shows genuine renames, not delete+add pairs).
- Package declarations + every `import com.example...` → `be.dnit...` across all main and test sources in `shared-model`, `auth-server`, `order-service`, `inventory-service`.
- `<groupId>com.example</groupId>` → `<groupId>be.dnit</groupId>` in all 4 `pom.xml`, including the inter-module dependency declarations (`order-service`/`inventory-service` depend on `shared-model` by groupId+artifactId).
- `application.yml`/`application-test.yml`: `spring.json.trusted.packages`, `spring.json.value.default.type`, logging-level category keys.
- `.github/workflows/ci.yml`: 3 comment lines referencing `com.example:shared-model` as a Maven coordinate, updated for accuracy.
- **Historical docs deliberately untouched**: `docs/backlog/`, `reviews/`, `task001_share_flows_own_entities.md` — confirmed via `git diff --stat` showing zero changes under those paths.

## A false alarm worth documenting, in case a reviewer hits the same confusion
`git diff --stat` shows the two services' `WebSocketConfig.java` files as **cross-renamed** in the diff display:
```
rename {order-service/.../com/example/orderservice => inventory-service/.../be/dnit/inventoryservice}/config/WebSocketConfig.java
rename {inventory-service/.../com/example/inventoryservice => order-service/.../be/dnit/orderservice}/config/WebSocketConfig.java
```
This looks alarming at a glance (as if the two services' configs got swapped) but is a **git rename-detection display artifact**, not a real defect — the two files are genuinely ~95% textually identical (both are minimal `WebSocketConfig` classes differing only in package), so git's content-similarity heuristic cross-paired them arbitrarily for display purposes. **Coordinator read both files' actual final content directly**: `order-service/src/main/java/be/dnit/orderservice/config/WebSocketConfig.java` has `package be.dnit.orderservice.config;` and is correctly located; `inventory-service`'s equivalent likewise. Both are correctly scoped to their own service — confirmed, not assumed.

## Coordinator verification — full independent rebuild from integrated `main`, not just the worktree
- `shared-model`: `./mvnw clean install` — BUILD SUCCESS.
- `auth-server`: `./mvnw clean verify` — BUILD SUCCESS, no tests (pre-existing, this module has none).
- `order-service`: `./mvnw clean verify` — **BUILD SUCCESS, Tests run: 9, Failures: 0, Errors: 0, Skipped: 2** (matches implementer's report exactly).
- `inventory-service`: `./mvnw clean verify` — **BUILD SUCCESS, Tests run: 9, Failures: 0, Errors: 0, Skipped: 1** (matches implementer's report exactly).
- The Kafka-heavy integration tests passing (`OrderEventIntegrationTest`, `OutboxIntegrationTest`, `OutboxRelayScheduledInvocationTest`, etc.) is itself proof that `spring.json.trusted.packages`/`spring.json.value.default.type` were updated correctly — a wrong string there fails at deserialization/test time, not compile time.
- The `KafkaStorageException`/LogManager-shutdown warnings during teardown are the pre-existing standing caveat (already documented, exit 0, unrelated to this rename).
- `git status --short` clean on `main` after the cherry-pick.
- `grep -rl "com\.example" --include="*.java" --include="*.yml" --include="*.yaml" --include="*.xml" --include="*.sh" .` (excluding `target/`, `docs/backlog/`, `reviews/`, `task001_*.md`) returns empty — independently re-run by the coordinator, not just trusted from the implementer's report.

## Loop note
Reviewer: this is wide but genuinely mechanical — the WebSocketConfig cross-rename note above is worth a quick independent look given how it presents in `git diff --stat`, but the actual file content is correct. The real risk class here (silent runtime failure from a missed string, not a compile error) is why this got the full review cadence despite being "just a rename."
