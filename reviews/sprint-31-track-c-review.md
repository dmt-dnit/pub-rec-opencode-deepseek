# Sprint 31 Track C Review - JVM Heap Caps

Review-Target-Commit: `12038b3`  
Handoff: `docs/backlog/sprint-31-handoff.md`  
Verdict: ACCEPT

## Findings

No blocking source-level findings in Sprint 31.

## Verified Against Handoff

- **All three systemd units now carry explicit heap caps.** The committed `ExecStart` lines add bounded JVM heaps in [deploy/systemd/pubrec-auth.service](C:/projects/pub-rec-opencode-deepseek/deploy/systemd/pubrec-auth.service:11), [deploy/systemd/pubrec-order.service](C:/projects/pub-rec-opencode-deepseek/deploy/systemd/pubrec-order.service:11), and [deploy/systemd/pubrec-inventory.service](C:/projects/pub-rec-opencode-deepseek/deploy/systemd/pubrec-inventory.service:11). That matches the handoff's artifact-level claim exactly.

- **The Kafka-side compose artifact was updated in the two stated places only.** `KAFKA_HEAP_OPTS` is present for ZooKeeper at [deploy/docker-compose/kafka-vps.yml](C:/projects/pub-rec-opencode-deepseek/deploy/docker-compose/kafka-vps.yml:10) and for Kafka at [deploy/docker-compose/kafka-vps.yml](C:/projects/pub-rec-opencode-deepseek/deploy/docker-compose/kafka-vps.yml:30). No listener, port-binding, or persistence settings were changed as part of this sprint.

- **This sprint introduces no new application libraries or package-manager dependencies.** The delta is limited to deployment artifacts, so dependency freshness and application vulnerability exposure are unchanged from the previously reviewed codebase. The one operational dependency note is that the VPS compose stack remains on the pre-existing ZooKeeper-era `confluentinc/cp-zookeeper:7.8.0` and `confluentinc/cp-kafka:7.8.0` images at [deploy/docker-compose/kafka-vps.yml](C:/projects/pub-rec-opencode-deepseek/deploy/docker-compose/kafka-vps.yml:5) and [deploy/docker-compose/kafka-vps.yml](C:/projects/pub-rec-opencode-deepseek/deploy/docker-compose/kafka-vps.yml:15); this sprint caps their heaps but does not modernize that stack.

## Residual Checks Not Reproduced Here

- I did not perform the live VPS apply (`systemctl daemon-reload` / service restarts / Kafka container recreation) from this review session.
- I did not independently execute the Confluent containers to observe the capped JVM command lines after startup; this review is source-level and artifact-level only.

Those are operational follow-ups for the live-apply step, not blockers to accepting the committed config change.
