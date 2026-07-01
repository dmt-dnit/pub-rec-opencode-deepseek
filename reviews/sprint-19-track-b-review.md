# Sprint 19 Track B Review - CI E2E Smoke / Bare-Podman Fallback

Review target: `e305061`, `8d2bbd8`  
Handoff: `docs/backlog/sprint-19-handoff.md`  
Verdict: **REJECT / not cleared**

## Findings

### P1 - The "hardened" bare-podman fallback still does not fail if auth-server never becomes ready

Sprint 19 claims the fallback path replaced fixed sleeps with bounded health polls, but auth-server is still handled by the old non-failing loop:

- [scripts/startup-all.sh](C:/projects/pub-rec-opencode-deepseek/scripts/startup-all.sh:83)
- [scripts/startup-all.sh](C:/projects/pub-rec-opencode-deepseek/scripts/startup-all.sh:84)
- [scripts/startup-all.sh](C:/projects/pub-rec-opencode-deepseek/scripts/startup-all.sh:85)
- [scripts/startup-all.sh](C:/projects/pub-rec-opencode-deepseek/scripts/startup-all.sh:86)
- [scripts/startup-all.sh](C:/projects/pub-rec-opencode-deepseek/scripts/startup-all.sh:87)

If `/oauth2/jwks` never comes up, the loop simply exhausts 30 attempts and the script continues on to build and start `order-service` and `inventory-service` anyway. There is no success check after the loop and no `exit 1`, unlike the new `wait_for()` helper at [scripts/startup-all.sh](C:/projects/pub-rec-opencode-deepseek/scripts/startup-all.sh:35).

Impact: the fallback path can still launch downstream services against an unavailable auth server, which directly contradicts the handoff's "replace fixed sleeps with bounded health-polls" claim and leaves the startup sequence operationally flaky in exactly the path this sprint was meant to harden.

### P2 - The so-called plain-podman fallback still prefers Docker whenever the `docker` CLI is installed, even if Podman is the working runtime

The fallback branch is described in the handoff as a hardened "plain-podman fallback", but engine selection still does this:

- [scripts/startup-all.sh](C:/projects/pub-rec-opencode-deepseek/scripts/startup-all.sh:28)
- [scripts/startup-all.sh](C:/projects/pub-rec-opencode-deepseek/scripts/startup-all.sh:30)
- [scripts/startup-all.sh](C:/projects/pub-rec-opencode-deepseek/scripts/startup-all.sh:31)

After both compose-provider checks fail, the script sets `ENGINE=podman` and then immediately flips to `docker` purely if the `docker` binary exists. That means a workstation with a Docker CLI installed but no usable Docker daemon, and a working Podman runtime, will take the wrong engine path and fail before any of the new readiness probes matter.

Impact: this is an operational mismatch against the sprint goal. The fallback is not reliably "plain-podman" and can still break in mixed-tool environments, which is precisely the setup this script claims to support.

## Notes on B-9

I did not find a blocking source-level defect in the new `e2e-smoke` workflow job. The structure is coherent:

- it brings the stack up with `docker compose up -d --build`,
- waits on service health rather than fixed sleeps at [.github/workflows/ci.yml](C:/projects/pub-rec-opencode-deepseek/.github/workflows/ci.yml:240),
- starts both UIs and waits for ports `4200` / `4201`,
- runs Playwright,
- and captures backend/UI/test artifacts on failure.

The remaining proof for B-9 is the live GitHub Actions run, not a local code defect in the YAML I inspected.

## Dependency / Vulnerability Notes

- Sprint 19 does not change Maven or npm manifests, so I did not find new dependency-freshness drift in the touched files.
- The new workflow does add a heavier CI path (`npm ci` twice plus Playwright browser install on every run), so expect noticeably longer CI runtime. That is an operational cost, not a correctness bug.

## Suggested Next Step

Before treating Sprint 19 as cleared:

1. Convert the auth-server JWKS wait in `startup-all.sh` to the same fail-fast `wait_for()` pattern used for Zookeeper, Kafka, and the actuator checks.
2. Make the fallback choose Podman explicitly in the no-compose branch, rather than switching to Docker just because the CLI exists.
3. Then re-check the live `e2e-smoke` job result in GitHub Actions.
