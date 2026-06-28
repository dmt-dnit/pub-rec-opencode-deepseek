# Sprint 14 — Track B Sprint 1 (hardening)

**Track:** B — hardening to "polished local/CI grade"
**Sequence:** First Track B sprint. Numbered 14 to continue the global sprint counter (Track A = Sprints 1–13); "Track B" identity lives in the review filename (`reviews/sprint-14-track-b-review.md`), same convention Track A used. Keeps `scripts/pre-review-check.sh 14` working unchanged and avoids collision with `docs/backlog/tasks/sprint-1/` (Track A's A-1…A-8).

## Why this sprint exists

Track A landed the Order/Inventory saga and a passing browser smoke gate (closed 2026-06-28, `docs/backlog/track-a-retro.md`). The saga works on the happy path but has no failure handling: a thrown exception in a Kafka listener is retried by Spring's defaults and then silently dropped, and a redelivered event double-applies (Inventory double-decrements stock). There is also no CI — every check to date has been run by hand, which is exactly the gap that let proxy-signal "Pass" claims through in early Track A.

This sprint lands the two highest-leverage items from the Track B backlog (`docs/backlog/sprint-1.md` §Track B):

- **B-5 (CI pipeline)** — gates every future test run. Worth landing first so B-1's new tests (and all later Track B work) execute in CI on every PR instead of only on someone's laptop.
- **B-1 (failure handling: retry, DLQ, idempotency)** — the core hardening work. Without it the saga loses messages on error and corrupts stock on redelivery.

## Task list

| ID  | Title                                              | Brief                                                        | Implementer            |
|-----|----------------------------------------------------|-------------------------------------------------------------|------------------------|
| B-5 | CI pipeline (GitHub Actions)                       | `tasks/sprint-14/B-5-ci-pipeline.md`                        | Claude worktree, sonnet |
| B-1 | Failure handling — retry, DLQ, idempotency         | `tasks/sprint-14/B-1-failure-handling-retry-dlq-idempotency.md` | opencode+DeepSeek (worktree) |

Both are logic/config, not trivial content — neither is coordinator-direct eligible. Split decided with Dimitri (2026-06-28): **B-5 → Claude** (CI yaml is fully verifiable by reading + actionlint; an independent implementer adds little). **B-1 → opencode+DeepSeek** under rule 5's *genuine second-opinion value* trigger — transactional dedup/idempotency is subtle enough that an independent implementation perspective earns its keep, and it keeps that path exercised. Codex remains the independent reviewer for both regardless of implementer.

## Recommended order

1. **B-5 first.** It's independent (only touches `.github/workflows/`) and, once merged, runs B-1's tests automatically. Land it, confirm the workflow is green on a trivial PR, then B-1's PR gets real CI.
2. **B-1 second.** Touches `order-service` and `inventory-service` Java only — no overlap with B-5's files, so the two can run in parallel worktrees if desired; B-5-first is just the cleaner sequence.

## Out of scope this sprint (Track B backlog, later sprints)

- **B-6** (one-command Docker Compose full stack) — largest Track B item; its own sprint.
- **B-2** (observability / correlation IDs), **B-3** (Testcontainers + contract tests), **B-4** (OpenAPI) — later Track B sprints.
- **B-7** (docs refresh) — already complete (CLAUDE.md + README current).

Note: B-1's tests use **EmbeddedKafka** (`spring-kafka-test`), not Testcontainers. B-3 brings Testcontainers later; B-1 must not depend on a Docker daemon so it runs in CI (B-5) with no extra infra.

## Pre-review

`bash scripts/pre-review-check.sh 14` must pass before handing to Codex. Handoff doc: `docs/backlog/sprint-14-handoff.md` (written after implementation, diff-verified).
