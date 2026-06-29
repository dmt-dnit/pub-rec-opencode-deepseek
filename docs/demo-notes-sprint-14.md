# Sprint 14 Demo Notes

Date: 2026-06-28
Source review: `reviews/sprint-14-track-b-review.md`

## Presentation Verdict

Sprint 14 is useful demo material, but not as a success montage. It shows the first Track B hardening sprint producing real retry, DLT, and idempotency coverage, then independent review catching a target-platform CI failure that local Windows validation would miss.

## Story Beats

- Track B started with infrastructure and reliability work rather than new UI features: GitHub Actions CI plus Kafka listener retry/DLT/idempotency hardening.
- The backend work mostly held up under local verification: `shared-model`, `auth-server`, `order-service`, and `inventory-service` Maven verifies all passed under Java 21.
- The important failure was not in Java code. The workflow calls `./mvnw` on Ubuntu, but all four Unix wrapper files are tracked as mode `100644`, so CI would fail with a permission error before tests could run.
- This is a good example of why reviewer validation must reason about the actual target runtime, not just the coordinator's local machine.
- A secondary reliability nuance remains: inventory duplicate handling prevents double stock decrement for successful reservations, but it does not persist or replay the original outcome for rejected reservations.

## Evidence To Preserve

- `git ls-files --stage auth-server/mvnw inventory-service/mvnw order-service/mvnw shared-model/mvnw` reported all four wrappers as `100644`.
- `.github/workflows/ci.yml` invokes `./mvnw` in the backend jobs, so Ubuntu runners need executable wrapper bits or an explicit `bash ./mvnw`.
- `gh run list --repo dmt-dnit/pub-rec-opencode-deepseek --limit 10` returned no workflow runs because the Sprint 14 commits were still local and not pushed.
- `npm audit --omit=dev` passed with zero production vulnerabilities in both Angular UIs.
- `npm run build` passed in both Angular UIs.

## Visual Capture

No UI screenshot or video was captured for Sprint 14. This sprint is backend and CI focused, so the useful demo artifact is the review evidence itself: a local green verification set plus a Linux CI metadata failure caught before push.

## Conference Angle

This sprint is a credible "agents still need independent review" moment. The implementation looked complete and local checks passed, but a small Git file-mode detail would have broken the automation gate. That is stronger material than a clean happy-path demo because it shows the process catching a real cross-environment defect.

## Follow-Up For Next Sprint

- Fix the Unix Maven wrapper executable bits or invoke the wrapper through `bash`.
- Decide whether B-1 should require full outcome idempotency for rejected inventory reservations, not only no double-decrement for successful reservations.

## Round 2 Addendum

Date: 2026-06-29
Source review: `reviews/sprint-14-track-b-round-2-review.md`

Sprint 14 is now better demo material because it has a full failure-to-green arc:

- Codex rejected round 1 for a target-platform CI defect: Unix `mvnw` files were tracked as `100644`, so Ubuntu runners would fail before Maven started.
- Round 2 fixed the executable bits (`100755`), then hit a second real CI problem: the shared-model artifact hand-off failed in live Actions.
- The durable fix was to stop transferring the Maven local repository between jobs and instead build `shared-model` inline inside each downstream Maven job.
- Live Actions run `28354413116` for commit `f5d81b4` passed with all six jobs green: `shared-model`, `auth-server`, `order-service`, `inventory-service`, `order-ui`, and `inventory-ui`.
- The inventory idempotency gap was also closed for normal duplicate delivery: duplicate success and duplicate rejection now publish exactly once and push exactly one WebSocket feed item.

This gives a strong conference story: the agent workflow did not just build CI, it caught two CI design mistakes before treating the gate as trustworthy. The first mistake was local/Windows vs Linux metadata. The second was an apparently reasonable artifact-transfer design that failed only in the real hosted runner.

Remaining nuance to mention honestly:

- The workflow is green but not dependency-current: current GitHub tags are newer majors than the `actions/*@v4` pins. Treat the Node 20 deprecation warning as a small follow-up task, not as a Sprint 14 failure.
- The inventory fix is outcome-idempotent for normal duplicate delivery, but not an outbox/transactional-publish design. Strong crash-safe DB+Kafka exactly-once semantics remain future hardening.

Suggested visual evidence:

- Screenshot the green GitHub Actions run `28354413116` with the six job boxes visible.
- Screenshot the review finding from `reviews/sprint-14-track-b-review.md` next to the green round-2 review verdict to show the process improvement loop.
- If using terminal footage, replay `git ls-files --stage auth-server/mvnw inventory-service/mvnw order-service/mvnw shared-model/mvnw` showing `100755`.
