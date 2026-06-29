# SEC-1 — Server-side vulnerability scanning in CI

**Sprint:** 16 (Track B Sprint 3)
**Priority:** Should — Sprint 14 follow-up 3 (Codex flagged: no server-side CVE visibility; CI runs build/test only)
**Implementer:** Claude sonnet worktree agent (CI/config, read-verifiable). Branch from current `main`; verify base with `git diff --name-only`.
**Scope:** `.github/workflows/` (CI) + a Dependabot config + per-module Maven plugin config as needed. No source changes.

## What to do

Give the Java backend the same vulnerability visibility the UIs have (`npm audit --omit=dev`). Two parts:

### 1. Maven CVE scan in CI
Add a server-side dependency vulnerability scan — **OWASP Dependency-Check Maven plugin** (`org.owasp:dependency-check-maven`) is the default choice. Add a CI job (or step) that runs it across the Maven modules and **fails on a defined severity threshold** (recommend: fail on CVSS ≥ 7 / High+). Make the policy explicit and documented.
- Be mindful of runtime: Dependency-Check downloads the NVD database (slow/flaky). Use the NVD API key mechanism if available, and/or cache the DB; if it can't be made reliable in CI, run it as a separate non-blocking job first and document the limitation rather than making CI flaky.
- State the exact threshold and whether the job is blocking or report-only in the first iteration.

### 2. Dependabot
Add `.github/dependabot.yml` enabling version + security updates for the Maven modules (and optionally the two npm UIs + GitHub Actions). Note that enabling Dependabot **alerts** themselves is a repo setting (Codex found alerts disabled) — call that out as a repo-admin action for Dimitri if it can't be set from code.

## Acceptance criteria (observable)

1. CI runs a server-side vuln scan; the workflow change is valid YAML and the scan step/job is present with a stated fail/pass threshold. Show the workflow diff.
2. `.github/dependabot.yml` exists and is valid, covering the Maven modules.
3. The scan runs in a live Actions run (Codex-only / post-push) — state the live result is pending push, and report whatever you can run locally (e.g. `./mvnw org.owasp:dependency-check-maven:check` on one module, or note it's too slow/needs NVD key and is Codex/CI-verified).
4. Existing CI jobs still pass; the new scan doesn't break the build green unless it finds an above-threshold CVE (which would be a real finding to surface, not suppress).

## Notes
- The point is *visibility with a policy*, not zero findings. If it surfaces real CVEs, report them — don't suppress to make CI green.
- Keep it isolated from the build/test jobs so a scan hiccup doesn't mask real test failures (separate job, like the existing per-module isolation).
- Live CI behavior is the authoritative check (Codex/post-push); local NVD runs are slow — be explicit about what was vs wasn't run.
