# Demo Notes - Sprint 15

Sprint 15 is a strong demo beat, but not as a success slide yet. It shows why the Codex-only browser/runtime gate exists: CI and Maven were green, but the live stack failed because the resource servers hardcoded `http://localhost:9000/oauth2/jwks` and ignored the compose/startup override to `http://auth-server:9000/oauth2/jwks`.

Useful evidence for the story:

- Green CI: GitHub Actions run `28365627533` passed for `17843ea7222390ffcd51daafa8776270d39ac001`.
- Failed live smoke: `cd e2e && npm test` failed at `e2e/smoke.spec.ts:70`, before order placement, because authenticated backend reads returned HTTP 500.
- Root cause: `order-service/.../SecurityConfig.java:25` and `inventory-service/.../SecurityConfig.java:25` construct `NimbusJwtDecoder` with a hardcoded localhost JWKS URL.
- Why it matters: `docker-compose.yml` and `scripts/startup-all.sh` already contain the correct container URL, but the code-level bean prevents the configuration from taking effect.

Suggested next-sprint demo artifact:

- First show the green CI run and the failed smoke result side by side.
- Then show the one-line hardcoded decoder root cause.
- After the fix, rerun the same Playwright smoke and capture the passing saga: login, order placement, confirmed order badge, reservation feed, and inventory decrement.

Do not present Sprint 15 as cleared until the JWT decoder uses configuration and the full Playwright smoke passes against the documented containerized backend path.

