# V-2 — Nginx vhosts for the 3 subdomains

**Sprint:** 22. **Type:** deploy config artifacts (new files, no existing code touched).
**Implementer:** opencode+DeepSeek (worktree).

## Why this exists
Public HTTPS entry points for the three services, reverse-proxying to their local ports
on `dnit-vps`, mirroring the proven Pet Giftshop vhost pattern. This task produces the
three vhost files as repo artifacts — it does **not** install anything on the live Nginx
config (see "out of scope" below).

## Reference template (fetched from `dmt-dnit/petgiftshop`, use this exact shape)

```nginx
server {
    listen 80;
    listen [::]:80;
    server_name staging-api.petshop.dnit.be;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name staging-api.petshop.dnit.be;

    # Replace with your staging certificate paths (for example via certbot).
    ssl_certificate /etc/letsencrypt/live/staging-api.petshop.dnit.be/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/staging-api.petshop.dnit.be/privkey.pem;

    client_max_body_size 25m;

    location / {
        proxy_pass http://127.0.0.1:8081;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 90s;
    }
}
```

## A deliberate deviation from the template: WebSocket support

`order-service` and `inventory-service` expose a STOMP-over-WebSocket endpoint at `/ws`
(confirmed: `WebSocketConfig.java` → `registry.addEndpoint("/ws")`;
`SecurityConfig.java` permits `/ws/**` and `/api/ws/**`). Pet Giftshop's template has no
WebSocket traffic, so it doesn't need upgrade headers — **this project does**. For the
`order`/`inventory` vhosts only, the `location /` block needs, in addition to the
template's existing proxy headers:

```nginx
proxy_set_header Upgrade $http_upgrade;
proxy_set_header Connection "upgrade";
```

`auth-server` has no WebSocket traffic — its vhost follows the template unmodified
(aside from the substitutions below).

## Deliverables

Create `deploy/nginx/` in the repo root with three files, following the template shape
exactly (HTTP→HTTPS redirect block + TLS server block, same header set, same
`client_max_body_size`/`proxy_read_timeout`), substituting per this table:

| File | `server_name` | `proxy_pass` target | WebSocket headers? |
|---|---|---|---|
| `deploy/nginx/saga-auth.conf` | `saga-auth.dnit.be` | `http://127.0.0.1:9000` | No |
| `deploy/nginx/saga-orders.conf` | `saga-orders.dnit.be` | `http://127.0.0.1:8090` | **Yes** |
| `deploy/nginx/saga-inventory.conf` | `saga-inventory.dnit.be` | `http://127.0.0.1:8091` | **Yes** |

`ssl_certificate`/`ssl_certificate_key` paths follow the same
`/etc/letsencrypt/live/<server_name>/{fullchain,privkey}.pem` shape as the template,
substituting each vhost's own `server_name`.

## Explicitly out of scope (do not do this)
- Do not SSH into `dnit-vps`.
- Do not install these vhost files into any live Nginx `sites-available`/`sites-enabled`
  path.
- Do not run `certbot` or request any real TLS certificate.
- These are artifacts for review only — the live-apply step is separately scheduled with
  Dimitri in the loop.

## Acceptance criteria (observable outcomes)
1. `ls deploy/nginx/*.conf` shows exactly the three files named above.
2. Each file's content matches the template shape (HTTP redirect block + TLS server
   block, same proxy header set) with only the substitutions from the table.
3. `saga-orders.conf` and `saga-inventory.conf` — and **only** those two — additionally
   carry the WebSocket upgrade headers; `saga-auth.conf` does not (state explicitly in
   your report that you checked this asymmetry deliberately, not by omission).
4. Run `nginx -t` against each file if an Nginx binary is available in this environment
   to catch syntax errors before commit; if not available, say so explicitly and instead
   visually verify brace-matching/statement-termination by reading the file back.
5. `git status --short` shows only the new `deploy/nginx/` files — no existing source
   touched, no accidental live-apply commands run.
