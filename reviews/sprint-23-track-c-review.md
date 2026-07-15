# Sprint 23 Track C Review - Kafka VPS Artifacts

Review-Target-Commit: `1728fcd`  
Handoff: `docs/backlog/sprint-23-handoff.md`  
Verdict: ACCEPT

## Findings

No blocking source-level findings in Sprint 23.

## Verified Against Handoff

- **The security-critical port binding is correct.** The VPS compose file binds ZooKeeper and Kafka to loopback only at [deploy/docker-compose/kafka-vps.yml](C:/projects/pub-rec-opencode-deepseek/deploy/docker-compose/kafka-vps.yml:11) and [deploy/docker-compose/kafka-vps.yml](C:/projects/pub-rec-opencode-deepseek/deploy/docker-compose/kafka-vps.yml:19), while preserving the broker’s local-host advertised listener at [deploy/docker-compose/kafka-vps.yml](C:/projects/pub-rec-opencode-deepseek/deploy/docker-compose/kafka-vps.yml:23). That matches the application-side bootstrap configuration in [order-service/src/main/resources/application.yml](C:/projects/pub-rec-opencode-deepseek/order-service/src/main/resources/application.yml:25) and [inventory-service/src/main/resources/application.yml](C:/projects/pub-rec-opencode-deepseek/inventory-service/src/main/resources/application.yml:12), which both target `localhost:9092`.

- **The compose adaptation is intentionally narrow.** Relative to the local-dev base, the committed VPS file adds only a top-level project name at [deploy/docker-compose/kafka-vps.yml](C:/projects/pub-rec-opencode-deepseek/deploy/docker-compose/kafka-vps.yml:1), `restart: unless-stopped` on both services at [deploy/docker-compose/kafka-vps.yml](C:/projects/pub-rec-opencode-deepseek/deploy/docker-compose/kafka-vps.yml:6) and [deploy/docker-compose/kafka-vps.yml](C:/projects/pub-rec-opencode-deepseek/deploy/docker-compose/kafka-vps.yml:15), and the loopback host bindings. The Confluent image tags and the Kafka/ZooKeeper environment block stay aligned with the proven local configuration in [order-service/docker-compose.yml](C:/projects/pub-rec-opencode-deepseek/order-service/docker-compose.yml:1).

- **The systemd wrapper matches detached Docker Compose semantics.** The unit uses `Type=oneshot` plus `RemainAfterExit=yes` at [deploy/systemd/pubrec-kafka.service](C:/projects/pub-rec-opencode-deepseek/deploy/systemd/pubrec-kafka.service:7) and [deploy/systemd/pubrec-kafka.service](C:/projects/pub-rec-opencode-deepseek/deploy/systemd/pubrec-kafka.service:8), with explicit Docker ordering at [deploy/systemd/pubrec-kafka.service](C:/projects/pub-rec-opencode-deepseek/deploy/systemd/pubrec-kafka.service:3) and [deploy/systemd/pubrec-kafka.service](C:/projects/pub-rec-opencode-deepseek/deploy/systemd/pubrec-kafka.service:4), and the expected start/stop commands at [deploy/systemd/pubrec-kafka.service](C:/projects/pub-rec-opencode-deepseek/deploy/systemd/pubrec-kafka.service:10) and [deploy/systemd/pubrec-kafka.service](C:/projects/pub-rec-opencode-deepseek/deploy/systemd/pubrec-kafka.service:11). For a detached compose stack, that is the correct systemd shape.

- **The runbook addition encodes the right safety check.** The new Kafka phase includes the loopback verification step at [docs/backlog/sprint-22-live-apply-runbook.md](C:/projects/pub-rec-opencode-deepseek/docs/backlog/sprint-22-live-apply-runbook.md:269), [docs/backlog/sprint-22-live-apply-runbook.md](C:/projects/pub-rec-opencode-deepseek/docs/backlog/sprint-22-live-apply-runbook.md:270), and the immediate dependent-service restart at [docs/backlog/sprint-22-live-apply-runbook.md](C:/projects/pub-rec-opencode-deepseek/docs/backlog/sprint-22-live-apply-runbook.md:287). That matches the actual failure mode described in the handoff: healthy services with no live broker.

## Residual Checks Not Reproduced Here

- I did not run `docker compose config` on the VPS artifact from this environment.
- I did not run a live compose bring-up, `ss -tlnp`, or a live saga check against the VPS.
- I did not verify the runtime presence of `/usr/bin/docker` on the target box from this review session; I am treating that as handoff-backed operational context rather than independently reproduced evidence.

Those are live-apply checks, not source-level blockers in this artifact review.
