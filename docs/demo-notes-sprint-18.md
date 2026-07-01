# Demo Notes - Sprint 18

Sprint 18 is a good "workflow maturity" story because it is not about a dramatic bug fix. It is about closing a reliability gap in how the stack is started and how the reviewer proves a concurrency fix at the UI boundary.

What changed:

- The stack now has explicit compose-level readiness checks instead of start-order only. Kafka gets a real broker probe, and `order-service` / `inventory-service` wait for healthy Kafka plus healthy auth before starting.
- The F2 smoke assertion moved from "a reservation appeared" to "the reservation feed count increased by exactly one", which is the right observable check for duplicate STOMP pushes on a dirty database.

What makes it interesting:

- This sprint turns an architecture claim into a system-behavior claim. Sprint 17 fixed the outbox race in backend code; Sprint 18 makes the reviewer prove it at the UI feed.
- It also surfaces a useful distinction for a conference audience: compose-path reliability and fallback-path reliability are not the same thing. `docker-compose.yml` is now health-gated, while `scripts/startup-all.sh`'s no-compose fallback still uses simpler sequencing.

Reviewer outcome:

- I did not find a blocking code defect in the Sprint 18 changes.
- I could verify the source-level compose gating and the source-level F2 assertion wiring.
- I could not complete the live compose-path and browser-path verification in this environment because no working compose provider / Podman socket was available here.

Presentation angle:

- This is a clean example of independent review adding precision even when the code is likely correct. The right outcome is not always "reject" or "approve"; sometimes it is "source is sound, runtime proof still pending".
- That is valuable in an AI workflow story because it shows the gate is checking both code correctness and evidence quality, not collapsing those into the same thing.
