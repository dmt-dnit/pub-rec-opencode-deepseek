# Sprint 22 round 2 — fix Codex's two REJECT findings

**Sprint:** 22 round 2. **Type:** deploy config artifacts, same files as round 1.
**Implementer:** opencode+DeepSeek (worktree).
**Addresses:** `reviews/sprint-22-track-c-review.md`, verdict REJECT (verified fresh via
`scripts/verify-review.sh 22`, exit 3 — genuine blocker, both findings independently
confirmed by the coordinator reading the actual files before this brief was written).

## Finding 1 — SSH host key not pinned (MITM risk on the deploy path)

All three `.github/workflows/deploy-{auth,order,inventory}.yml` files (identical logic,
copied from the fetched Pet Giftshop template — this is a latent gap in the reference
pattern itself, not something the implementer introduced) have:

```yaml
ssh-keyscan -H "$VPS_HOST" >> ~/.ssh/known_hosts
```

This blindly trusts whatever host key the network returns at CI runtime — no
verification against a known-good value. A network-level attacker positioned between
the GitHub Actions runner and `dnit-vps` could present their own host key and the
deploy would trust it silently.

**Fix: pin the real host key instead of scanning it at runtime.** The coordinator
already captured it from an established, trusted SSH session this session
(`ssh-keyscan -H 93.127.142.134` run from a shell that had already verified connectivity
to `dnit-vps` via the SSH alias configured in this environment):

```
|1|r3Xoj8pILkgDpcI9MWIumPIV7Ck=|IHtIkczOtwJmEoEifuE+AibEiCM= ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABgQDIvK9brBBeeicJP7TiLoiqpLFM9fOl+0gzGbxOMBLirp465g/0n+/5nFIZJXhSovTcig9SkBUFpXpfDIJ1pvDm1WjfpDRYZdP3KYjjWYTovBzKQmmQI8kdPcC4Pah2IA0kwVapXRXw1SHGmFV8xHAPEm5U5edceBdM5oFqbbc6xpAkYcxhq89j7njd7E8x1o3e2RFhTxGTYo+W8Rb+L9xjJlNsDwq6uFp+ApolKcQiyzHDxh5w48cBaADcLYcAPR69AVVC5YmIkY98bLxMb3ZZ4eFbr26iaKgNMgkTGz12QH0p+FZa0lzC4Vq2nFJ8LLgASDWk1WbcvUfGtiHJIC02kbUSv+2RAbckxL3sZSEMKCeSc29q4VuYF0tXRJxzZ3ZQuWqBlG+dHCY/Sw7mGCfcVml/AfVS9uW/Y9Nl01bLYs2M0v2HSu1LVrkshr0GgESOWqhg1o4qUCvU8yyByw8rroRdvRLyY1b6fW94zLOXbNB2SbnDk/mg1emAk5nuHxE=
|1|dLLvdltaVzEyuFuPTLJEhgfO15Q=|V5lPSXV4WAz13U0VM7XRhzqQS7c= ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBIcP+zySSkBNPAnH+9G4Nb1vCbaG+shD1lr2LB7JZas+OnQohZ00lw1nNwCWBOMIjsObxH1ykEnxuFkIYVM6i9I=
|1|Z5sNsoNFLfLWb+sLGv7/XMNBD6Y=|6oOcrKVhsUXadcohMuvriSNoTQY= ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAICf+oYlQw9sF9O1QbXxAAWlt/cZ8qLl7tgMnfgU/81UU
```

SSH host keys are **public by design** — the security property comes from verifying
them out-of-band once and pinning that value, not from keeping them secret. It is
correct and standard practice to commit this to the repo (unlike the SSH *private* key,
which stays a GitHub secret and never touches the repo).

Deliverables:
1. Create `deploy/ssh/dnit-vps.known_hosts` containing exactly the three lines above,
   verbatim.
2. In all three `deploy-{auth,order,inventory}.yml` workflows, replace the
   `ssh-keyscan -H "$VPS_HOST" >> ~/.ssh/known_hosts` line with appending the *committed*
   file instead: `cat deploy/ssh/dnit-vps.known_hosts >> ~/.ssh/known_hosts` (the repo is
   already checked out at that point in the workflow — `actions/checkout` runs before
   the "Prepare SSH" step in all three files, confirm this ordering is still true before
   relying on it).
3. Remove the `ssh-keyscan` call and the now-unnecessary runtime network fetch entirely
   — do not keep it as a fallback; a fallback that silently reintroduces the
   trust-on-first-use gap defeats the fix.

## Finding 2 — WebSocket upgrade headers scoped to the catch-all `location /`, not `/ws`

**This was a coordinator brief error in round 1**, not an implementer deviation — the
V-2 brief explicitly said "the `location /` block needs... `Upgrade`/`Connection`
headers," mirroring the Pet Giftshop template's single catch-all block without
accounting for the fact that this project's WebSocket traffic is only at one specific
path. The implementer followed that instruction correctly; the instruction itself was
wrong. Correcting it now.

Current state in `deploy/nginx/saga-orders.conf` and `deploy/nginx/saga-inventory.conf`
(confirmed by reading both files): the single `location / { ... proxy_set_header
Connection "upgrade"; ... }` block forces `Connection: upgrade` semantics onto **every**
proxied request, including plain REST API calls — not just the actual WebSocket
handshake at `/ws`. This is the standard Nginx WebSocket-proxy anti-pattern: unscoped
upgrade headers can break normal HTTP keep-alive/connection reuse behavior for
non-WebSocket traffic.

**Fix: split into two location blocks** — a dedicated `location /ws` carrying the
upgrade headers, and the catch-all `location /` reverting to plain HTTP proxying (same
shape as `saga-auth.conf`, which has no WebSocket traffic and is correct as-is — use it
as the reference for what `location /` should look like once the headers are removed).

For `saga-orders.conf` and `saga-inventory.conf` only, replace the single `location /`
block with:

```nginx
    location /ws {
        proxy_pass http://127.0.0.1:<PORT>;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 90s;
    }

    location / {
        proxy_pass http://127.0.0.1:<PORT>;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 90s;
    }
```

(`<PORT>` = 8090 for orders, 8091 for inventory — same ports as round 1, unchanged.)
`location /ws` as a prefix match correctly covers the STOMP endpoint and any SockJS
fallback transport paths that hang off it (e.g. `/ws/xhr`, `/ws/websocket`) since Nginx
prefix-location matching includes everything under the matched path. Verify this is
still true for how `WebSocketConfig.java` registers the endpoint before assuming it —
don't just copy this snippet blind.

`saga-auth.conf` is not touched — it was already correct in round 1 (no WebSocket
traffic, no upgrade headers).

## Acceptance criteria (observable outcomes)

1. `deploy/ssh/dnit-vps.known_hosts` exists with exactly the three lines given above.
2. All three deploy workflows use `cat deploy/ssh/dnit-vps.known_hosts >>
   ~/.ssh/known_hosts` and no longer call `ssh-keyscan` anywhere. `grep -rn ssh-keyscan
   .github/workflows/deploy-*.yml` returns nothing.
3. `saga-orders.conf` and `saga-inventory.conf` each have a dedicated `location /ws`
   block carrying the upgrade headers, and their `location /` block no longer has
   `Upgrade`/`Connection` headers — `grep -n "location /ws" deploy/nginx/saga-{orders,inventory}.conf`
   shows a match in both; `grep -n "Connection \"upgrade\"" deploy/nginx/saga-{orders,inventory}.conf`
   shows exactly one match per file (inside `location /ws`, not `location /`).
4. `saga-auth.conf` is untouched — `git diff` should show no changes to that file.
5. YAML re-validated for all three workflows after the edit (show actual
   `yaml.safe_load` output). Manual brace/statement-termination check for the two
   changed Nginx files (same as round 1 — `nginx -t` still not expected to be available,
   say so if that's still true rather than assuming).
6. `git status --short` shows only the files touched by these two fixes — no scope
   creep into V-1's systemd units or the other sprint-22 artifacts.

## Related
Round-1 handoff: `docs/backlog/sprint-22-handoff.md`. Round-1 review (REJECT):
`reviews/sprint-22-track-c-review.md`. This is a genuine, coordinator-verified blocker —
not a stale/false reject — so this round-2 fix is required before Sprint 22 can close,
not optional polish.
