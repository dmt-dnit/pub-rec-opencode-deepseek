# Demo Notes - Sprint 15

Sprint 15 is now a strong demo beat: CI and Maven were green, Codex's live browser/runtime gate found a real container-only JWT bug, and round 2 fixed it with the same Playwright smoke proving the saga works again.

Useful evidence for the story:

- Green CI: GitHub Actions run `28365627533` passed for `17843ea7222390ffcd51daafa8776270d39ac001`.
- Failed live smoke: `cd e2e && npm test` failed at `e2e/smoke.spec.ts:70`, before order placement, because authenticated backend reads returned HTTP 500.
- Root cause: `order-service/.../SecurityConfig.java:25` and `inventory-service/.../SecurityConfig.java:25` construct `NimbusJwtDecoder` with a hardcoded localhost JWKS URL.
- Why it matters: `docker-compose.yml` and `scripts/startup-all.sh` already contain the correct container URL, but the code-level bean prevents the configuration from taking effect.
- Round-2 proof: GitHub Actions run `28371179353` passed for `13cf8ddb7d0e938fe316a76c5757e1acb9997c5a`, direct authenticated `/api/**` probes returned HTTP 200, and `cd e2e && npm.cmd test` passed with `1 passed (14.7s)`.

Suggested talk/demo framing:

- First show the green CI run and the failed smoke result side by side.
- Then show the one-line hardcoded decoder root cause.
- After the fix, show the same Playwright smoke passing: login, order placement, confirmed order badge, reservation feed, and inventory decrement.
- Emphasize that the bug predated Sprint 15, but the Boot 4 migration forced a real runtime gate that caught it before Track B moved on.

Sprint 15 can now be presented as cleared, with the remaining compatibility-mode cleanup explicitly deferred to Sprint 16.
