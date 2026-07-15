# Sprint 26 Demo Notes

Sprint 26 is a good example of a migration that is mostly correct but still misses one user entrypoint.

- The team correctly moved REST API calls, WebSocket URLs, CORS allow-lists, and deploy-hook workflows toward the split Vercel-plus-VPS topology.
- The rejected detail is exactly the kind of thing that slips through when a change is "mostly configuration": Google login still uses a frontend-relative path, which worked on same-origin localhost but breaks once the SPA is hosted on Vercel and all unmatched routes rewrite to `index.html`.
- Good presentation angle: the review didn’t find a deep algorithm bug. It found a topology bug — one route still assumed the old deployment shape after everything else moved to the new one.
