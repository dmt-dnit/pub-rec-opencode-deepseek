# V-3 — GitHub Actions Vercel deploy-hook workflows

**Sprint:** 26. **Track:** C — go-live, Phase 4. **Scope:** two new workflow files,
`.github/workflows/` only. Artifacts only — no secret is created and no workflow is
dispatched as part of this task (see "Out of scope").

## Why

Once V-1 lands, `order-ui`/`inventory-ui` are buildable Vercel-ready static SPAs. Vercel
projects deploy from a **Deploy Hook** URL (a plain webhook Vercel generates per project)
rather than needing Vercel's own GitHub App wired into this repo — this is the same
pattern Dimitri already uses for Pet Giftshop's frontend, fetched live for reference
(`gh api repos/dmt-dnit/petgiftshop/contents/.github/workflows/vercel-deploy.yml`):

```yaml
name: Deploy Frontend (Vercel Hook)

on:
  push:
    branches:
      - master
  workflow_dispatch:

jobs:
  deploy:
    runs-on: ubuntu-latest
    environment: Production
    env:
      VERCEL_DEPLOY_HOOK_URL: ${{ secrets.VERCEL_DEPLOY_HOOK_URL }}
    steps:
      - name: Validate deploy hook secret
        run: |
          if [ -z "${VERCEL_DEPLOY_HOOK_URL:-}" ]; then
            echo "Missing required secret: VERCEL_DEPLOY_HOOK_URL in environment 'Production'." >&2
            exit 1
          fi
      - name: Trigger Vercel deploy hook
        run: curl -fsS -X POST "$VERCEL_DEPLOY_HOOK_URL"
```

## Deliberate deviation from the Pet Giftshop reference

Pet Giftshop's workflow triggers on every push to `master`. **This repo's existing
backend deploy workflows** (`deploy-auth.yml`, `deploy-order.yml`,
`deploy-inventory.yml`) instead use `workflow_dispatch` only — manual trigger, no
push-triggered auto-deploy — because this is a demo app where Dimitri wants to control
exactly when the public instance changes (decided explicitly in Sprint 22 scoping).
**Follow that same convention here**, not the Pet Giftshop push-trigger: drop the `on:
push:` block entirely, keep only `workflow_dispatch`.

## What to create

Two new files, one per UI, each with its own deploy-hook secret name (this is a
monorepo — a single shared secret would make both workflows deploy the same project):

`.github/workflows/deploy-order-ui.yml`:
```yaml
name: Deploy order-ui (Vercel Hook)

on:
  workflow_dispatch:

jobs:
  deploy:
    runs-on: ubuntu-latest
    environment: Production
    env:
      VERCEL_DEPLOY_HOOK_URL_ORDER_UI: ${{ secrets.VERCEL_DEPLOY_HOOK_URL_ORDER_UI }}
    steps:
      - name: Validate deploy hook secret
        run: |
          if [ -z "${VERCEL_DEPLOY_HOOK_URL_ORDER_UI:-}" ]; then
            echo "Missing required secret: VERCEL_DEPLOY_HOOK_URL_ORDER_UI in environment 'Production'." >&2
            exit 1
          fi
      - name: Trigger Vercel deploy hook (order-ui)
        run: curl -fsS -X POST "$VERCEL_DEPLOY_HOOK_URL_ORDER_UI"
```

`.github/workflows/deploy-inventory-ui.yml`: identical shape, substitute
`VERCEL_DEPLOY_HOOK_URL_INVENTORY_UI` and the matching step names/comments.

## Explicitly out of scope

- Creating the actual Vercel Deploy Hooks in the Vercel dashboard, or adding the
  `VERCEL_DEPLOY_HOOK_URL_ORDER_UI`/`VERCEL_DEPLOY_HOOK_URL_INVENTORY_UI` secrets to the
  GitHub `Production` environment — that's Dimitri's dashboard action, same
  artifact-vs-live-apply split used for the Phase 3 systemd/Nginx work.
- Dispatching either workflow.
- Any change to the existing backend deploy workflows.

## Acceptance criteria (show real output, don't assert "Pass")

1. Both files are valid YAML — show `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/deploy-order-ui.yml'))"` (and same for `deploy-inventory-ui.yml`) succeeding, or equivalent.
2. Confirm each workflow uses `workflow_dispatch` **only** — no `on: push` block. Show
   the relevant `on:` section of both files in your report.
3. Confirm the two secret names are distinct (`_ORDER_UI` vs `_INVENTORY_UI` suffix) —
   a copy-paste that leaves both referencing the same secret name would make both
   workflows deploy whichever project that one hook belongs to.
4. `git status --short` clean after commit.
