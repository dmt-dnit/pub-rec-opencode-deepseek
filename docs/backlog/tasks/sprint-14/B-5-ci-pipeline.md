# B-5 — CI pipeline (GitHub Actions)

**Sprint:** 14 (Track B Sprint 1)
**Priority:** Must — gates every future test run
**Implementer:** Claude worktree agent, `model: sonnet` (job graph + reactor ordering + caching is more than mechanical YAML; fully verifiable by reading + actionlint, so an independent implementer adds little here). Worktree isolation required. Coordinator verifies diff; Codex reviews independently.
**Files to create:** `.github/workflows/ci.yml` (new; there is no `.github/` directory yet). Do not modify source, poms, or package.json.

## Goal

There is no CI today. Add one GitHub Actions workflow that builds and tests every module on each PR and push to `main`, fails fast, and isolates failures so a broken test in one module fails only that module's job.

## Build facts (from CLAUDE.md — confirm against the repo)

- **No root build.** Each module builds independently. Maven build order matters: `shared-model` must be `install`ed to the local `~/.m2` repo before `auth-server`, `order-service`, `inventory-service` will resolve it.
- **Java 21 is required.** Each Maven module's Enforcer plugin pins `[21,22)` and fails fast otherwise. Use `actions/setup-java@v4`, `distribution: temurin`, `java-version: 21`.
- **Per-module Maven wrappers.** Use `./mvnw` from inside each module dir (Linux runner). There is no root `mvnw`.
- **Two Angular UIs**, each its own npm project (no root `package.json`): `order-ui` (4200) and `inventory-ui` (4201). Build with `npm ci && npm run build` in each.

## Required workflow shape

Trigger: `pull_request` and `push` to `main`.

Jobs:

1. **`shared-model`** — `cd shared-model && ./mvnw clean install`. This builds, tests, and installs the contract jar. After it succeeds, persist `~/.m2/repository` so the downstream Maven jobs can resolve `shared-model` without rebuilding it (use `actions/cache` keyed on the relevant `pom.xml` hashes, or `actions/upload-artifact` + download into `~/.m2`). Pick one mechanism and make it deterministic — downstream jobs must reliably see the installed `com/example/sharedmodel` artifact.

2. **`auth-server`, `order-service`, `inventory-service`** — three **separate** jobs (not a single matrix that cancels siblings), each `needs: shared-model`, each restores the `~/.m2` artifact/cache from step 1, then `cd <module> && ./mvnw clean verify`. Separate jobs are what gives the "a broken test fails only that job" property. If you prefer a matrix, set `fail-fast: false` so one module's failure doesn't cancel the others.

3. **`order-ui`, `inventory-ui`** — Angular builds. Independent of the Maven jobs (no `needs`). `actions/setup-node@v4`, `npm ci && npm run build` in each UI dir. Cache npm per UI (`cache: npm`, `cache-dependency-path: <ui>/package-lock.json`). These can be two jobs or a `fail-fast: false` matrix over the two dirs.

"Fail fast" in the backlog means the workflow surfaces failures quickly and a green PR requires every job green — not that one failure cancels all jobs. Independent jobs already give fast, isolated signal.

## Acceptance criteria (observable outcomes)

1. The workflow file is valid: it parses and (if `actionlint` is available to you) passes `actionlint` with no errors. Show the actual lint/parse output. If `actionlint` isn't available in your environment, validate YAML parse (e.g. `python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml'))"`) and **say** actionlint wasn't run.
2. On a clean PR, every job passes (Maven build order respected, Angular builds succeed). The real green run can only be confirmed once the branch is pushed and Actions runs — if you cannot push/observe a live run, state that explicitly and mark this criterion **Codex-only verification** rather than asserting "Pass".
3. A deliberately broken test in any one module fails that module's job and that job only — the other module jobs still run and report independently. Describe how the job topology guarantees this (separate jobs / `fail-fast: false`); a live demonstration is Codex-only if you can't trigger Actions.
4. Java is pinned to 21 (temurin) for every Maven job; Enforcer's `[21,22)` is satisfied. Node version pinned for the Angular jobs.
5. `shared-model` is built/installed before the three downstream Maven jobs, and they resolve it from the persisted `~/.m2` (state which mechanism: cache vs artifact).

## Notes / guidance

- Don't commit any build output (`target/`, `dist/`). The repo has no `.gitignore` and historically commits build artifacts — your PR should contain only `.github/workflows/ci.yml`. Confirm with `git status --short` before handoff.
- Be explicit in the handoff about what's verifiable locally (YAML/actionlint, the job topology argument) vs. what only a live Actions run proves (criteria 2 and 3's live demonstration). Per CLAUDE.md verification standards, a stated "this needs a live run / Codex-only" is the correct answer when you can't trigger Actions — an unverified "Pass" is not.
- This workflow becomes the home for later Track B signals (B-3 Testcontainers, etc.). Keep job names clear so they can be extended.
