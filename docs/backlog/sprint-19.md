# Sprint 19 (Track B Sprint 6) — automate the runtime proof + harden the fallback

**Track:** B — hardening. **Theme:** stop treating "runtime clearing" as a perpetual manual carryover. Make the containerized end-to-end proof (stack comes up healthy → saga runs → reservation feed grows by exactly one) an **automated CI gate**, and bring the bare-podman fallback path up to the same readiness-gating the compose path got in Sprint 18.

## Why this sprint exists
Sprint 18 (B-6) added compose healthchecks + `service_healthy` ordering and the F2 `toHaveCount(+1)` smoke assertion. Codex cleared it **at source** but flagged the obvious gap: **nobody in the loop could run the live proof** — not Codex (no compose provider / dead podman socket), not the coordinator box (podman works but no compose provider, no browser), not a worktree agent. The right fix isn't "someone runs it by hand once" — it's to automate it so every push proves it. That's B-9. B-8 closes the second Sprint-18 residual: the compose healthchecks don't help the plain-`podman run` fallback in `scripts/startup-all.sh`, which still sequences by fixed `sleep`s.

## Dependency currency (cadence step 1, 2026-07-01)
No drift needing action:
- Spring Boot **4.1.0**, Spring Security 7.1.0 (patched), Java **21** (LTS), spring-kafka (Boot-managed) — current. CI actions checkout@v5 / setup-java@v5 / setup-node@v6 / codeql@v4 — current.
- **Angular** latest stable still **22.0.4** (re-checked registry; `22.1.0-next.3` pre-release only) → dev-CVE watch continues, not a task.
- Low-priority notes (not tasks): Confluent images pinned `7.8.0` (Kafka 3.8; newer exists, fine for a demo); Playwright `^1.45.0` in `e2e/package.json` — caret resolves to a current 1.x on `npm ci`, which B-9 will exercise. If B-9's `playwright install` pulls a much newer browser, pin the version explicitly at that point.

## Tasks (2, loosely coupled — parallelizable)

| ID | Title | Brief | Risk | Implementer (rec) |
|----|-------|-------|------|-------------------|
| B-9 | CI-automate the containerized E2E smoke (produces the B-6 runtime proof) | `tasks/sprint-19/B-9-ci-containerized-e2e-smoke.md` | **Med–High** (CI orchestration; only verifiable on a live run) | Claude sonnet worktree agent |
| B-8 | Harden `startup-all.sh` bare-podman fallback (health-polls, not sleeps) | `tasks/sprint-19/B-8-harden-podman-fallback.md` | Low–Med (shell) | Claude sonnet worktree agent |

**Sequencing:** independent — different files (`.github/workflows/ci.yml` + maybe a helper script vs `scripts/startup-all.sh`). Can run as parallel worktrees. **B-9 is the headline** (it's what actually clears B-6's runtime item); prioritize it. If only one lands cleanly, ship B-9.

## Acceptance (sprint-level)
1. **B-9:** a live CI run (on push) builds the stack via `docker compose up -d --build`, waits for all services healthy, starts both Angular UIs, runs the Playwright smoke, and it **passes green — including the F2 `toHaveCount(initialFeedCount + 1)` assertion**. The run URL/logs are the B-6 runtime proof. On failure the Playwright report/trace is uploaded as an artifact.
2. **B-8:** the fallback in `scripts/startup-all.sh` waits on **actual readiness** (broker-api-versions for Kafka, `/actuator/health` for the services, JWKS for auth) with bounded timeouts + clear failure messages, instead of fixed `sleep`s. `shellcheck` clean. Live podman-fallback run verified on a real podman box (coordinator or Dimitri) or clearly marked as the one live-only check.
3. CI overall green; `bash scripts/pre-review-check.sh 19` passes.

## Verification reality (state honestly in the handoff)
- **B-9 is only truly verifiable on a live CI run** — the coordinator can lint the workflow (`actionlint`) and reason about it, but the pass/fail signal is the pushed run. Pushing workflow files needs the `workflow` OAuth scope ([[feedback-ci-maven-sharedlib-inline]]); any new script needs its exec bit committed as `100755` ([[feedback-ci-check-script-exec-bit]]).
- **B-8's live path builds 3 images** — a full run is slow; the coordinator box has working podman and can attempt it, but if it can't complete, `shellcheck` + a dry read + a Dimitri/Codex live run is the honest fallback. Don't assert a live "Pass" that wasn't run.

## Deferred future backlog (unchanged)
- Vercel frontend deploy prep (vercel.json rewrites / env API base URL + CORS on the 3 Spring services).
- Real Google OAuth end-to-end + CI (needs a "no real secrets" policy decision first).

## Loop note
Reviewer auto-pickup on handoff. Reviewer must **clean-build first** ([[feedback-review-build-from-source]]). For B-9 specifically, the review signal is the live CI run, not a local `target/`.
