# V-1 — systemd units for the 3 services

**Sprint:** 22. **Type:** deploy config artifacts (new files, no existing code touched).
**Implementer:** opencode+DeepSeek (worktree).

## Why this exists
Backend hosting on `dnit-vps` mirrors the proven Pet Giftshop pattern: each Spring
service runs as its own systemd unit, jar dropped at a fixed path, config supplied via
an `EnvironmentFile`. This task produces the three unit files as repo artifacts — it
does **not** install anything on the live VPS (see "out of scope" below).

## Reference template (fetched from `dmt-dnit/petgiftshop`, use this exact shape)

```ini
[Unit]
Description=Pet Giftshop Backend (Staging)
After=network.target

[Service]
Type=simple
User=petgiftshop
Group=petgiftshop
WorkingDirectory=/opt/petgiftshop/staging
EnvironmentFile=/etc/petgiftshop/backend-staging.env
ExecStart=/usr/bin/java -jar /opt/petgiftshop/staging/backend.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=5
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

## Deliverables

Create `deploy/systemd/` in the repo root with three files, following the template
above exactly (same `[Unit]`/`[Service]`/`[Install]` shape, same `Restart=on-failure`/
`RestartSec=5`/`SuccessExitStatus=143`/journal logging), substituting per this table:

| File | `Description` | `User`/`Group` | `WorkingDirectory` | `EnvironmentFile` | `ExecStart` jar path |
|---|---|---|---|---|---|
| `deploy/systemd/pubrec-auth.service` | `Pub-Rec Demo — auth-server` | `pubrec` | `/opt/pubrec/auth` | `/etc/pubrec/auth.env` | `/opt/pubrec/auth/backend.jar` |
| `deploy/systemd/pubrec-order.service` | `Pub-Rec Demo — order-service` | `pubrec` | `/opt/pubrec/order` | `/etc/pubrec/order.env` | `/opt/pubrec/order/backend.jar` |
| `deploy/systemd/pubrec-inventory.service` | `Pub-Rec Demo — inventory-service` | `pubrec` | `/opt/pubrec/inventory` | `/etc/pubrec/inventory.env` | `/opt/pubrec/inventory/backend.jar` |

One shared `pubrec` system user across all three units (this project's three services
share isolation from *other* hosted projects on the box, not from each other).

### Also create: three example `.env` templates (NOT the real ones — these are checked
into the repo as documentation of what each `EnvironmentFile` must contain; the real
files live only on the VPS, outside git, per `docs/security/secrets-and-test-data.md`'s
no-real-secrets rule)

`deploy/systemd/env-examples/auth.env.example`:
```
SERVER_PORT=9000
SPRING_H2_CONSOLE_ENABLED=false
```

`deploy/systemd/env-examples/order.env.example`:
```
SERVER_PORT=8090
SPRING_H2_CONSOLE_ENABLED=false
```

`deploy/systemd/env-examples/inventory.env.example`:
```
SERVER_PORT=8091
SPRING_H2_CONSOLE_ENABLED=false
```

**Why `SPRING_H2_CONSOLE_ENABLED=false` matters:** all three services currently ship
`spring.h2.console.enabled: true` unconditionally in `src/main/resources/application.yml`
(verify this yourself — `grep -n "h2:" -A2 */src/main/resources/application.yml`). That's
acceptable on `localhost` for local dev/demo but must not be reachable on a public
subdomain. Spring Boot's environment-variable relaxed binding overrides the YAML default
without any source change — `SPRING_H2_CONSOLE_ENABLED` (env var) maps to
`spring.h2.console.enabled` (property). Confirm this mapping is correct by reading
Spring Boot's relaxed-binding rules, don't just assume the name — get this wrong and the
console stays exposed.

## Explicitly out of scope (do not do this)
- Do not SSH into `dnit-vps`.
- Do not create the `pubrec` user, `/opt/pubrec/*` directories, or real `/etc/pubrec/*.env`
  files anywhere, including on the coordinator's machine.
- Do not install these unit files into any live `/etc/systemd/system/` path.
- These are artifacts for review only — the live-apply step is separately scheduled with
  Dimitri in the loop.

## Acceptance criteria (observable outcomes)
1. `ls deploy/systemd/*.service` shows exactly the three files named above.
2. Each file's content matches the template shape (diff against the fetched Pet
   Giftshop template structurally — same section order, same restart/logging directives)
   with only the substitutions from the table above.
3. `ls deploy/systemd/env-examples/*.env.example` shows exactly the three example files,
   each containing the correct port and `SPRING_H2_CONSOLE_ENABLED=false`.
4. Confirm via `grep` that all three services' `application.yml` currently has
   `h2.console.enabled: true` unconditionally (this is the finding that justifies the
   env override) — show that grep output in your report.
5. `git status --short` shows only the new `deploy/systemd/` files — no existing source
   touched, no accidental live-apply commands run.
