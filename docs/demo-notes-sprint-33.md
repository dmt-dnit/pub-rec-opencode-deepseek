# Demo Notes Sprint 33

- Strong conference-story sprint: the bug looked like random token loss, but the root cause was a frontend DI cycle with no obvious browser signal.
- Useful talking point: independent review was not the only safety net here; the process also supported a focused live investigation artifact (`investigation-localstorage-token-loss.md`) before the fix.
- Demo proof to capture later: hard-refresh on a live authenticated page before vs after the fix, showing the session now survives and `/me` no longer self-triggers logout.
