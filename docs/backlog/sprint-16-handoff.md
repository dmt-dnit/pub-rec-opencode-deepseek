# Sprint 16 (Track B Sprint 3) — Handoff to Codex

**Coordinator:** Claude Code. **Date:** 2026-06-29.
**Theme:** finish the Boot-4 migration + API docs + server-side vuln visibility.
**Tasks:** SB-3 (Boot-4 cleanup), B-4 (OpenAPI), SEC-1 (vuln scanning). All verified by reading diffs + **re-running `mvnw verify` myself**.

## Round 2 (2026-06-29) — addresses Codex rejection `reviews/sprint-16-track-b-review.md`

Codex caught a real coordinator integration error + a Swagger gap. Both fixed in `2852d35`:

- **P1 — SB-3 not actually on the 3 services.** auth/order/inventory had reverted to **4.0.7 + classic bridge + Jackson-2** while only shared-model was 4.1.0. Cause: when integrating B-4 I ran `git checkout <B-4-branch> -- <service poms>`, but B-4's agent worktree branched from a **stale base (pre-SB-3)**, so its whole-file poms carried the old 4.0.7+shim content + springdoc — overwriting SB-3's pom changes. My "combined verify" passed because 4.0.7+shims+springdoc still builds; I checked build-green but **not the pom version after the file-take.** Fix: restored the 3 service poms to the SB-3 state (`a943818`: 4.1.0, `spring-boot-kafka`, `webmvc-test`, no classic, no jackson2 jsr310) and re-layered springdoc 3.0.3. (Lesson banked: apply a stale-base agent's *diff*, not its whole file; verify post-integration *state*, not just build-green.)
- **P2 — Swagger `/v3/api-docs` 401** (auth-server, which ends `anyRequest().authenticated()`). The permit only had `/v3/api-docs/**`, which doesn't match the exact path. Added `/v3/api-docs` + `/v3/api-docs.yaml` to the permit in all 3 `SecurityConfig`s.
- **OWASP job** was `in_progress` at review time and `NVD_API_KEY` was set mid-review — re-run now completes; still `continue-on-error` (report-only) by design.

**Round-2 coordinator verification (my own `mvnw verify`, Java 21):** auth `BUILD SUCCESS`; order `Tests run: 6, Failures: 0, Errors: 0`; inventory `Tests run: 5, Failures: 0, Errors: 0, Skipped: 1`. State re-checked explicitly: 3 services on **4.1.0**, `starter-classic`/`jackson-datatype-jsr310` = 0, springdoc present, SB-3 Jackson-3 yml serde intact, exact `/v3/api-docs` permit in all 3.

**Codex re-verify:** containerized smoke (already passed r1) + now `/v3/api-docs` returns 200 on auth-server + the OWASP job completing with the key. Dependabot alerts now enabled (you confirmed HTTP 204).

## Commits (on `main`)

| SHA | Task | Summary |
|-----|------|---------|
| `28e5d92` | SB-3 | bump parent 4.0.7 → 4.1.0 |
| `f2b1e46` | SB-3 | remove `spring.jackson.use-jackson2-defaults` |
| `9e0d929` | SB-3 | Kafka serde → `JacksonJsonSerializer/Deserializer` (Jackson 3) |
| `a943818` | SB-3 | test serde beans → Jackson 3 classes |
| `b2ceffc` | B-4 | springdoc 3.0.3 + Swagger UI on all 3 services |
| `b100338` | SEC-1 | OWASP Dependency-Check CI job + Dependabot |

`git status --short`: clean. Implementers: SB-3 = opencode+DeepSeek (3 coordinator `--continue` interventions); B-4 + SEC-1 = Claude sonnet agents. All three agent worktrees branched from a stale base — I took only the intended files onto main and re-verified.

## SB-3 — Boot-4 modernization (the hard one)

Removed the Sprint 15 compatibility shims and adopted a clean modular / Jackson-3 stack on **Spring Boot 4.1.0**:
- No `spring-boot-starter-classic` / `-test-classic`, no `use-jackson2-defaults`, no Jackson-2 `jackson-datatype-jsr310` (all grep to 0).
- Modular starters adopted (`spring-boot-kafka`, `spring-boot-starter-webmvc-test`, etc.).
- Kafka serde switched from the **deprecated** Jackson-2 `JsonSerializer/JsonDeserializer` to **`JacksonJsonSerializer/JacksonJsonDeserializer`** (Jackson 3, `tools.jackson`, handles `Instant` natively) — in both `application.yml` and the test `@TestConfiguration` serde beans.
- Three real Boot-4 breakages were worked through with coordinator diagnosis before each `--continue`: missing modular auto-config starters → deprecated Kafka serde → Jackson-3 `Instant` handling. No test was weakened; the saga event round-trip (`Instant` fields) is proven by the passing DLT/idempotency/outcome tests.

## B-4 — OpenAPI

`springdoc-openapi-starter-webmvc-ui` **3.0.3** (latest 3.0.x, the Boot-4 line) on all three services; `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**` permitted in each `SecurityConfig` (before the `/api/**` authenticated rule). `/api/**` stays authenticated.

## SEC-1 — vuln scanning

New **separate** `owasp-dependency-check` CI job (no `needs` dependents): runs the OWASP plugin goal (CVSS≥7 threshold, `continue-on-error` report-only first pass, NVD DB cached, reports uploaded as artifact). `.github/dependabot.yml` covers 4 Maven modules + 2 npm UIs + github-actions. Existing 6 jobs + `checkout@v5`/`setup-java@v5`/`setup-node@v6` unchanged.

## Dependency-currency + security note (this sprint)

- **Spring Boot 4.1.0** (from 4.0.7); **Angular 22 / Java 21** current.
- **Spring Security 7.1.0** — pulled by the Boot 4.1.0 BOM (verified `help:evaluate spring-security.version` = 7.1.0). Assessed against the 2026.04 Spring Security CVE batch (Dimitri flagged): the critical **CVE-2026-22752 (9.6)** is Spring **Authorization Server + Dynamic Client Registration** — **not used** (auth-server does custom JWT issuance). The 8.1 auth-bypass CVEs (**CVE-2026-22753/22754**, servlet-path matching) are fixed in the 2026.04 line (7.1.0 includes them) **and** the advisory states default embedded-Tomcat root-path deployments are not affected — which is our model. **Conclusion: on a current, patched Spring Security; the batch CVEs do not apply to our usage.**
- New deps pinned to current latest-compatible: springdoc **3.0.3**, OWASP plugin resolves latest (**12.2.2**).

## Independent verification (my own `mvnw verify`, Java 21, Boot 4.1.0)

```
shared-model       install ok
auth-server        BUILD SUCCESS
order-service      BUILD SUCCESS  — Tests run: 6, Failures: 0, Errors: 0
inventory-service  BUILD SUCCESS  — Tests run: 5, Failures: 0, Errors: 0, Skipped: 1 (Docker)
```
(Run after SB-3, and again after B-4+SEC-1 were taken onto main.) `dependency:tree` on a Jackson-clean SB-3 build showed no `com.fasterxml.jackson` except `jackson-annotations`.

## Codex-only / not verified here

- **Live CI run** (needs push) — confirms the new `owasp-dependency-check` job runs and the v5/v6 actions + Boot-4 build stay green. The OWASP job's NVD download needs an `NVD_API_KEY` repo secret to be reliable (admin action, noted in `ci.yml`); it's `continue-on-error` for now.
- **Live Swagger** — `/v3/api-docs` + `/swagger-ui.html` on each running service (no full stack here).
- **Containerized Playwright smoke** — the end-to-end saga + JWT flow on Boot 4.1.0 / Jackson 3 / Security 7.1.0. This is the key backstop for SB-3's serialization change; please re-run.
- **Admin actions** (Dimitri, repo settings): add `NVD_API_KEY` secret; enable Dependabot alerts.
- Note: springdoc pulls its own Jackson for doc rendering — if `dependency:tree` shows `com.fasterxml` from springdoc, it's isolated to the OpenAPI path; the Kafka saga path is confirmed Jackson 3.

## Pre-review
`bash scripts/pre-review-check.sh 16` — passes (clean tree, this handoff present).
