# Sprint 16 (Track B Sprint 3) — finish the Boot-4 migration + API docs + vuln visibility

**Track:** B — hardening. **Theme:** "finish the migration" (Dimitri's call, 2026-06-29): clear the Sprint 15 compatibility shims and close two carried-over hardening gaps.

## Dependency currency (cadence step 1, 2026-06-29)
- Spring Boot **4.0.7** → 4.1.0 available; 4.0.x supported to Dec 31 2026 (no urgency). Fold the 4.1.0 bump into SB-3.
- Angular 22, Java 21 — current. GitHub Actions on v5/v5/v6.
- No EOL pressure.

## Tasks (3, loosely coupled — run SB-3 first; it restructures the poms)

| ID | Title | Brief | Implementer (rec) |
|----|-------|-------|-------------------|
| SB-3 | Boot-4 modernization cleanup (drop shims, real Jackson 3, → 4.1.0) | `tasks/sprint-16/SB-3-boot4-cleanup-jackson3.md` | opencode+DeepSeek (continuity + serialization second-opinion) or Claude opus |
| B-4 | OpenAPI / Swagger UI on the 3 services | `tasks/sprint-16/B-4-openapi.md` | Claude sonnet agent (additive, read-verifiable) |
| SEC-1 | Server-side vulnerability scanning in CI | `tasks/sprint-16/SEC-1-server-vuln-scanning.md` | Claude sonnet agent (CI/config) |

**Sequencing:** SB-3 restructures starters/Jackson deps in the poms; B-4 adds a springdoc dep and SEC-1 adds a CI step — both build cleanly on the settled SB-3 poms. Do SB-3 first (or in a separate worktree merged first), then B-4 / SEC-1.

## Why these three
- **SB-3** finishes what Sprint 15 deliberately deferred: Sprint 15 landed Boot 4.0.7 via the `spring-boot-starter-classic` bridge + `spring.jackson.use-jackson2-defaults:true` (declared shims). Removing them gets us to a clean, modular, Jackson-3 stack. **Highest-risk task** — touches Kafka event serialization (the saga). The Sprint 14/15 DLT + idempotency + integration tests exercise the event round-trip and are the safety net; the containerized Playwright smoke (Codex) is the end-to-end backstop.
- **B-4** (OpenAPI) — Track B backlog item; self-contained, additive, reviewer/demo-visible.
- **SEC-1** — Sprint 14 follow-up 3 (Codex flagged: no server-side CVE visibility). Adds an OWASP Dependency-Check (or equivalent) gate to CI + Dependabot.

## Deferred to Sprint 17+
Inventory exactly-once/outbox hardening (Sprint 14 follow-up 2), B-2 (observability), B-3 (Testcontainers + contract tests), B-6 (Docker Compose, its own sprint).

## Acceptance (sprint-level)
1. No `spring-boot-starter-classic` / `-test-classic`, no `use-jackson2-defaults`, no Jackson-2 `jackson-datatype-jsr310` left; all 4 modules on Boot 4.1.0; `mvnw verify` green each (real output).
2. Kafka saga + JWT flow unchanged; all Sprint 14/15 hardening tests pass under Jackson 3; containerized Playwright smoke still green (Codex).
3. `/swagger-ui.html` (or configured path) on auth-server, order-service, inventory-service lists real endpoints with DTO schemas.
4. CI runs a server-side vuln scan with a defined fail/pass policy; Dependabot enabled.
5. CI green; `pre-review-check.sh 16` passes.
