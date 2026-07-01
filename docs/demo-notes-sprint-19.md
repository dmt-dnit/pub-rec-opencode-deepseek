# Demo Notes - Sprint 19

Sprint 19 is a strong example of why "operational hardening" needs the same review discipline as product code.

What changed:

- CI gained a dedicated `e2e-smoke` job that stands the stack up with compose, waits for health, starts both Angular UIs, and runs the Playwright saga smoke.
- The bare-container fallback script replaced some fixed sleeps with explicit readiness probes for Zookeeper, Kafka, and the two business services.

What the review found:

- The CI job itself reads cleanly and I did not find a blocking source-level defect in the YAML.
- The fallback shell script is not fully hardened despite the handoff claim. Auth-server still uses a non-failing retry loop, so the script can continue even when JWKS never becomes ready.
- The so-called plain-podman fallback still flips to Docker whenever the `docker` CLI exists, even if Podman is the actual working runtime.

Why that matters for the story:

- This is not a flashy algorithm bug. It is a realistic automation bug: the handoff story sounded right, the diff was small, but the reliability guarantee was still weaker than advertised.
- It is a good conference example because it shows AI workflows can now build orchestration and CI, but they still need independent review for failure semantics and environment selection logic.

Presentation angle:

- Sprint 18 was about proving a concurrency fix at the UI boundary.
- Sprint 19 is the next layer up: automating that proof in CI and hardening the developer fallback path.
- The review result is useful precisely because it separates the two: CI automation looks on the right track, fallback reliability still has real edge-case bugs.
