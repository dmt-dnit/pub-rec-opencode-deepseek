# Sprint 22 round 3 — fix shared remote temp-path race condition in deploy workflows

**Sprint:** 22 round 3. **Type:** CI workflow config, same files as V-3.
**Implementer:** opencode+DeepSeek (worktree).
**Found:** live, during Phase 4 of the live-apply session (2026-07-15) — not caught by
Codex's review, which explicitly did not run a live deploy.

## What happened

All three `.github/workflows/deploy-{auth,order,inventory}.yml` files use **identical**
remote temp paths, copied verbatim from the Pet Giftshop reference template (which only
ever deploys one app, so the collision never mattered there):

```yaml
REMOTE_TMP_JAR: /tmp/backend.jar
REMOTE_DEPLOY_SCRIPT: /tmp/deploy-backend.sh
```

Dispatching all three within seconds of each other caused a real collision on the
shared VPS: two of the three deploys failed with "Jar not found: /tmp/backend.jar"
(their jar got clobbered or removed by another workflow's `scp`/`mv` before their own
deploy script ran). Worse: the "successful" auth-server deploy actually moved
**inventory-service's** jar into `/opt/pubrec/auth/backend.jar` and started it under the
`pubrec-auth` systemd unit — confirmed by the running process's own structured log
output self-identifying as `"service":{"name":"inventory-service"}`. Caught and stopped
immediately (`pubrec-auth` is inactive again; no other service on the box was affected —
this stayed entirely within the new `pubrec-*` units).

## Fix

Give each workflow its own unique remote temp paths — simplest, least error-prone fix
is to include the service name in the path, matching each workflow's own
`BACKEND_SERVICE` value:

| File | `REMOTE_TMP_JAR` | `REMOTE_DEPLOY_SCRIPT` |
|---|---|---|
| `.github/workflows/deploy-auth.yml` | `/tmp/backend-auth.jar` | `/tmp/deploy-backend-auth.sh` |
| `.github/workflows/deploy-order.yml` | `/tmp/backend-order.jar` | `/tmp/deploy-backend-order.sh` |
| `.github/workflows/deploy-inventory.yml` | `/tmp/backend-inventory.jar` | `/tmp/deploy-backend-inventory.sh` |

Only the `REMOTE_TMP_JAR`/`REMOTE_DEPLOY_SCRIPT` env values change — no other logic in
the workflows or `deploy/scripts/deploy-backend.sh` needs to change (the script is
already fully parametrized via env vars, it doesn't care what the paths are named).

**Also update `docs/backlog/sprint-22-live-apply-runbook.md`'s sudoers `PUBREC_DEPLOY`
Cmnd_Alias** — it currently allowlists the shared `/tmp/backend.jar` source path for all
three `mv` commands:
```
/usr/bin/mv /tmp/backend.jar /opt/pubrec/auth/backend.jar, /usr/bin/mv /tmp/backend.jar /opt/pubrec/order/backend.jar, /usr/bin/mv /tmp/backend.jar /opt/pubrec/inventory/backend.jar
```
This must change to match the new per-service source paths exactly (sudoers matches
exact arguments — same class of bug as the `--no-pager` mismatch found earlier this
sprint):
```
/usr/bin/mv /tmp/backend-auth.jar /opt/pubrec/auth/backend.jar, /usr/bin/mv /tmp/backend-order.jar /opt/pubrec/order/backend.jar, /usr/bin/mv /tmp/backend-inventory.jar /opt/pubrec/inventory/backend.jar
```
The already-applied live sudoers file on `dnit-vps` will need updating to match once
this lands — that's a live-apply step for Dimitri (needs root), not part of this task.

## Acceptance criteria (observable outcomes)

1. `grep -n "REMOTE_TMP_JAR\|REMOTE_DEPLOY_SCRIPT" .github/workflows/deploy-*.yml` shows
   the three unique per-service paths from the table above, no two files sharing a path.
2. YAML re-validated for all three workflows (show `yaml.safe_load` output).
3. `deploy/scripts/deploy-backend.sh` is unchanged (it's already generic via env vars —
   confirm via `git diff` that this file has zero changes).
4. `docs/backlog/sprint-22-live-apply-runbook.md`'s sudoers block updated to the
   corrected per-service `mv` source paths.
5. `git status --short` shows only the 3 workflow files + the runbook doc — no other
   scope creep.

## Related
This is the third round of fixes on Sprint 22's deploy artifacts (round 1: SSH host-key
pinning + Nginx `/ws` scoping, both genuine Codex findings; round 2 cleared Codex
review). This round is a live-ops finding, not a Codex finding — Codex's review is not
being re-requested for this specific fix since it's config-only and was already caught
and will be verified by the coordinator re-running a real (sequential) deploy
immediately after this lands, which is a stronger signal than another artifact review.
