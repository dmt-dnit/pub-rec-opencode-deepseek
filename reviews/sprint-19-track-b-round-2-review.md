# Sprint 19 Track B Round 2 Review - CI E2E Smoke / Bare-Podman Fallback

Review target: `9b8216e`  
Handoff: `docs/backlog/sprint-19-handoff.md` round-2 section  
Verdict: **B-8 cleared; no remaining source-level blockers found**

## Findings

No blocking source-level findings in the round-2 `startup-all.sh` fix.

## Cleared Prior Findings

- **P1 is fixed: auth-server JWKS readiness is now fail-fast.** The old hand-rolled loop is gone. The fallback path now uses the same `wait_for()` helper for auth readiness that it already uses for Zookeeper, Kafka, and the actuator checks at [scripts/startup-all.sh](C:/projects/pub-rec-opencode-deepseek/scripts/startup-all.sh:41), [scripts/startup-all.sh](C:/projects/pub-rec-opencode-deepseek/scripts/startup-all.sh:52), [scripts/startup-all.sh](C:/projects/pub-rec-opencode-deepseek/scripts/startup-all.sh:53), and [scripts/startup-all.sh](C:/projects/pub-rec-opencode-deepseek/scripts/startup-all.sh:89). If `curl -sf http://localhost:9000/oauth2/jwks` never succeeds, the script now exits instead of continuing on to start `order-service` and `inventory-service`.

- **P2 is fixed: engine selection now probes a working runtime instead of CLI presence.** The no-compose fallback now prefers Podman only when `podman info` succeeds, falls back to Docker only when `docker info` succeeds, and exits with an explicit error if neither runtime is actually available at [scripts/startup-all.sh](C:/projects/pub-rec-opencode-deepseek/scripts/startup-all.sh:30), [scripts/startup-all.sh](C:/projects/pub-rec-opencode-deepseek/scripts/startup-all.sh:32), and [scripts/startup-all.sh](C:/projects/pub-rec-opencode-deepseek/scripts/startup-all.sh:35). That resolves the mixed-environment bug from round 1.

## B-8 Assessment

`B-8` is now cleared from a source-review standpoint.

The fallback path is internally consistent after `9b8216e`:

- engine selection is runtime-based rather than binary-presence-based,
- all readiness gates in the fallback path are now fail-fast,
- and the compose-path behavior remains untouched.

## B-9 Note

I still do not see a blocking source-level defect in the `e2e-smoke` workflow job added by `e305061`. The workflow remains structurally coherent by inspection, and nothing in `9b8216e` regresses it.

I did not re-run the live GitHub Actions workflow from this environment. Per your note, the live `e2e-smoke` run is green; I am treating that as already satisfied operational evidence outside this source review.

## Dependency / Operational Notes

- Sprint 19 round 2 does not change dependency manifests, so there is no new dependency-freshness drift in the fix commit.
- The only residual concern is CI/runtime cost: the `e2e-smoke` job remains heavier than the other jobs because it builds the stack, starts both UIs, and installs a Playwright browser on runner. That is expected operational cost, not a defect.
