# Sprint 31 — Handoff to Codex

**Coordinator:** Claude Code. **Date:** 2026-07-17.
**Task:** cap JVM heap sizes across all backend services, following a real VPS resource-exhaustion incident.
**Implementer:** opencode+DeepSeek, isolated worktree.
**Merge-gate tier:** coordinator-supervised, pushed direct to `main` per the Sprint 21 policy.
Review-Target-Commit: 12038b3

## Why this sprint exists

`dnit-vps` went fully unresponsive today: load average **156.02**, swap **100% full**
(2.0Gi/2.0Gi), SSH and all three `saga-*.dnit.be` endpoints timing out completely. The
immediate trigger was a stuck Pet-Giftshop-side LXD container extraction (`unsquashfs`,
`petgiftshop-cleanup`) that had been thrashing for ~56 minutes without progress —
unrelated to this project, killed by Dimitri directly on the box (`kill -9`), which
recovered the box immediately.

**The trigger isn't why the box had zero headroom to absorb it.** Investigated while
recovering: none of this project's 3 Spring services, nor Kafka/ZooKeeper, have any
JVM heap cap configured — all run unconstrained default heap sizing on a ~961Mi box
shared with several other tenants (Pet Giftshop production+staging, a file-upload API,
a separate `food-manager` project). Dimitri's call: optimize this project's own
footprint first, before considering a bigger VPS tier.

## What was done (`12038b3`)

- `deploy/systemd/pubrec-auth.service` and `pubrec-order.service`: `ExecStart` now
  includes `-Xms64m -Xmx160m`.
- `deploy/systemd/pubrec-inventory.service`: `-Xms64m -Xmx224m` — sized higher than
  the other two, verified (not guessed) reason: `order-service` has zero `@Scheduled`
  tasks (purely reactive Kafka listener + REST), while `inventory-service`'s
  `OutboxRelay.scheduledRelay()` (Sprint 24) polls every ~500ms continuously since the
  service starts — that sustained allocation/GC churn is the actual, verified cause of
  its live-observed 275MB RSS (4x `order-service`'s 69MB) during the incident.
- `deploy/docker-compose/kafka-vps.yml`: added `KAFKA_HEAP_OPTS: "-Xms128m -Xmx256m"`
  to `zookeeper` and `KAFKA_HEAP_OPTS: "-Xms256m -Xmx512m"` to `kafka` — both images
  share the same underlying Confluent launch scripts and the same env var name
  (confirmed via web search against Confluent's own Docker config reference, not
  assumed from `cp-kafka`'s convention alone). Reduces Kafka from its unconfigured
  1GB default (confirmed live via `ps aux` showing `-Xmx1G -Xms1G` on the running
  process) to 512MB — still generous for this demo's 3-topic, low-throughput workload.

## Coordinator verification

- Diff reviewed directly: exactly the 4 files, exactly the intended lines, nothing else
  touched.
- `deploy/docker-compose/kafka-vps.yml` re-validated as parseable YAML after the edit.
- This is a config-only change — no Maven/npm build applies. Confirmed the live
  `saga-*.dnit.be` endpoints are healthy and back to normal response times (~1s, down
  from ~3.6s observed mid-recovery) on integrated `main` after merge — not a build
  check, but the actual real-world health signal that matters here.
- `git status --short` clean on `main` after the cherry-pick.

## Explicitly out of scope, not done, needs Dimitri

**No live changes were made to the VPS as part of this sprint** — these are config
artifacts only. Applying the new heap caps requires:
1. Restarting all three systemd services (`sudo systemctl restart pubrec-auth
   pubrec-order pubrec-inventory`) to pick up the new `ExecStart` lines — systemd
   requires `daemon-reload` first since the unit files themselves changed:
   `sudo systemctl daemon-reload && sudo systemctl restart pubrec-auth pubrec-order
   pubrec-inventory`.
2. Recreating the Kafka/ZooKeeper containers to pick up the new environment variables
   (a plain restart won't re-read the compose file's env changes — needs `docker
   compose up -d` or `podman compose up -d` from wherever `kafka-vps.yml` lives on the
   box, which recreates the containers with the new environment).

Neither of these was attempted — the coordinator has no sudo access on `dnit-vps`
beyond a narrow exact-command allowlist that doesn't cover this (confirmed in earlier
sprints). This is Dimitri's action to take when ready, same split as every other
live-apply step in this Track.

Also explicitly out of scope, unchanged from the brief: no KRaft-mode migration
(collapsing ZooKeeper+Kafka into one process — bigger, deferred optimization), no
change to `inventory-service`'s 500ms polling interval (a deliberate demo-
responsiveness trade-off, not something to change as a side effect), no action on the
Pet Giftshop LXD container (not this project's concern).

## Loop note

Reviewer: config-only change, low complexity to review. The one thing worth an
independent check is the `KAFKA_HEAP_OPTS` convention for `cp-zookeeper` specifically
(cited above as verified via Confluent's own docs, not just assumed identical to
`cp-kafka`) — everything else is a straightforward, verifiable diff against the brief.
