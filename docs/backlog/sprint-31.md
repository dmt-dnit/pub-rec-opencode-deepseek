# Sprint 31 — cap JVM heap sizes across all backend services (resource crisis follow-up)

**Track:** C — go-live, urgent operational follow-up. **Date:** 2026-07-17.

## Why this sprint exists

`dnit-vps` (shared box, ~961Mi total RAM, also hosts Pet Giftshop production+staging,
a file-upload API, and a separate `food-manager` project) went unresponsive today:
load average **156.02**, swap **100% full** (2.0Gi/2.0Gi), SSH and all three
`saga-*.dnit.be` endpoints timing out entirely. Root cause of the *trigger*: a
Pet-Giftshop-side LXD container extraction (`unsquashfs`, `petgiftshop-cleanup`)
got stuck thrashing for ~56 minutes, compounding existing memory pressure until the
whole box seized up. Killing that stuck process (`kill -9`) recovered it.

**But the trigger isn't the root cause of why the box has no headroom at all.**
Investigated while recovering: **none of this project's 3 Spring services, nor Kafka,
have any JVM heap cap configured** — all run with unconstrained default heap sizing
on a box shared with several other tenants, each JVM independently unaware of how
much RAM the others need. Dimitri's call: optimize resource usage first, before
considering paying for a bigger VPS tier.

## Findings that shaped this brief (verified directly, not assumed)

- All three systemd units (`deploy/systemd/pubrec-{auth,order,inventory}.service`)
  use a bare `ExecStart=/usr/bin/java -jar ...` — no `-Xmx`/`-Xms` anywhere.
- `deploy/docker-compose/kafka-vps.yml` sets no `KAFKA_HEAP_OPTS` — the
  `confluentinc/cp-kafka:7.8.0` image's baked-in default is `-Xmx1G -Xms1G`,
  confirmed live via `ps aux` showing exactly that flag on the running process.
  `zookeeper` (same compose file) also has no heap override.
- Live `ps aux --sort=-rss` during the incident showed `inventory-service` at
  **275MB RSS** — 4x `order-service`'s 69MB and ~10x `auth-server`'s 29MB. Root
  cause identified by comparing the two codebases directly: `order-service` has
  **zero** `@Scheduled` tasks (purely reactive — Kafka listener + REST), while
  `inventory-service`'s `OutboxRelay.scheduledRelay()` (Sprint 24) polls **every
  ~500ms, continuously**, since the service started. That constant allocation/GC
  churn is exactly the workload pattern that grows a JVM's heap over time when
  nothing bounds it — not a leak, just unconstrained growth under sustained load
  the other two services don't have.

## What to change (artifacts only — this sprint does not touch the live VPS)

### 1. Heap caps on the three systemd units

Add explicit, modest caps to each `ExecStart` line. Sizing rationale: generous
enough that normal operation never hits the ceiling and starts thrashing/OOMing,
but low enough to meaningfully bound worst-case growth compared to today's
"unlimited" default:

- `deploy/systemd/pubrec-auth.service`: `ExecStart=/usr/bin/java -Xms64m -Xmx160m -jar /opt/pubrec/auth/backend.jar`
- `deploy/systemd/pubrec-order.service`: `ExecStart=/usr/bin/java -Xms64m -Xmx160m -jar /opt/pubrec/order/backend.jar`
- `deploy/systemd/pubrec-inventory.service`: `ExecStart=/usr/bin/java -Xms64m -Xmx224m -jar /opt/pubrec/inventory/backend.jar` (higher than the other two — this service's continuous `OutboxRelay` polling is the one genuinely different, higher-churn workload; verified above, not guessed)

Change nothing else in these files.

### 2. Kafka + ZooKeeper heap caps

`deploy/docker-compose/kafka-vps.yml`: add an environment variable to each service
to override the image's oversized default. Confirm the correct env var name for
`confluentinc/cp-zookeeper:7.8.0` before assuming it's the same `KAFKA_HEAP_OPTS`
used by the `cp-kafka` image (both are built on the same base image family and
conventionally share this variable name, but verify against the actual image's
documented environment variables rather than assume):
```yaml
  zookeeper:
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
      KAFKA_HEAP_OPTS: "-Xms128m -Xmx256m"
  kafka:
    environment:
      ...(existing vars unchanged)...
      KAFKA_HEAP_OPTS: "-Xms256m -Xmx512m"
```
This is a demo app with 3 topics and a handful of messages per test run — 512MB is
already generous headroom over what's actually needed, a large reduction from the
1GB default without risking under-provisioning for this workload.

## Explicitly out of scope

- **No live changes to the running VPS** — this sprint produces artifacts only.
  Applying the new heap flags requires restarting all three services (and the
  Kafka/ZooKeeper containers), which is a live-apply step for Dimitri to run
  himself when ready, same split as every other infra sprint in this Track.
- **No KRaft-mode migration** (collapsing ZooKeeper+Kafka into one process) — a
  real, bigger optimization already noted as deferred in the Track C roadmap.
  Worth reconsidering given today's incident, but it's a larger, riskier change
  than adding heap caps; scope separately if heap caps alone don't give enough
  headroom.
- **No change to `inventory-service`'s 500ms polling interval** — reducing its
  frequency would also reduce GC churn, but that's a demo-responsiveness trade-off
  Dimitri should decide deliberately, not something to change silently as a side
  effect of a resource-crisis fix.
- **No action on the Pet Giftshop `petgiftshop-cleanup` LXD container** — not this
  project's concern; flagged to Dimitri, his call on that separately.

## Acceptance criteria (show real output, don't assert "Pass")

1. Show the actual diff of all 4 changed files (3 systemd units + the compose file).
2. Confirm the chosen `KAFKA_HEAP_OPTS`/heap-cap env var name is genuinely correct
   for the `cp-zookeeper` image (cite where this was confirmed, don't just assume
   parity with `cp-kafka`).
3. `git status --short` clean after commit.
4. State explicitly that live-apply (restarting services, applying the new compose
   config) is NOT part of this task and needs Dimitri's separate action — don't
   attempt it, don't assume it, don't guess at whether it's already been done.

## Loop note

Reviewer: this is a config-only change (no application code touched), low risk to
review — the main thing worth double-checking is that the `KAFKA_HEAP_OPTS`
convention is verified against the actual `cp-zookeeper` image docs/behavior, not
copy-pasted from the `cp-kafka` service on an assumption they're identical.
