# B-9 — CI-automate the containerized E2E smoke (the B-6 runtime proof)

**Sprint:** 19 (Track B Sprint 6)
**Priority:** Must — this is the sprint headline; it clears B-6's open runtime item permanently.
**Implementer:** Claude sonnet worktree agent. Branch from current `main`; coordinator applies the diff onto main + verifies. **The real pass/fail signal is a live CI run** — you cannot fully verify this locally.
**Scope:** `.github/workflows/ci.yml` (add one new job); optionally a small helper script under `scripts/` (if so, commit it `chmod +x` / mode `100755`). Do **not** change service main code, the compose file, or the smoke test — B-9 only *runs* what B-6 built.

## Goal
Add a CI job that stands up the full containerized backend stack, starts both Angular UIs, and runs the existing Playwright smoke (`e2e/smoke.spec.ts`) against it — so every push proves, automatically, that the stack comes up healthy and the reservation feed grows by exactly one (the F2 / B-6 runtime proof).

## Context (read before editing)
- `.github/workflows/ci.yml` already has jobs: `shared-model`, `auth-server`, `order-service`, `inventory-service`, `order-ui`, `inventory-ui`, `snyk-security`. Match their style (checkout@v5, setup-java@v5, setup-node@v6, `cache`).
- **The compose build is self-contained.** Each service `Dockerfile` first `COPY shared-model/ … && mvn install` then builds the service, so `docker compose up -d --build` needs no external shared-model step. `docker-compose.yml` (repo root) defines zookeeper, kafka, auth-server (9000), order-service (8080), inventory-service (8081) with healthchecks + `service_healthy` gating (Sprint 18).
- **ubuntu-latest runners have Docker + the compose plugin.** Use `docker compose` (not podman) in CI — it's the supported provider there.
- The UIs run via `npm start` (`ng serve`) with `proxy.conf.json` proxying `/api/**`, `/oauth2/**`, `/ws/**` to the published container ports (4200→8080/9000, 4201→8081/9000). This matches the deliberate "UIs stay on npm start" decision — do **not** containerize them.
- `e2e/` is its own npm project: `package.json` (`@playwright/test ^1.45.0`, script `"test": "playwright test"`), `playwright.config.ts` (headless chromium, `baseURL: http://localhost:4200`, `timeout: 60_000`, `retries: 0`, screenshot/video on failure), `smoke.spec.ts` (hits `http://localhost:4200` and `http://localhost:4201`, logs in as seeded `customer1@example.test` / `warehouse1@example.test`, places one SKU-001 order, asserts the saga + the F2 `toHaveCount(initialFeedCount + 1)`).

## What to do — add one job, e.g. `e2e-smoke`
Ordered steps (adapt as needed; the outcome matters, not the exact shape):
1. `actions/checkout@v5`, `setup-node@v6` (node 22). Java not needed on the host — images build inside Docker.
2. **Bring up the backend:** `docker compose up -d --build`. Then **wait for healthy** — poll `docker compose ps` (or `docker inspect` health status) until auth-server, order-service, inventory-service report `healthy`, with a bounded timeout (the compose healthchecks have `start_period` up to 60s; allow generously, e.g. up to ~180s). Fail the job with the compose logs (`docker compose logs`) if they don't come up.
3. **Start both UIs:** `npm ci` in `order-ui` and `inventory-ui`, then `npm start` for each in the background (`&`), and **wait until `http://localhost:4200` and `http://localhost:4201` respond** (poll with curl, bounded timeout — `ng serve`'s first compile can take 30–60s). Capture their logs to files for artifact upload.
4. **Run the smoke:** in `e2e/`, `npm ci`, `npx playwright install --with-deps chromium`, then `npx playwright test`. The seeded accounts exist because auth-server's `DataSeeder` runs on startup (H2, fresh each run) — a clean DB, which the baseline-relative assertion handles.
5. **Artifacts on failure:** upload the Playwright HTML report / traces and the UI + `docker compose logs` (`actions/upload-artifact@v4`, `if: failure()` or `if: always()`).
6. **Teardown:** `docker compose down -v` in an `if: always()` step (nice-to-have; runners are ephemeral anyway).
7. Decide the trigger: run on `push` to main and on `pull_request` like the other jobs. It's a heavy job — that's acceptable; do **not** put it in another job's `needs` (a smoke hiccup shouldn't mask unit failures), mirroring how `snyk-security` is isolated.

## Acceptance criteria (observable)
1. **A live CI run on push is green and the `e2e-smoke` job passes**, with the Playwright output showing the smoke test passed — including the F2 assertion (`toHaveCount(initialFeedCount + 1)`). **Paste the run URL + the relevant job log excerpt.** This is the B-6 runtime proof; without a green live run this task is not done.
2. The job waits for **actual health** (compose `healthy` state) before starting UIs, and waits for the UIs to respond before running Playwright — no fixed-`sleep`-only sequencing. Show the wait logic.
3. On a forced failure (you can demonstrate locally by pointing the smoke at a down stack, or just reason it through), the Playwright report + logs upload as artifacts. Describe this.
4. `actionlint` (or equivalent YAML/workflow lint) is clean. State that you ran it.
5. Existing CI jobs are unaffected (the new job is additive, not in any `needs`).

## Notes / guardrails
- **Verification honesty:** you almost certainly cannot run GitHub Actions locally. Lint the workflow and reason about it, but **the acceptance signal is the pushed live run** — say clearly in your report that CI is the verifier, and (if you lack push access / `workflow` scope) that the coordinator must push it. Do not claim the job passes without a real run.
- **Pushing workflow files needs the `workflow` OAuth scope** — flag if your push is rejected for that reason so the coordinator handles it.
- If you add any helper script under `scripts/`, commit it executable (`git update-index --chmod=+x`, mode `100755`) — a non-exec script that CI `bash`-invokes has bitten this repo before.
- Do not weaken `e2e/smoke.spec.ts` or the compose healthchecks to make CI pass — if the stack doesn't come up, fix the *orchestration/waits*, not the assertions.
- Keep the job self-contained; rely on the compose build for shared-model (don't add a Maven step on the host).
