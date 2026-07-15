# Sprint 22 Track C Round 2 Review - Deploy Artifacts

Review-Target-Commit: `341554b`  
Handoff: `docs/backlog/sprint-22-handoff.md`  
Verdict: ACCEPT

## Findings

No blocking source-level findings remain in Sprint 22 round 2.

## Cleared Prior Findings

- **P1 is fixed: SSH host verification is now pinned instead of learned at deploy time.** The runtime `ssh-keyscan` trust-on-first-use step is gone from all three workflows. Each workflow now appends the committed host-key file into `known_hosts` at [.github/workflows/deploy-auth.yml](C:/projects/pub-rec-opencode-deepseek/.github/workflows/deploy-auth.yml:70), [.github/workflows/deploy-order.yml](C:/projects/pub-rec-opencode-deepseek/.github/workflows/deploy-order.yml:70), and [.github/workflows/deploy-inventory.yml](C:/projects/pub-rec-opencode-deepseek/.github/workflows/deploy-inventory.yml:70). The pinned material is present in [deploy/ssh/dnit-vps.known_hosts](C:/projects/pub-rec-opencode-deepseek/deploy/ssh/dnit-vps.known_hosts:1).

- **P2 is fixed: WebSocket upgrade handling is now scoped to `/ws`, while ordinary HTTP stays on a plain `location /`.** The two WebSocket-facing vhosts now split the routes cleanly: [deploy/nginx/saga-orders.conf](C:/projects/pub-rec-opencode-deepseek/deploy/nginx/saga-orders.conf:18), [deploy/nginx/saga-orders.conf](C:/projects/pub-rec-opencode-deepseek/deploy/nginx/saga-orders.conf:22), and [deploy/nginx/saga-orders.conf](C:/projects/pub-rec-opencode-deepseek/deploy/nginx/saga-orders.conf:30), plus the matching lines in [deploy/nginx/saga-inventory.conf](C:/projects/pub-rec-opencode-deepseek/deploy/nginx/saga-inventory.conf:18), [deploy/nginx/saga-inventory.conf](C:/projects/pub-rec-opencode-deepseek/deploy/nginx/saga-inventory.conf:22), and [deploy/nginx/saga-inventory.conf](C:/projects/pub-rec-opencode-deepseek/deploy/nginx/saga-inventory.conf:30). That now matches the application’s actual WebSocket path at [order-service/src/main/java/com/example/orderservice/config/WebSocketConfig.java](C:/projects/pub-rec-opencode-deepseek/order-service/src/main/java/com/example/orderservice/config/WebSocketConfig.java:21) and [inventory-service/src/main/java/com/example/inventoryservice/config/WebSocketConfig.java](C:/projects/pub-rec-opencode-deepseek/inventory-service/src/main/java/com/example/inventoryservice/config/WebSocketConfig.java:21). `saga-auth.conf` remains correctly unchanged at [deploy/nginx/saga-auth.conf](C:/projects/pub-rec-opencode-deepseek/deploy/nginx/saga-auth.conf:18).

## Residual Checks Not Reproduced Here

- I did not run `nginx -t` in this environment.
- I did not run `actionlint` in this environment.
- I did not exercise a live deploy workflow or a live VPS target.

Those are still operational checks for the later live-apply session, but they are not source-level blockers for this artifact review.
