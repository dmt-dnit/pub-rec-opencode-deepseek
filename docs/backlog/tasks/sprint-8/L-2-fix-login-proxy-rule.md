# Task L-2: Narrow /login proxy rule to preserve SPA route

**Resolves:** Should-fix in `reviews/sprint-7-track-a-review.md` — "Direct `/login` requests are still swallowed by the dev proxy."

## Context

Both proxy configs (`order-ui/proxy.conf.json` and `inventory-ui/proxy.conf.json`) contain:

```json
"/login/**": {
  "target": "http://localhost:9000",
  ...
}
```

This forwards every request whose path begins with `/login/` to `auth-server`. It means that if a developer navigates directly to `http://localhost:4200/login`, the Angular dev server proxies the request to Spring Security's `/login` endpoint on port 9000 and returns Spring's HTML login form — instead of serving the Angular SPA which has its own `/login` route. The result is a misleading blank/wrong page in a dev environment and obscures smoke-test setup.

The rule exists to relay the Google OAuth2 callback, which Spring Security handles at `/login/oauth2/code/google`. That specific path must still reach auth-server; the bare `/login` route should not.

## Change required

In **both** proxy configs, replace the `/login/**` key with `/login/oauth2/**`:

**Files:**
- `order-ui/proxy.conf.json`
- `inventory-ui/proxy.conf.json`

```json
// Before
"/login/**": {
  "target": "http://localhost:9000",
  "secure": false,
  "logLevel": "debug"
},

// After
"/login/oauth2/**": {
  "target": "http://localhost:9000",
  "secure": false,
  "logLevel": "debug"
},
```

No other change to either file.

## Verification

1. After the change, `grep -r '"/login/\*\*"' order-ui/proxy.conf.json inventory-ui/proxy.conf.json` must return nothing (the broad rule is gone).
2. `grep -r '"/login/oauth2/\*\*"' order-ui/proxy.conf.json inventory-ui/proxy.conf.json` must return two hits (one per file).
3. Both files must remain valid JSON (`node -e "JSON.parse(require('fs').readFileSync('order-ui/proxy.conf.json','utf8'))"` and same for inventory-ui).

## Out of scope
- Do not change any other proxy rules.
- Do not start the dev server or verify runtime behavior — that is L-3's job.
