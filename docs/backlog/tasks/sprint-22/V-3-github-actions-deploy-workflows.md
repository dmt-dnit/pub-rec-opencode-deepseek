# V-3 — GitHub Actions deploy workflows (3)

**Sprint:** 22. **Type:** CI workflow artifacts (new files) + one shared deploy script.
**Implementer:** opencode+DeepSeek (worktree).

## Why this exists
Manual-dispatch deploy pipelines for the three services, mirroring the proven Pet
Giftshop deploy workflow shape exactly. This task produces the workflow + script
artifacts as repo files — it does **not** create any GitHub secrets/Environment or run
a real deploy (see "out of scope" below).

## Reference templates (fetched from `dmt-dnit/petgiftshop`, use this exact shape)

`.github/workflows/deploy-backend-production.yml`:
```yaml
name: Deploy Backend (Production)

on:
  workflow_dispatch:
    inputs:
      git_ref:
        description: "Branch or tag to deploy"
        required: true
        default: "master"

jobs:
  deploy:
    runs-on: ubuntu-latest
    environment: Production
    env:
      PROD_VPS_HOST: ${{ secrets.PROD_VPS_HOST }}
      PROD_VPS_USER: ${{ secrets.PROD_VPS_USER }}
      PROD_VPS_SSH_KEY: ${{ secrets.PROD_VPS_SSH_KEY }}
      PROD_VPS_SSH_KEY_B64: ${{ secrets.PROD_VPS_SSH_KEY_B64 }}
      PROD_BACKEND_SERVICE: petgiftshop-backend
      PROD_REMOTE_TMP_JAR: /tmp/backend.jar
      PROD_REMOTE_DEPLOY_SCRIPT: /tmp/deploy-backend.sh
      PROD_REMOTE_JAR_DEST: /opt/petgiftshop/backend.jar
      PROD_APP_USER: petgiftshop
    steps:
      - name: Validate required secrets
        run: |
          for name in PROD_VPS_HOST PROD_VPS_USER; do
            if [ -z "${!name:-}" ]; then
              echo "Missing required secret: $name (environment: Production)." >&2
              exit 1
            fi
          done
          if [ -z "${PROD_VPS_SSH_KEY:-}" ] && [ -z "${PROD_VPS_SSH_KEY_B64:-}" ]; then
            echo "Missing required secret: set PROD_VPS_SSH_KEY or PROD_VPS_SSH_KEY_B64 (environment: Production)." >&2
            exit 1
          fi

      - uses: actions/checkout@v4
        with:
          ref: ${{ inputs.git_ref }}

      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"

      - name: Build backend jar
        working-directory: backend
        run: ./mvnw -DskipTests clean package

      - name: Prepare SSH
        run: |
          mkdir -p ~/.ssh
          chmod 700 ~/.ssh
          if [ -n "${PROD_VPS_SSH_KEY_B64:-}" ]; then
            printf '%s' "$PROD_VPS_SSH_KEY_B64" | tr -d '\r\n\t ' | base64 -d > ~/.ssh/id_ed25519
          else
            printf '%s' "$PROD_VPS_SSH_KEY" | tr -d '\r' > ~/.ssh/id_ed25519
            if grep -q '\\n' ~/.ssh/id_ed25519; then
              perl -0pi -e 's/\\n/\n/g' ~/.ssh/id_ed25519
            fi
          fi
          chmod 600 ~/.ssh/id_ed25519
          ssh-keygen -y -f ~/.ssh/id_ed25519 >/dev/null
          ssh-keyscan -H "$PROD_VPS_HOST" >> ~/.ssh/known_hosts

      - name: Upload deploy script
        run: |
          scp scripts/deploy-backend.sh \
            "$PROD_VPS_USER@$PROD_VPS_HOST:$PROD_REMOTE_DEPLOY_SCRIPT"

      - name: Upload backend jar
        run: |
          scp backend/target/backend-0.0.1-SNAPSHOT.jar \
            "$PROD_VPS_USER@$PROD_VPS_HOST:$PROD_REMOTE_TMP_JAR"

      - name: Restart production backend service
        run: |
          ssh "$PROD_VPS_USER@$PROD_VPS_HOST" \
            "chmod +x '$PROD_REMOTE_DEPLOY_SCRIPT' && \
             SERVICE='$PROD_BACKEND_SERVICE' \
             JAR_SRC='$PROD_REMOTE_TMP_JAR' \
             JAR_DEST='$PROD_REMOTE_JAR_DEST' \
             APP_USER='$PROD_APP_USER' \
             bash '$PROD_REMOTE_DEPLOY_SCRIPT'"
```

`scripts/deploy-backend.sh` (the remote-side script it uploads and runs):
```bash
#!/usr/bin/env bash
set -euo pipefail

SERVICE="${SERVICE:-petgiftshop-backend}"
JAR_SRC="${JAR_SRC:-/tmp/backend.jar}"
JAR_DEST="${JAR_DEST:-/opt/petgiftshop/backend.jar}"
APP_USER="${APP_USER:-petgiftshop}"

if [[ ! -f "$JAR_SRC" ]]; then
  echo "Jar not found: $JAR_SRC" >&2
  exit 1
fi

sudo systemctl stop "$SERVICE"
sudo mv "$JAR_SRC" "$JAR_DEST"
sudo chown "$APP_USER:$APP_USER" "$JAR_DEST"
sudo systemctl start "$SERVICE"
sudo systemctl status "$SERVICE" --no-pager
```

## Deliverables

### 1. One shared, generalized deploy script: `deploy/scripts/deploy-backend.sh`
Same shape as the Pet Giftshop script above (already fully parametrized via env vars —
no change needed to its logic, just relocate it under `deploy/scripts/` to sit alongside
this sprint's other new `deploy/` artifacts, and update its default fallback values from
`petgiftshop-backend`/`petgiftshop` to something generic since this script now serves
three different services, e.g. defaults matching the auth-server case with a comment
noting the other two override them via env).

### 2. Three workflow files, each following the reference shape exactly
(`workflow_dispatch` only — no push trigger, this is a demo app where deploys should be
deliberate, matching the "manual dispatch to start" decision in the roadmap), each in
its own file, secrets/env substituted per this table — **reuse one shared secret set**
(`VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`/`VPS_SSH_KEY_B64`) across all three workflows
since they target the same VPS, under one GitHub Environment named `Production` (matches
Pet Giftshop's naming for consistency across Dimitri's projects, per his standardization
goal — flag in your report if you think a different Environment name fits better, but
default to `Production` unless told otherwise):

| File | `BACKEND_SERVICE` | `REMOTE_JAR_DEST` | `APP_USER` | Maven module dir | jar filename pattern |
|---|---|---|---|---|---|
| `.github/workflows/deploy-auth.yml` | `pubrec-auth` | `/opt/pubrec/auth/backend.jar` | `pubrec` | `auth-server` | check the actual `artifactId`/`version` in `auth-server/pom.xml` — don't guess |
| `.github/workflows/deploy-order.yml` | `pubrec-order` | `/opt/pubrec/order/backend.jar` | `pubrec` | `order-service` | same, check `order-service/pom.xml` |
| `.github/workflows/deploy-inventory.yml` | `pubrec-inventory` | `/opt/pubrec/inventory/backend.jar` | `pubrec` | `inventory-service` | same, check `inventory-service/pom.xml` |

**Important build-order difference from Pet Giftshop:** this repo has a `shared-model`
dependency that must be `mvnw clean install`-ed before any of the three services will
build (see `CLAUDE.md`'s "Java services (Maven)" section) — the Pet Giftshop template
has no such shared-lib step. Each workflow needs a "build shared-model" step before its
"build backend jar" step, inline (per `[[feedback-ci-maven-sharedlib-inline]]` — don't
try to pass `~/.m2` between jobs/steps via upload-artifact, build it inline, it's cheap).

Point each workflow's `scp`/`ssh` steps at the relocated `deploy/scripts/deploy-backend.sh`
path from deliverable 1, not the old `scripts/deploy-backend.sh` path from the reference
template (that path was Pet Giftshop-specific).

## Explicitly out of scope (do not do this)
- Do not create the GitHub repo secrets or the `Production` Environment — that's a
  GitHub repo-settings action for Dimitri, not something this task does or can do.
- Do not actually run/dispatch any of these workflows.
- Do not SSH into `dnit-vps`.
- These are artifacts for review only — the live-apply step (including secret creation)
  is separately scheduled with Dimitri in the loop.

## Acceptance criteria (observable outcomes)
1. `ls .github/workflows/deploy-{auth,order,inventory}.yml` shows exactly the three
   files.
2. `ls deploy/scripts/deploy-backend.sh` exists, `git ls-files -s` shows mode `100755`
   (executable bit — verify this explicitly, don't assume `git add` set it; see
   `[[feedback-ci-check-script-exec-bit]]`, this exact class of bug already cost a sprint
   round once).
3. Each workflow YAML is well-formed (`python3 -c "import yaml,sys; yaml.safe_load(open('...'))"`
   for each file, show actual output) and correctly includes the shared-model inline
   build step before its own module's build step.
4. `actionlint` if available (show output); if not available, say so explicitly.
5. Confirm via `grep -n artifactId -A1 -B3 */pom.xml` (or equivalent) what each
   service's actual built jar filename will be, and show that the workflow's `scp`
   source path matches it exactly — a filename mismatch here fails silently until a real
   dispatch, so get this right by checking, not guessing.
6. `git status --short` shows only the new files — no existing workflow (`ci.yml`)
   touched, no accidental live-apply commands run, no secrets committed anywhere.
