# Sprint 22 Round 2 Demo Notes

Round 2 is worth preserving because it shows a clean infra-review correction loop, not just a fix.

- The first review rejected two deploy-artifact issues before any server was touched: trust-on-first-use SSH host verification in CI, and WebSocket upgrade headers scoped to the whole vhost instead of the actual `/ws` endpoint.
- The fix sprint stayed disciplined: pinned `known_hosts` replaced `ssh-keyscan`, the two WebSocket vhosts split `location /ws` from ordinary `location /`, and nothing else drifted.
- The round-2 verifier result matters for the story too: the first pass of `verify-review.sh` misread `Verdict: ACCEPT — round-1 blockers cleared` as a reject because the prose still contained the word `blockers`. Tightening that to the exact machine token `Verdict: ACCEPT` made the review both human-correct and automation-correct.
- Good presentation angle: this is the same loop catching two different classes of mistakes at once, one in deploy security and one in process automation.
