# Sprint 29 — Handoff to Codex

**Coordinator:** Claude Code. **Date:** 2026-07-17.
**Task:** persist `auth-server`'s H2 database across restarts (file mode + `ddl-auto` fix) so user accounts, roles, and approvals survive redeploys.
**Implementer:** opencode+DeepSeek, isolated worktree.
**Merge-gate tier:** coordinator-supervised, pushed direct to `main` per the Sprint 21 policy.
Review-Target-Commit: cabc84c

## Why this sprint exists

The very first backend redeploy after Sprint 28 wiped Dimitri's registered account and
admin role — `auth-server` runs H2 in pure in-memory mode, so any JVM restart loses
everything. The Track C roadmap's Phase 8 ("real DB instead of H2," 2026-07-16) was
explicitly deferred as "decide when we get there" — that phase implied a bigger
migration (Postgres, likely all three services). Dimitri chose a smaller, faster fix
instead: keep the existing embedded H2 engine, switch it to file-backed persistence
for `auth-server` only. `order-service`/`inventory-service` stay in-memory —
resetting orders/inventory between demo sessions hasn't been flagged as a problem.

## Root cause — two independent things, both needed fixing

Found by reading the actual config, not assumed from the symptom:
1. `spring.datasource.url: jdbc:h2:mem:authdb` — in-memory by definition.
2. `spring.jpa.hibernate.ddl-auto: create-drop` — **drops all tables on shutdown and
   recreates them empty on startup**, independent of the storage backend. Fixing only
   #1 without #2 would still wipe everything on every restart.

The deploy script itself was cleared of blame by reading it directly —
`deploy/scripts/deploy-backend.sh` only stops the service, replaces the jar, and
restarts; it never touches any other file or directory.

## What was done (`cabc84c`)

- `auth-server/src/main/resources/application.yml`: `ddl-auto: create-drop` → `update`.
  `datasource.url` **deliberately left as `jdbc:h2:mem:authdb`** — that stays correct
  for local dev/CI, where a hardcoded VPS file path wouldn't exist.
- `deploy/systemd/env-examples/auth.env.example`: documents
  `SPRING_DATASOURCE_URL=jdbc:h2:file:/opt/pubrec/auth/data/authdb` as the production
  override — Spring Boot's relaxed environment-variable binding means this needs **no
  code change** to take effect; it only needs to actually be added to the real
  `/etc/pubrec/auth.env` on the VPS (a manual one-time step, see below).

## Coordinator verification — the real proof, not just a green build

- `shared-model`: `./mvnw clean install` — BUILD SUCCESS.
- `auth-server`: `./mvnw clean verify` — BUILD SUCCESS (no tests, pre-existing).
- **A real two-process restart test**, since there's no automated test for this and a
  green build alone wouldn't prove persistence:
  1. Started `auth-server` locally with `SPRING_DATASOURCE_URL=jdbc:h2:file:/tmp/
     sprint29-test-authdb` (disposable test path). Confirmed the 3 seeded users via
     `GET /api/admin/users`.
  2. Registered a 4th test user (`sprint29-persist-test@example.test`, `PENDING`).
  3. **Killed the process entirely**, confirmed it was gone, then started a fresh
     process with the exact same `SPRING_DATASOURCE_URL`.
  4. `GET /api/admin/users` on the new process showed **all 4 users, unchanged, same
     IDs, same statuses** — not re-seeded to 3, not wiped. `DataSeeder`'s log line
     (`--- SEEDED USERS ---`) did **not** print on the second run, confirming its
     existing `count() > 0` idempotency guard correctly detected the persisted data.

## A real environment gotcha found during this verification, worth documenting

The bare `java` command in this coordinator's WSL environment resolves to
`/home/dnit/.local/bin/java`, which turned out to be a **Windows-interop shim** — it
silently launches Windows' own Java 25 installation (confirmed via the startup log
showing `Java 25` and `C:\projects\...` paths), completely ignoring `JAVA_HOME` and not
forwarding environment variables across the WSL/Windows boundary. This produced
confusing, hard-to-diagnose failures (connection refused on the expected port, wrong
Java version, in-memory URL despite setting `SPRING_DATASOURCE_URL`) before being
traced to its actual cause. `./mvnw` was never affected (it resolves the JDK correctly
via its own wrapper), only bare `java -jar ...` invocations. Fixed by explicitly
invoking `/usr/lib/jvm/java-21-openjdk-amd64/bin/java` for this verification; noted here
so a future session doesn't lose time re-discovering it.

## Not done in this sprint — Dimitri's one-time manual action

**The coordinator has no sudo access on `dnit-vps`** beyond a narrow exact-command
allowlist that doesn't cover this (confirmed directly — `sudo -n true` fails). Live
provisioning is a manual step, same split as every other live-apply in this Track:

```bash
sudo mkdir -p /opt/pubrec/auth/data
sudo chown pubrec:pubrec /opt/pubrec/auth/data
echo "SPRING_DATASOURCE_URL=jdbc:h2:file:/opt/pubrec/auth/data/authdb" | sudo tee -a /etc/pubrec/auth.env
```
After that, a redeploy of `auth-server` (`deploy-auth.yml`) picks up the file-backed
config. **This one redeploy will still wipe current live data one final time** (moving
from in-memory to file-backed can't preserve data that was never persisted) — after
that, state survives every future restart.

## Loop note

Reviewer: the real risk with `ddl-auto: update` (vs. the previous `create-drop`) is that
it can leave orphaned columns/tables around or fail on certain incompatible schema
changes if a future sprint changes `UserEntity`'s shape — not a concern for this
sprint's diff (no entity changes here), but worth remembering next time that entity is
touched.
