# Sprint 29 — persistent auth-server DB (H2 file mode, not a full DB migration)

**Track:** C — go-live, post-Phase-4 follow-up.

## Why this sprint exists

Dimitri's account and admin role were wiped by the very first backend redeploy after
Sprint 28 promoted the concept of "admin access that survives" — because `auth-server`
runs H2 in pure **in-memory** mode. Any JVM restart (redeploy or not) loses every user,
role, and approval. The Track C roadmap's Phase 8 ("real DB instead of H2," added
2026-07-16) was explicitly deferred as "decide when we get there" — but that phase
implied a bigger migration (Postgres, all three services). Dimitri chose the smaller,
faster fix instead: keep the existing embedded H2 engine, just switch it from in-memory
to file-backed for `auth-server` only. `order-service`/`inventory-service` stay
in-memory — resetting orders/inventory between demo sessions hasn't been flagged as a
problem.

## Root cause, found by reading the actual config (not assumed)

Two independent things currently wipe data on every restart, not one:
1. `spring.datasource.url: jdbc:h2:mem:authdb` — in-memory by definition, gone the
   moment the JVM stops.
2. `spring.jpa.hibernate.ddl-auto: create-drop` — **drops all tables on shutdown and
   recreates them empty on startup**, regardless of whether the underlying storage is
   in-memory or file-backed. Fixing only #1 without also fixing #2 would still wipe
   everything — verified by reading the actual `application.yml`, not assumed from the
   symptom.

The deploy mechanism itself is not at fault: `deploy/scripts/deploy-backend.sh` only
stops the service, replaces the jar, and restarts — it never touches any other file or
directory under `/opt/pubrec/auth/`. Confirmed by reading the actual script.

## What to change (code, this sprint's scope)

`auth-server/src/main/resources/application.yml`:
- `spring.jpa.hibernate.ddl-auto`: `create-drop` → `update`.
- **Leave `spring.datasource.url` as `jdbc:h2:mem:authdb` in this file** — that stays
  the correct default for local dev/CI (a file path like `/opt/pubrec/auth/data/authdb`
  doesn't exist on a developer's machine or in CI). The production override happens via
  environment variable, matching the existing pattern already used for
  `SPRING_H2_CONSOLE_ENABLED` in `deploy/systemd/env-examples/auth.env.example` — Spring
  Boot's relaxed env-var binding means `SPRING_DATASOURCE_URL` set in the systemd
  unit's `EnvironmentFile` overrides `spring.datasource.url` with **no code change
  needed** for the override mechanism itself.
- Add `SPRING_DATASOURCE_URL=jdbc:h2:file:/opt/pubrec/auth/data/authdb` as a new,
  documented (commented or example) line in
  `deploy/systemd/env-examples/auth.env.example` — this is the example/documentation
  file, not the real one on the VPS; actually enabling it there is a live-apply step
  (see below), not part of this code sprint.

`DataSeeder.java` needs no change — its existing `if (userRepository.count() > 0)
return;` guard is already correct/idempotent for a persistent store.

No test changes — `auth-server` has no test suite (confirmed, pre-existing, same as
Sprint 25/28's notes).

## Explicitly out of scope

- No change to `order-service`/`inventory-service` — they stay in-memory, unaffected.
- No Postgres, no new database engine, no docker-compose changes — this is not Phase 8.
- No change to `DataSeeder.java`'s seeded accounts.
- The actual live VPS provisioning (creating `/opt/pubrec/auth/data`, adding the real
  `SPRING_DATASOURCE_URL` line to the real `/etc/pubrec/auth.env`, redeploying) is
  **not part of this sprint's automated scope** — it's a one-time manual step Dimitri
  runs himself, same split as every other live-apply step in this Track (Phase 3's
  systemd/Nginx setup, Phase 3.5's Kafka compose stack). The coordinator has no sudo
  access on `dnit-vps` beyond a narrow exact-command allowlist that doesn't cover this
  (confirmed by testing directly — `sudo -n true` fails).

## Acceptance criteria (show real output, don't assert "Pass")

1. `cd shared-model && ./mvnw clean install`, then `auth-server`: `./mvnw clean verify`
   — **BUILD SUCCESS**.
2. Show the actual `git diff` of both changed files.
3. **Manually verify persistence works locally**, since there's no automated test for
   this: run `auth-server` locally (`./mvnw spring-boot:run` with
   `SPRING_DATASOURCE_URL=jdbc:h2:file:/tmp/sprint29-test-authdb` set as an environment
   variable, pointing somewhere disposable, not the real VPS path), confirm the seeded
   users exist via `GET /api/admin/users` (as `admin@example.test`), stop the process,
   start it again with the *same* env var, and confirm the same users are still there
   with `count()` unchanged (not re-seeded, not wiped) — this is the real proof `ddl-
   auto: update` + file-mode actually persists across a process restart, which is
   exactly the scenario that was broken. Show the actual before/after `GET
   /api/admin/users` output for both runs.
4. `git status --short` clean after commit.

## Loop note

Reviewer: the real risk here is `ddl-auto: update` behaving unexpectedly if the JPA
entity mapping ever changes shape in a future sprint (unlike `create-drop`, `update`
can leave orphaned columns/tables around, or fail outright on certain incompatible
schema changes) — not a concern for this sprint's diff itself (no entity changes here),
but worth a one-line note in the commit message so a future sprint touching
`UserEntity` knows `update` mode is now in play for this service.
