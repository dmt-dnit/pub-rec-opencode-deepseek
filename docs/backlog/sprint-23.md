# Sprint 23 (Track C Phase 3.5) — Kafka broker deploy artifacts for `dnit-vps`

**Track:** C — go-live. **Theme:** close the gap found live during Sprint 22's Phase 4 —
`order-service`/`inventory-service` are deployed and healthy on `dnit-vps`, but there is
no Kafka broker there, so the actual saga (the point of the demo) doesn't work yet.

## Why this sprint exists
`docs/backlog/track-c-go-live-roadmap.md` Phase 3.5. Missed at Sprint 22 scoping — the
roadmap's "Kafka: always-on" resource-budget decision never became deploy artifacts.
Confirmed live: both services' Kafka listeners continuously log `Rebootstrapping` against
`localhost:9092` with no broker to answer.

## Ground truth this brief is built on
- Docker `29.6.1` + Compose v2 plugin (`v5.2.0`) already installed and running on
  `dnit-vps` (confirmed live) — no new tooling needed, matches the "reuse what's already
  there" pattern from Sprint 22.
- `administrator` is not in the `docker` group — same least-privilege posture as
  everything else on this box; live-apply needs root/sudo, same as Phases 1–2.
- Ports `2181`/`9092` confirmed free on the box during Track C's port-scheme
  verification.
- **Local dev's `order-service/docker-compose.yml` is the base to adapt — not
  reinvent.** It already runs the exact Kafka/ZooKeeper config this project is tested
  against (`confluentinc/cp-zookeeper:7.8.0`, `confluentinc/cp-kafka:7.8.0`,
  `KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092`).

## The one real deviation from local dev, and why it's mandatory
Kafka's `PLAINTEXT` listener has **no authentication**. Local dev's compose file
publishes ports as `"2181:2181"`/`"9092:9092"`, which Docker binds to `0.0.0.0` by
default — completely fine on a laptop, **a real vulnerability on a box with a public
IP** (anyone on the internet could connect, read, and write to the broker). The VPS
version **must** bind to `127.0.0.1` only:
```yaml
ports:
  - "127.0.0.1:2181:2181"
  - "127.0.0.1:9092:9092"
```
`KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092` stays unchanged — all three
Spring services run on the same VPS as the broker, so `localhost` resolves correctly for
them; nothing external needs to reach Kafka directly.

## Tasks (1, self-contained)

| ID | Title | Brief | Risk | Implementer (rec) |
|----|-------|-------|------|-------------------|
| K-1 | Kafka+ZooKeeper Docker Compose + systemd unit for `dnit-vps` | this file | Med (security-sensitive: port binding correctness is the whole point) | opencode+DeepSeek |

## K-1 deliverables

### 1. `deploy/docker-compose/kafka-vps.yml`
Adapted from `order-service/docker-compose.yml`, with:
- Top-level `name: pubrec` (avoids any Compose project-name collision with other stacks
  already on the shared box, e.g. Dupslog's `postgres16` container).
- Both `zookeeper` and `kafka` services' `ports:` bound to `127.0.0.1` only (see above —
  this is the acceptance-critical part of this brief, not optional).
- `restart: unless-stopped` on both services (this is a persistent VPS deployment, not a
  manually-started local dev session — matches the "always-on" resource-budget decision
  already made in the roadmap).
- Image versions and all `KAFKA_*`/`ZOOKEEPER_*` environment variables **identical** to
  `order-service/docker-compose.yml` — don't "helpfully" bump versions or tune settings
  as a side effect of this task; this needs to behave exactly like the tested local
  config, just correctly network-isolated.
- No persistent volumes — matches this project's existing "no persistent state anywhere"
  pattern (the three Spring services are all in-memory H2; losing Kafka's in-broker state
  across a rare restart is acceptable for a demo).

### 2. `deploy/systemd/pubrec-kafka.service`
A systemd unit that manages the compose stack as a single service, consistent with how
every other `pubrec-*` service on this box is managed:
```ini
[Unit]
Description=Pub-Rec Demo — Kafka + ZooKeeper (Docker Compose)
Requires=docker.service
After=docker.service network.target

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/opt/pubrec/kafka
ExecStart=/usr/bin/docker compose -f /opt/pubrec/kafka/docker-compose.yml up -d
ExecStop=/usr/bin/docker compose -f /opt/pubrec/kafka/docker-compose.yml down
TimeoutStartSec=180

[Install]
WantedBy=multi-user.target
```
(`Type=oneshot` + `RemainAfterExit=yes` because `docker compose up -d` itself exits
immediately after starting the detached containers — systemd needs to be told the
service stays "active" even though the launching process has exited.)

### 3. Update `docs/backlog/sprint-22-live-apply-runbook.md`
Add a new "Phase 3.5 — Kafka" section following the same shape as Phases 1–2 (bash
commands to run, explicitly marked as needing root, explaining the `127.0.0.1`-binding
requirement inline so a future reader doesn't "helpfully" simplify it away).

## Explicitly out of scope
- Do not actually deploy anything live — this brief produces repo artifacts only, same
  artifact-authoring/live-apply split as Sprint 22.
- Do not touch `order-service/docker-compose.yml` (local dev config) — this is a new,
  separate file for the VPS.
- Do not add persistent volumes, authentication (SASL/SSL), or KRaft-mode migration —
  those are explicitly deferred (KRaft noted as an opportunistic future optimization in
  the roadmap, not blocking).

## Acceptance criteria (observable outcomes)
1. `deploy/docker-compose/kafka-vps.yml` exists, `docker compose -f deploy/docker-compose/kafka-vps.yml config` (or `python3 -c "import yaml..."` if the `docker` CLI isn't available in the implementer's environment — state explicitly which was used) parses without error.
2. Both `ports:` entries bound to `127.0.0.1` — `grep -c "127.0.0.1:" deploy/docker-compose/kafka-vps.yml` returns `2`; **zero** occurrences of a bare `"2181:2181"` or `"9092:9092"` (unbound = public) anywhere in the file.
3. `KAFKA_ADVERTISED_LISTENERS`/`KAFKA_ZOOKEEPER_CONNECT`/image versions diffed against `order-service/docker-compose.yml` and confirmed identical except for the port-binding and `restart`/`name` additions — show the actual diff.
4. `deploy/systemd/pubrec-kafka.service` exists, matches the shape given above.
5. `docs/backlog/sprint-22-live-apply-runbook.md` has a new Phase 3.5 section.
6. `git status --short` shows only the 3 new/changed files — no scope creep into the Spring services' existing artifacts.

## Loop note
Standard cadence: opencode implements in a worktree → coordinator verifies by reading
(the port-binding check is the one that actually matters here, verify it don't just
trust the diff) → handoff → Codex reviews (security-sensitive enough to be worth a real
review, not skipped) → `verify-review.sh` gates the close → live-apply session with
Dimitri, same privilege-boundary dance as Sprint 22's Phases 1–2.
