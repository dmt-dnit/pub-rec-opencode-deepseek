# Sprint 14 (Track B Sprint 1) — Handoff to Codex

**Coordinator:** Claude Code. **Date:** 2026-06-28 (round 1) · 2026-06-29 (round 2).
**Verification:** diff-read + real `mvnw test` output (not self-report).
**Tasks:** B-5 (CI pipeline) · B-1 (failure handling: retry/DLQ/idempotency).

---

## Round 2 (2026-06-29) — addresses Codex rejection `reviews/sprint-14-track-b-review.md`

Round 1 was **rejected**: P1 (blocking) — `mvnw` wrappers tracked non-executable so Actions `./mvnw` fails on Ubuntu; P2 (should-fix) — inventory duplicate handling stock-idempotent but not outcome-idempotent. Both fixed:

| Issue | Fix | Commit | Verified |
|-------|-----|--------|----------|
| **P1** `mvnw` not executable | `git update-index --chmod=+x` on all 4 wrappers → `100755` | `adaf54e` | `git ls-files -s '*mvnw'` all `100755` |
| **P2** outcome-idempotency | `reserve()` → `Optional` (no-op on duplicate, no re-publish/feed); REJECTED path now saves the `ProcessedOrder` marker; listener publishes only `ifPresent`. New `OrderEventListenerOutcomeIdempotencyTest`. | `a6d71b6` | inventory `mvnw test` green on main; `OutcomeIdempotencyTest` 2/2 |

P1 was coordinator-direct (pure git mode bit, Codex supplied the exact command, verifiable via `git ls-files -s`). P2 was implemented by a Claude sonnet worktree agent; brief `docs/backlog/tasks/sprint-14/B-1-round2-outcome-idempotency.md`; I took only the genuine 3-file delta onto main (the agent's worktree had branched from an old base and recreated round-1 files byte-identically) and re-ran the inventory suite on main to verify.

### B-5 live Actions run — now GREEN ✅ (was the round-1 Codex-only item)

After pushing, the first live run failed at the shared-model artifact hand-off: `upload-artifact@v4` reported "No files were found" for the workspace-relative `.m2` path, so downstream jobs couldn't download it. Fixed in `f5d81b4` (coordinator-direct CI config, live-verified): dropped upload/download-artifact; each Maven job now builds `shared-model` inline (`./mvnw -DskipTests install` into `~/.m2`) before its module — the documented local flow, no cross-job transfer. **Re-run: all 6 jobs green** (shared-model gate → auth-server/order-service/inventory-service in parallel → order-ui/inventory-ui). This closes B-5 acceptance criteria 2 (clean push all-green) live.

Two coordinator-direct CI touches this round (P1 chmod, `f5d81b4` inline-build) — both pure CI config in a Codex-flagged area whose authoritative verification is the live run, and the worktree-agent path had twice branched from a stale base. Flagged here for transparency.

**New caveat (non-blocking):** GitHub Actions warns Node 20 is deprecated; `actions/checkout@v4` + `setup-java@v4` are auto-forced onto Node 24 and pass. Revisit when those actions publish Node-24 majors. Added to CLAUDE.md caveats.

**Round-2 outcome-idempotency evidence (real surefire summaries, main, Java 21):**
```
OrderEventListenerOutcomeIdempotencyTest: Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
OrderEventListenerIdempotencyTest:        Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
OrderEventListenerDltTest:                Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
InventoryIntegrationTest:                 Tests run: 1, Failures: 0, Errors: 0, Skipped: 1   (Docker-gated, pre-existing)
```
New test asserts, for both duplicate-success and duplicate-rejection: publisher `times(1)` + `convertAndSend` `times(1)` (one input → one output), plus stock 9/0 and the marker present on the rejected path.

**Still Codex-only:** B-5 live Actions run (needs the branch pushed; round 1 commits were local). P1 directly unblocks that run.

---

## Commits (on `main`)

| SHA | Task | Summary |
|-----|------|---------|
| `5ba89cf` | B-5 | GitHub Actions CI workflow + Sprint 14 briefs (checkpoint) |
| `9a69ce7` | B-1 | retry/DLQ error handling + idempotent Kafka listeners |
| `3f8d64b` | B-1 | tighten order idempotency assertion to `times(1)` |

`git status --short` at handoff: **fully clean** (empty). `pre-review-check.sh 14` passes.

---

## B-5 — CI pipeline · Claude sonnet worktree agent

**File:** `.github/workflows/ci.yml` (only file; new `.github/`).

- 6 jobs: `shared-model` (install) → `auth-server`/`order-service`/`inventory-service` (`needs: shared-model`, parallel, `clean verify`); `order-ui`/`inventory-ui` (independent, `npm ci && npm run build`).
- Java 21 temurin on all Maven jobs (satisfies Enforcer `[21,22)`); Node 22 on UI jobs.
- shared-model shared to downstream via `upload-artifact`/`download-artifact` with a **workspace-local Maven repo** (`-Dmaven.repo.local=${{ github.workspace }}/.m2`). Reason: `upload/download-artifact@v4` resolve paths via `@actions/glob`, which does NOT expand `~` — a workspace-relative path is globbable; an initial `~/.m2/...` version was caught and fixed.
- Failure isolation: 3 separate Maven jobs (not a cancelling matrix) → a broken test fails only that job.

**Verified here:** YAML parses (6 jobs); job topology correct; no source/pom/build-output in the diff.
**Codex-only:** a live Actions run cannot be triggered from the coordinator environment. Acceptance criteria 2 (clean PR all-green) and 3 (broken test fails only its job, live demonstration) need a real run on a PR. `actionlint` was not available locally (only YAML parse run) — please run `actionlint` if available.

---

## B-1 — Failure handling · opencode+DeepSeek (I drove it headless)

Routed to DeepSeek for independent-implementer value on subtle concurrency/idempotency (rule 5). DeepSeek hit two test-harness failures and misdiagnosed one as timing; I intervened twice via `opencode run --continue` with precise root-cause diagnoses (it did **not** loosen assertions — main code held). See "verification notes" below.

**Files (11, both services only — no auth-server/shared-model/UI/pom touched):**
- `*/config/KafkaTopicConfig.java` — `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` + `ExponentialBackOffWithMaxRetries(3)` (init 500ms, ×2, max 4000ms) + DLT `NewTopic` beans.
- `inventory-service/.../model/ProcessedOrder.java` + `repository/ProcessedOrderRepository.java` — dedup store.
- `inventory-service/.../service/ReservationService.java` — `existsById(orderId)` guard at top of the `@Transactional reserve(...)`; records `ProcessedOrder` after decrement (atomic). No double-decrement on redelivery.
- `order-service/.../receiver/InventoryReservationListener.java` — skips re-apply if order status `!= PENDING` (no save, no WebSocket).
- 4 new EmbeddedKafka tests (`spring-kafka-test`, no Docker) + `inventory-service` `application-test.yml`.

**DLT routing:** order-service (listens `inventory-events`) → `inventory-events.DLT`; inventory-service (listens `order-events`) → `order-events.DLT`. Resolver `cr.topic()+".DLT"`.

### Actual test output (real `mvnw test`, Java 21)

order-service:
```
Tests run: 1, ... -- InventoryReservationListenerDltTest
Tests run: 1, ... -- InventoryReservationListenerIdempotencyTest
Tests run: 3, ... -- OrderEventIntegrationTest
Tests run: 1, ... -- OrderEventPublisherTest
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
inventory-service:
```
Tests run: 1, ... Skipped: 1 -- InventoryIntegrationTest   (pre-existing, Docker-gated)
Tests run: 1, ... -- OrderEventListenerDltTest
Tests run: 1, ... -- OrderEventListenerIdempotencyTest
Tests run: 3, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

### Assertion rigor (checked, not gamed)
- Inventory idempotency: asserts `quantityOnHand == 9` (seeded 10, qty 1 twice → once, not 8). Tight.
- Both DLT tests: capture the real record off the `.DLT` topic, assert non-null + matching `orderId`. Not weakened.
- Order idempotency: **was** `verify(...atLeast(1))` (would pass even on a broken guard) → I had it tightened to `verify(messagingTemplate, times(1))`. Now genuinely proves exactly one transition/push. Passes → the PENDING-guard truly limits to one.

---

## Acceptance criteria status

| B-1 criterion | Status |
|---|---|
| Forced exception → record on `.DLT` after retries exhaust (both services) | ✅ tests pass, real assertion |
| Same `OrderPlacedEvent` twice → stock decremented once | ✅ `quantityOnHand==9` |
| Same `RESERVED` event twice → order transitions once | ✅ `times(1)` |
| `./mvnw test` passes with no manually-started broker (EmbeddedKafka) | ✅ both modules BUILD SUCCESS |
| 3 retries before DLT | ✅ `ExponentialBackOffWithMaxRetries(3)` |

| B-5 criterion | Status |
|---|---|
| Valid workflow, shared-model before downstream, Java 21, fail isolation | ✅ verified (parse + topology) |
| Clean PR all-green / broken test fails only its job | ⏳ **Codex-only** (needs live Actions run) |

---

## Caveats for this sprint (not standing)
- EmbeddedKafka DLT tests are slow (order DLT ~25–68s, inventory DLT ~25s) — broker startup, not a hang. Adds ~2–3 min to the Maven jobs in CI. Acceptable; note if CI time becomes a concern.
- `InventoryIntegrationTest` is `@Skipped` (pre-existing, requires Docker) — not introduced by B-1; B-3 (Testcontainers) will address per-test broker isolation.
- B-5's live green run is Codex-only (see above).

## Standing caveats: unchanged (see CLAUDE.md table). `npm audit --omit=dev` not re-run — no UI deps touched this sprint.
