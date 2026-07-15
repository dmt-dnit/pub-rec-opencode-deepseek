# Sprint 23 (Track C Phase 3.5) — Handoff to Codex

**Coordinator:** Claude Code. **Date:** 2026-07-15.
**Tasks:** K-1 (Kafka+ZooKeeper Docker Compose + systemd unit for `dnit-vps`). Deploy-artifact-authoring only, same split as Sprint 22 — no live infrastructure touched by this task.
**Implementer:** opencode+DeepSeek, standing default, isolated worktree. Diff coordinator-reviewed by reading; integrated onto `main`.
**Merge-gate tier:** coordinator-supervised, pushed direct to `main` per the Sprint 21 policy.
Review-Target-Commit: 1728fcd

## Why this sprint exists
Sprint 22's live-apply session (2026-07-15, same day) got all three Spring services running on `dnit-vps`, but discovered live that there's no Kafka broker on that box at all — `order-service`/`inventory-service` are healthy but retry `localhost:9092` forever, so the actual saga doesn't work. Missed at Sprint 22 scoping. Full context: `docs/backlog/track-c-go-live-roadmap.md` Phase 3.5, `docs/backlog/sprint-22-live-apply-runbook.md`'s Kafka-gap note.

## Commit
`1728fcd` — `deploy/docker-compose/kafka-vps.yml`, `deploy/systemd/pubrec-kafka.service`, and a new "Phase 3.5 — Kafka" section in `docs/backlog/sprint-22-live-apply-runbook.md`.

## The one thing that actually matters for review: port binding
Kafka's `PLAINTEXT` listener has no authentication. Local dev's `order-service/docker-compose.yml` (the base this was adapted from) publishes bare `"2181:2181"`/`"9092:9092"`, which Docker binds to `0.0.0.0` by default — fine on a laptop, a real vulnerability on a box with a public IP. This adaptation changes both to `"127.0.0.1:2181:2181"`/`"127.0.0.1:9092:9092"`.

**Coordinator verification:** diffed directly against `order-service/docker-compose.yml` (`diff order-service/docker-compose.yml deploy/docker-compose/kafka-vps.yml`) — confirmed the *only* differences are: `version: "3.8"` dropped in favor of top-level `name: pubrec` (Compose v2 convention, `version` is deprecated/ignored), `restart: unless-stopped` added to both services, and the port bindings gaining the `127.0.0.1:` prefix. Every `KAFKA_*`/`ZOOKEEPER_*` environment variable and both image tags (`confluentinc/cp-zookeeper:7.8.0`, `confluentinc/cp-kafka:7.8.0`) are byte-identical to local dev — this is the exact broker config the project is already tested against, not a reimplementation.

`KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092` is intentionally unchanged — all three Spring services run on the same VPS as the broker, so `localhost` resolves correctly for them; nothing external is meant to reach Kafka directly, which is exactly what the `127.0.0.1` bind enforces.

## systemd unit
`deploy/systemd/pubrec-kafka.service` — `Type=oneshot` + `RemainAfterExit=yes` (required because `docker compose up -d` itself exits immediately after launching detached containers; without `RemainAfterExit`, systemd would consider the service "dead" the moment the launcher process exits). `Requires=docker.service`/`After=docker.service network.target` ensures correct boot ordering. No persistent volumes — matches the project's existing "no persistent state anywhere" pattern (all three Spring services are in-memory H2); losing Kafka's in-broker state across a rare restart is acceptable for a demo.

## Runbook addition
New "Phase 3.5 — Kafka broker" section in `docs/backlog/sprint-22-live-apply-runbook.md`, same shape as Phases 1–2 (bash commands, explicitly marked as needing root). Includes an explicit port-binding verification step (`ss -tlnp | grep -E ':(2181|9092)'`, expecting `127.0.0.1:` prefixes, not `0.0.0.0`/`*`) with an explicit "stop immediately if this shows public binding" instruction — not left implicit. Also includes restarting `pubrec-order`/`pubrec-inventory` after Kafka comes up, so they pick up the broker on their next connection attempt rather than waiting for a retry cycle.

## Coordinator verification notes
- `git status --short` clean on `main` after cherry-pick.
- `deploy/docker-compose/kafka-vps.yml` re-verified independently (not just trusting the implementer's grep): `grep -c "127.0.0.1:" deploy/docker-compose/kafka-vps.yml` → `2`; no bare `"2181:2181"`/`"9092:9092"` anywhere; no `0.0.0.0` anywhere in the file.
- YAML validated via Python's `yaml.safe_load` — the `docker` CLI wasn't available in the implementer's environment for a `docker compose config` dry-run, stated explicitly rather than skipped silently. **This should be re-verified with the real `docker compose config` at live-apply time**, since that also validates Compose-specific semantics `yaml.safe_load` can't catch (e.g. schema errors).
- `docker`/Compose v2 confirmed already installed and running on `dnit-vps` (`Docker version 29.6.1`, `Docker Compose version v5.2.0`) before this task was briefed — not assumed.
- **No live infrastructure was touched** — no SSH commands against `dnit-vps` beyond the coordinator's own earlier read-only checks (Docker version, port availability), no compose stack started, no systemd unit installed live. The live-apply session is separately scheduled with Dimitri in the loop, same pattern as Sprint 22.

## Loop note
Reviewer: there's no live target to exercise here (same as Sprint 22's artifact round) — "verify against the real target" means checking internal consistency and correct derivation from the repo's actual local-dev config (which you can and should diff against directly), not exercising a live deploy. The port-binding check is the one finding that would actually matter if wrong; everything else is low-risk config plumbing.
