# Sprint 7 → Codex: execute & verify K-2 (browser smoke test)

Sprint 7 is down to **one open task, K-2** — the real end-to-end browser smoke test. K-1 (Angular 21→22 dependency floor) and K-3 (stale "Angular 18" text) were already closed this session (commit `ab37adc`); see `docs/backlog/sprint-7-handoff.md` for their evidence. K-2 is handed directly to you rather than the opencode executor because it needs a live browser click-through, not a code change.

## The task

Perform and document the actual user flow against the now-Angular-22 UIs:

1. **order-ui** (`http://localhost:4200`): log in (seeded `CUSTOMER` account) → confirm dashboard loads → place an order for an in-stock SKU → watch status go `PENDING → CONFIRMED` live (WebSocket, no reload).
2. **inventory-ui** (`http://localhost:4201`): log in (seeded `WAREHOUSE_STAFF`) → confirm stock table loads → confirm the live reservation feed shows your order's event and the SKU quantity decremented.
3. Check the browser console in both for errors during the flow.
4. Document the specific account, SKU, and outcome — not "it works." Any failed step is a reportable finding.

Full acceptance criteria: `docs/backlog/tasks/sprint-7/K-2-actual-browser-smoke-test.md`.

## Deploy scripts (how to stand the stack up)

Backend — one command, containerized (Kafka + auth-server + order/inventory). **Verified healthy this session: all 5 containers up, endpoints returned 200.** Requires a working podman/docker:

```bash
bash scripts/startup-all.sh        # builds + starts the backend stack
podman ps                          # confirm zookeeper, kafka, auth-server, order-service, inventory-service are Up
```

Frontends — always on the host, require Node ≥ 22.22.3 / 24 (Node 24.17.0 is installed):

```bash
cd order-ui && npm start           # http://localhost:4200
cd inventory-ui && npm start       # http://localhost:4201
```

Health checks if you want to confirm the backend before opening a browser:
`curl http://localhost:9000/oauth2/jwks` · `curl http://localhost:8080/actuator/health` · `curl http://localhost:8081/actuator/health` (all returned 200 this session).

## Notes

- **No real creds needed.** Use the seeded accounts (`auth-server`'s `DataSeeder`); Google OAuth is placeholder-only and not on this path.
- If the backend won't come up, the service builds themselves are known-good this session — suspect the container runtime, not the Dockerfiles.
- Known non-blocker: `npm audit` reports 4 highs, all the `piscina` advisory (`GHSA-x9g3-xrwr-cwfg`) transitive in build tooling — not runtime, no clean fix at the current floor. Don't treat that as a K-2 failure.

## Verdict back to claude

Write your accept/reject for K-2 to `reviews/sprint-7-track-a-review.md`. If reject, the blockers become Sprint 8's backlog.
