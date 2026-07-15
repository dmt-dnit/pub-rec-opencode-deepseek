# Sprint 22 live-apply runbook — hosting the 3 backend services on `dnit-vps`

**Status:** not yet executed. Written 2026-07-15 after Sprint 22's artifacts (systemd
units, Nginx vhosts, deploy workflows) cleared Codex review round 2 (`341554b`).

## Who does what, and why

Two things discovered while prepping this runbook genuinely require Dimitri, not the
coordinator:

1. **DNS.** `dnit.be` is managed at a third-party registrar (BNAMED.net/One.com per
   `DNIT_INFRASTRUCTURE.md`) — the coordinator has no API access or credentials for it.
2. **Root on `dnit-vps`.** The coordinator's SSH session (`administrator`) has **no
   general sudo** — confirmed directly (`sudo -n true` fails; only pre-scoped commands
   like `systemctl status petgiftshop-backend` work passwordless). That's a
   well-configured least-privilege setup, not a bug — but it means every step below that
   needs root (user creation, systemd install, Nginx edits, certbot, sudoers) needs
   Dimitri to either run it himself or grant broader access for this session.

**SSH private key handling:** the deploy keypair generated in Phase 3 should never be
pasted into a chat session with the coordinator — generate it locally, add the public
half to the VPS, and load the private half into the GitHub secret directly from your own
machine (`gh secret set` or the GitHub web UI). This keeps the private key out of any
conversation transcript.

---

## Phase 0 — DNS (Dimitri, via registrar control panel)

Add three A records, same IP as the existing `petshop.dnit.be` record:

| Host | Type | Value |
|---|---|---|
| `saga-auth.dnit.be` | A | `93.127.142.134` |
| `saga-orders.dnit.be` | A | `93.127.142.134` |
| `saga-inventory.dnit.be` | A | `93.127.142.134` |

Verify propagation before Phase 2 (certbot needs these to resolve):
```bash
getent hosts saga-auth.dnit.be saga-orders.dnit.be saga-inventory.dnit.be
```

## Phase 1 — VPS user, directories, systemd units (needs root)

Mirrors the existing `petgiftshop` system-account convention exactly (confirmed live:
`uid=999(petgiftshop) gid=987(petgiftshop)`, `nologin` shell, home = app dir).

```bash
# 1. System user (mirrors petgiftshop's convention: system account, nologin, home = app dir)
sudo useradd --system --home-dir /opt/pubrec --shell /usr/sbin/nologin --create-home pubrec

# 2. App directories
sudo mkdir -p /opt/pubrec/{auth,order,inventory}
sudo chown -R pubrec:pubrec /opt/pubrec

# 3. Env directory (root-owned, group-readable only — mirrors /etc/petgiftshop's 640/root:group pattern)
sudo mkdir -p /etc/pubrec
sudo chown root:pubrec /etc/pubrec
sudo chmod 750 /etc/pubrec
```

Populate the three real env files (content is exactly what round 1's `.env.example`
templates specify — nothing more, don't scope-creep here):

```bash
sudo tee /etc/pubrec/auth.env >/dev/null <<'EOF'
SERVER_PORT=9000
SPRING_H2_CONSOLE_ENABLED=false
EOF

sudo tee /etc/pubrec/order.env >/dev/null <<'EOF'
SERVER_PORT=8090
SPRING_H2_CONSOLE_ENABLED=false
EOF

sudo tee /etc/pubrec/inventory.env >/dev/null <<'EOF'
SERVER_PORT=8091
SPRING_H2_CONSOLE_ENABLED=false
EOF

sudo chown root:pubrec /etc/pubrec/*.env
sudo chmod 640 /etc/pubrec/*.env
```

Install the reviewed systemd units (copy from the repo checkout on the VPS, or `scp`
them from your machine — either way, byte-for-byte from `deploy/systemd/` at commit
`341554b`, don't hand-retype them):

```bash
sudo cp deploy/systemd/pubrec-auth.service /etc/systemd/system/
sudo cp deploy/systemd/pubrec-order.service /etc/systemd/system/
sudo cp deploy/systemd/pubrec-inventory.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable pubrec-auth pubrec-order pubrec-inventory
```

**Do not `systemctl start` yet** — no jar exists at `/opt/pubrec/*/backend.jar` until
Phase 4's first CI deploy. Starting now would just log a failed-start attempt; harmless,
but there's no reason to.

## Phase 2 — Nginx + TLS (needs root)

The committed vhost files (`deploy/nginx/saga-*.conf`) reference certs that don't exist
yet — installing them as-is would fail `nginx -t`. Bootstrap with certbot's Nginx plugin
(already installed, confirmed: `certbot plugins` lists `nginx`), then swap in the
reviewed final config.

For **each** of the three domains (`saga-auth.dnit.be` → port 9000,
`saga-orders.dnit.be` → port 8090, `saga-inventory.dnit.be` → port 8091):

```bash
# 1. Temporary HTTP-only bootstrap vhost (no SSL block yet — this is NOT the reviewed
#    artifact, it's a throwaway used only so certbot has something to attach to)
DOMAIN=saga-auth.dnit.be   # repeat block for saga-orders.dnit.be (8090), saga-inventory.dnit.be (8091)
PORT=9000
sudo tee /etc/nginx/sites-available/$DOMAIN >/dev/null <<EOF
server {
    listen 80;
    server_name $DOMAIN;
    location / {
        proxy_pass http://127.0.0.1:$PORT;
        proxy_set_header Host \$host;
    }
}
EOF
sudo ln -s /etc/nginx/sites-available/$DOMAIN /etc/nginx/sites-enabled/$DOMAIN
sudo nginx -t && sudo systemctl reload nginx

# 2. Obtain the cert — certbot's nginx plugin finds the bootstrap server block above
#    and handles the ACME HTTP-01 challenge automatically
sudo certbot --nginx -d $DOMAIN

# 3. Replace certbot's auto-edited config with the reviewed final artifact from the repo
#    (certbot's auto-edit doesn't know about the /ws path-scoping from round 2's fix —
#    the repo's version is the one that was actually security-reviewed)
sudo cp deploy/nginx/saga-auth.conf /etc/nginx/sites-available/$DOMAIN   # match filename to $DOMAIN each iteration
sudo nginx -t && sudo systemctl reload nginx
```

Repeat for `saga-orders.dnit.be` (port 8090, `deploy/nginx/saga-orders.conf`) and
`saga-inventory.dnit.be` (port 8091, `deploy/nginx/saga-inventory.conf`).

**Verify `nginx -t` passes after every reload** — a syntax error here affects the whole
box's Nginx, including Pet Giftshop and the file-upload API. Don't reload without
testing first.

## Phase 3 — Deploy credentials (Dimitri, on his own machine — keep the private key out of chat)

```bash
# On your own machine, not in this session:
ssh-keygen -t ed25519 -C "pubrec-deploy" -f ~/pubrec_deploy_key -N ""
```

Add the **public** half to `administrator`'s authorized_keys on `dnit-vps`:
```bash
ssh-copy-id -i ~/pubrec_deploy_key.pub administrator@93.127.142.134
```

Add sudoers scoping — least-privilege, exact-command allowlist (no wildcards), new file
so it doesn't touch the existing Pet Giftshop rule:
```bash
sudo visudo -f /etc/sudoers.d/pubrec-deploy
```
Contents:
```
Cmnd_Alias PUBREC_SYSTEMCTL = /usr/bin/systemctl stop pubrec-auth, /usr/bin/systemctl start pubrec-auth, /usr/bin/systemctl status pubrec-auth, /usr/bin/systemctl stop pubrec-order, /usr/bin/systemctl start pubrec-order, /usr/bin/systemctl status pubrec-order, /usr/bin/systemctl stop pubrec-inventory, /usr/bin/systemctl start pubrec-inventory, /usr/bin/systemctl status pubrec-inventory
Cmnd_Alias PUBREC_DEPLOY = /usr/bin/mv /tmp/backend.jar /opt/pubrec/auth/backend.jar, /usr/bin/mv /tmp/backend.jar /opt/pubrec/order/backend.jar, /usr/bin/mv /tmp/backend.jar /opt/pubrec/inventory/backend.jar, /usr/bin/chown pubrec\:pubrec /opt/pubrec/auth/backend.jar, /usr/bin/chown pubrec\:pubrec /opt/pubrec/order/backend.jar, /usr/bin/chown pubrec\:pubrec /opt/pubrec/inventory/backend.jar
administrator ALL=(root) NOPASSWD: PUBREC_SYSTEMCTL, PUBREC_DEPLOY
```
`visudo` validates syntax before saving — won't let you lock yourself out with a typo.

Create the GitHub secrets and `Production` environment (repo Settings → Environments,
then Secrets), on your own machine:
```bash
gh secret set VPS_HOST --env Production --body "93.127.142.134" --repo dmt-dnit/pub-rec-opencode-deepseek
gh secret set VPS_USER --env Production --body "administrator" --repo dmt-dnit/pub-rec-opencode-deepseek
base64 -w0 ~/pubrec_deploy_key | gh secret set VPS_SSH_KEY_B64 --env Production --repo dmt-dnit/pub-rec-opencode-deepseek
```

## Phase 4 — First deploy (either of us — this step itself isn't sensitive)

Once Phase 3's secrets exist, dispatch each workflow:
```bash
gh workflow run deploy-auth.yml --repo dmt-dnit/pub-rec-opencode-deepseek --ref main
gh workflow run deploy-order.yml --repo dmt-dnit/pub-rec-opencode-deepseek --ref main
gh workflow run deploy-inventory.yml --repo dmt-dnit/pub-rec-opencode-deepseek --ref main
```
Watch each run (`gh run watch`) — this is the actual first real deploy: builds the jar,
uploads it, stops/starts the systemd unit for the first time with a real jar in place.

## Phase 5 — Verification

```bash
# Services actually running
ssh dnit-vps 'sudo systemctl status pubrec-auth pubrec-order pubrec-inventory --no-pager'

# Public HTTPS reachable
curl -sI https://saga-auth.dnit.be/oauth2/jwks
curl -sI https://saga-orders.dnit.be/actuator/health
curl -sI https://saga-inventory.dnit.be/actuator/health

# H2 console is NOT reachable publicly (the whole point of the round-1 finding)
curl -sI https://saga-auth.dnit.be/h2-console    # expect 404/403, not 200
```
WebSocket smoke test (`/ws` STOMP handshake) is easiest verified from a real browser
against `order-ui`/`inventory-ui` once Phase 4 (Vercel) is also done — not required to
close this runbook, but worth a manual check when convenient.

## What this runbook deliberately doesn't cover
Google OAuth real credentials (Track C Phase 6, gated on the separate secrets-policy
decision), CORS for the Vercel frontends (Track C Phase 4), promoting the Snyk gate
(Phase 5 of the roadmap). This runbook is scoped to exactly what Sprint 22 built.
