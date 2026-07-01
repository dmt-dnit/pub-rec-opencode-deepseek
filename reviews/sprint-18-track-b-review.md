# Sprint 18 Track B Review - Compose Health Gating / F2 Smoke Assertion

Review target: `03c963d`  
Handoff: `docs/backlog/sprint-18-handoff.md`  
Verdict: **No blocking code findings; runtime clearing still pending**

## Findings

No blocking code findings in the Sprint 18 source changes.

The change set is narrow, internally consistent, and matches the handoff scope:

- `docker-compose.yml` now adds healthchecks for Kafka and all three backend services, and gates `order-service` / `inventory-service` on `kafka` and `auth-server` reaching `service_healthy` at [docker-compose.yml](C:/projects/pub-rec-opencode-deepseek/docker-compose.yml:42), [docker-compose.yml](C:/projects/pub-rec-opencode-deepseek/docker-compose.yml:80), and [docker-compose.yml](C:/projects/pub-rec-opencode-deepseek/docker-compose.yml:102).
- The runtime images install `curl`, which the new service healthchecks depend on, at [auth-server/Dockerfile](C:/projects/pub-rec-opencode-deepseek/auth-server/Dockerfile:13), [order-service/Dockerfile](C:/projects/pub-rec-opencode-deepseek/order-service/Dockerfile:13), and [inventory-service/Dockerfile](C:/projects/pub-rec-opencode-deepseek/inventory-service/Dockerfile:13).
- The Playwright smoke now snapshots the reservation-feed baseline and asserts exactly `+1` feed item after the saga at [e2e/smoke.spec.ts](C:/projects/pub-rec-opencode-deepseek/e2e/smoke.spec.ts:90) and [e2e/smoke.spec.ts](C:/projects/pub-rec-opencode-deepseek/e2e/smoke.spec.ts:163), which matches the actual reservation-feed template at [inventory-ui/src/app/pages/dashboard/dashboard.component.ts](C:/projects/pub-rec-opencode-deepseek/inventory-ui/src/app/pages/dashboard/dashboard.component.ts:65).

## Verification Gaps

- **Compose-path runtime verification is still not executed in this environment.** I could inspect the compose file and confirm the healthcheck / `depends_on.condition: service_healthy` structure in source, but I could not run a live `docker compose up` or `podman compose up` here. `podman compose version` failed because no live Podman socket is connected, so the "all services become healthy in the intended order" acceptance check remains unverified at runtime.
- **The F2 browser smoke is still unexecuted here.** The new `toHaveCount(initialFeedCount + 1)` assertion is correctly wired in source, but I did not execute the Playwright saga against a running stack in this session.
- **Fresh dependency/vulnerability state was not re-probed against registries.** Sprint 18 does not change Maven or npm dependency manifests, and I found no local evidence of version drift in the touched files, but I did not run a fresh remote-backed outdated/vulnerability scan in this environment.

## Correctness Notes

- The Kafka healthcheck uses `kafka-broker-api-versions --bootstrap-server localhost:9092`, which is a readiness probe for the broker actually accepting client traffic, not just a process-start check, at [docker-compose.yml](C:/projects/pub-rec-opencode-deepseek/docker-compose.yml:42).
- The coordinator's decision to drop the Zookeeper healthcheck is defensible on correctness grounds. A broken `ruok` probe would deadlock the entire startup chain, while the required readiness gating for the application services still exists through Kafka's own healthcheck and the explicit `service_healthy` dependencies.
- The F2 smoke assertion is dirty-DB-safe because it is baseline-relative rather than hardcoding a count of `1`, and the selector is scoped to the actual reservation feed's `mat-list-item` nodes on the inventory dashboard.

## Residual Risks

- The compose-path hardening only applies when the stack is started through a compose provider. The plain-podman fallback in [scripts/startup-all.sh](C:/projects/pub-rec-opencode-deepseek/scripts/startup-all.sh:15) still sequences via sleeps and a JWKS poll, as the handoff already notes.
- Installing `curl` in each runtime image is a pragmatic fix for healthchecks, but it slightly increases image size and OS-package surface area. I do not consider that a blocker for this sprint.

## Suggested Next Step

Treat Sprint 18 as **code-reviewed but not fully runtime-cleared** until one environment with a working compose provider runs:

1. `docker compose up -d --build` or `podman compose up -d --build`
2. `... ps` to confirm Kafka/auth become healthy before order/inventory
3. the Playwright smoke to confirm the reservation feed count increases by exactly one
