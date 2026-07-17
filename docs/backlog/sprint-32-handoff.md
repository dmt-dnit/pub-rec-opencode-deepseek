# Sprint 32 — Closure Note (Codex review explicitly skipped)

**Coordinator:** Claude Code. **Date:** 2026-07-17.
**Task:** persist `auth-server`'s JWT signing key across restarts, so sessions survive redeploys the same way Sprint 29 made user accounts survive them.
Review-Target-Commit: 99552cb

## Why Codex review is being skipped for this one, logged not silently dropped

This fix was implemented by a **Codex browser-debugging agent** — a separate instance Dimitri had asked to help investigate the unrelated `localStorage` token-loss bug (`docs/backlog/investigation-localstorage-token-loss.md`), and asked it to "help me fix it," which it took as license to make a direct source-code fix rather than diagnosis-only. Dimitri confirmed this was an unintentional scoping slip on his part, not a deliberate workflow change.

This duplicated an opencode-dispatched Sprint 32 already in flight for the exact same fix (same brief, `docs/backlog/sprint-32.md`) — that worktree was abandoned once Codex's version was found to be more complete (it adds a genuine automated regression test, `JwtConfigTest.java`, which the opencode brief didn't ask for). Since Codex itself wrote this code, having Codex review it afterward would not be a genuine independent check — same conflict either the coordinator or Codex would have reviewing its own work. **Dimitri's explicit call: skip the formal Codex review step for this sprint, rely on coordinator verification instead** — same precedent as Sprint 25's skip.

## What was done (`99552cb`)

`auth-server/src/main/java/be/dnit/authserver/config/JwtConfig.java`:
- New `app.jwt.key-path` property (`APP_JWT_KEY_PATH` env override, Spring relaxed binding, same mechanism as Sprint 29's `SPRING_DATASOURCE_URL`). Empty/unset (local dev/CI default) → today's ephemeral in-memory key generation, byte-for-byte unchanged behavior.
- When set: loads a persisted RSA key from the file if it exists, or generates one and saves it (PKCS8-encoded private key bytes, public key reconstructed from the private key's CRT parameters via `RSAPublicKeySpec`) — standard `java.security` APIs, no new runtime dependency.
- File permissions set explicitly to `0600` (owner read/write only) — sensitive key material, not left to the process umask.
- JWKS `kid` switches from a random UUID to a deterministic SHA-256 fingerprint of the public key — stable across restarts of the same key. **Verified before this change shipped**: `grep -rn "getKeyID\|\"kid\"" auth-server/ order-service/ inventory-service/ shared-model/` returns empty — no code anywhere depends on the `kid` claim's format.

`auth-server/src/main/resources/application.yml`: adds the `app.jwt.key-path: ${APP_JWT_KEY_PATH:}` binding.

`deploy/systemd/env-examples/auth.env.example`: documents the production value,
**reusing the same `/opt/pubrec/auth/data/` directory Sprint 29 already provisioned**
on the VPS for the persistent H2 file — no new directory/permissions setup needed.

`auth-server/src/test/java/be/dnit/authserver/config/JwtConfigTest.java` (new):
instantiates `JwtConfig` twice against the same temp key file (simulating a restart),
asserts the private/public key bytes and the JWKS `kid` are identical across both
instances — a real, permanent regression test, not just a one-off manual check.

## Coordinator verification

- `cd shared-model && ./mvnw clean install` — BUILD SUCCESS.
- `cd auth-server && ./mvnw clean verify` — **BUILD SUCCESS**, and the new test
  genuinely passes: `Tests run: 1, Failures: 0, Errors: 0` for `JwtConfigTest`
  (`auth-server`'s first-ever automated test).
- Reviewed the RSA reconstruction logic directly: `RSAPrivateCrtKey.getPublicExponent()`
  + `getModulus()` feeding `RSAPublicKeySpec` is standard, correct Java crypto API
  usage for deriving a public key from a CRT-form private key (the default form
  `KeyPairGenerator` produces for RSA) — not a shortcut or approximation.
- Confirmed the `kid`-format grep independently, same empty result as when the brief
  was scoped.
- `git status --short` clean on `main` after commit.

## Explicitly out of scope

- **No live VPS changes** — artifacts only. Applying this means adding
  `APP_JWT_KEY_PATH=/opt/pubrec/auth/data/jwt-signing.key` to the real
  `/etc/pubrec/auth.env` and redeploying `auth-server` — Dimitri's manual action.
  **This redeploy will invalidate all current sessions one final time** (same
  transition cost Sprint 29 had) — after that, sessions survive every future restart.
- No change to token expiry, issuance logic, or `SecurityConfig.java` — unchanged from
  the original brief's scope.

## Note for whoever next touches this file

`JwtConfig.java` now has a real test (`JwtConfigTest.java`) — keep it passing. If this
class's shape changes again, the test's assertions (identical key bytes + identical
`kid` across two instances sharing a key file) are the thing to preserve, not just
"it compiles."
