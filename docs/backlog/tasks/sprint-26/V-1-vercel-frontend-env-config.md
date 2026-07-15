# V-1 — Vercel deploy prep for order-ui + inventory-ui

**Sprint:** 26. **Track:** C — go-live, Phase 4. **Scope:** frontend only, both UIs.

## Why

`order-ui`/`inventory-ui` only work today behind `ng serve`'s dev proxy. Deployed as
static SPAs on Vercel there is no proxy, so the app must call the real backend origins
(the `dnit-vps` subdomains Phase 3/3.5 already stood up: `saga-auth.dnit.be`,
`saga-orders.dnit.be`, `saga-inventory.dnit.be`) directly, and needs a client-side-routing
fallback since Vercel serves static files by default (a hard refresh on `/dashboard`
would 404 without one).

## Current state (verified, not assumed — re-check if the repo has moved since this was written)

- `order-ui/angular.json` and `inventory-ui/angular.json` each have a single `build`
  architect target, **no `configurations` key at all**. Full current `build` block
  (identical shape in both, only `outputPath`/ports differ — ports are in `serve`, not
  relevant here):
  ```json
  "build": {
    "builder": "@angular-devkit/build-angular:application",
    "options": {
      "outputPath": "dist",
      "index": "src/index.html",
      "browser": "src/main.ts",
      "polyfills": ["zone.js"],
      "tsConfig": "tsconfig.app.json",
      "assets": [],
      "styles": ["@angular/material/prebuilt-themes/indigo-pink.css", "src/styles.css"],
      "scripts": [],
      "allowedCommonJsDependencies": ["@stomp/stompjs"]
    }
  }
  ```
- Neither UI has a `src/environments/` directory yet — this task creates it.
- Confirmed by an actual local `ng build` run this session (order-ui): output goes to
  **`dist/browser/`** (not `dist/`) — this builder always nests browser output under
  `browser/` even with no SSR configured. Same builder/config in inventory-ui, so treat
  it as certain there too, but re-confirm with your own build rather than trust this
  note blindly.
- API base URLs used today (all relative, no environment indirection):
  - `order-ui/src/app/services/auth.service.ts:9` — `private apiBase = '/api/auth';`
  - `order-ui/src/app/services/order.service.ts:8` — `private apiBase = '/api/orders';`
  - `inventory-ui/src/app/services/auth.service.ts:9` — `private apiBase = '/api/auth';`
  - `inventory-ui/src/app/services/inventory.service.ts:8` — `private apiBase = '/api/inventory';`
- WebSocket broker URL, both UIs' `websocket.service.ts` (identical shape, lines 13-15):
  ```ts
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  this.client = new Client({
    brokerURL: `${protocol}//${window.location.host}/ws`,
    ...
  ```

## What to change

### 1. Environment files (both UIs)

Create `src/environments/environment.ts` (dev — values stay empty so relative-path
behavior under `ng serve` is byte-for-byte unchanged):
```ts
export const environment = {
  production: false,
  authApiBase: '',
  wsBase: ''
};
```
`order-ui` additionally needs `ordersApiBase: ''`; `inventory-ui` needs
`inventoryApiBase: ''` in this same dev file.

Create `src/environments/environment.prod.ts`:

`order-ui`:
```ts
export const environment = {
  production: true,
  authApiBase: 'https://saga-auth.dnit.be',
  ordersApiBase: 'https://saga-orders.dnit.be',
  wsBase: 'wss://saga-orders.dnit.be'
};
```

`inventory-ui`:
```ts
export const environment = {
  production: true,
  authApiBase: 'https://saga-auth.dnit.be',
  inventoryApiBase: 'https://saga-inventory.dnit.be',
  wsBase: 'wss://saga-inventory.dnit.be'
};
```

### 2. Wire the file swap into `angular.json` (both UIs)

Add a `production` configuration with `fileReplacements`, and set it as the default so a
plain `ng build` (what Vercel's build command will run) picks it up automatically:
```json
"build": {
  "builder": "@angular-devkit/build-angular:application",
  "options": { ...unchanged... },
  "configurations": {
    "production": {
      "fileReplacements": [
        { "replace": "src/environments/environment.ts", "with": "src/environments/environment.prod.ts" }
      ]
    }
  },
  "defaultConfiguration": "production"
}
```
Do not change any existing key under `options`.

### 3. Point the API calls at the environment (both UIs)

In each service, import `environment` from `../../environments/environment` (adjust
relative path to the actual file location) and prefix `apiBase`:
- `order-ui/auth.service.ts`: `private apiBase = \`${environment.authApiBase}/api/auth\`;`
- `order-ui/order.service.ts`: `private apiBase = \`${environment.ordersApiBase}/api/orders\`;`
- `inventory-ui/auth.service.ts`: `private apiBase = \`${environment.authApiBase}/api/auth\`;`
- `inventory-ui/inventory.service.ts`: `private apiBase = \`${environment.inventoryApiBase}/api/inventory\`;`

In dev, `environment.authApiBase` etc. are `''`, so the resulting string is identical to
today's (`/api/auth`) — no dev behavior change.

### 4. WebSocket broker URL (both UIs, same edit shape)

```ts
const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
const brokerURL = environment.wsBase
  ? `${environment.wsBase}/ws`
  : `${protocol}//${window.location.host}/ws`;
this.client = new Client({
  brokerURL,
  ...
```
In dev `environment.wsBase` is `''` (falsy), so it falls through to the existing
`window.location`-based construction — unchanged dev behavior.

### 5. `vercel.json` (both UIs, repo root of each UI directory since that's the Vercel
project's Root Directory)

```json
{
  "rewrites": [
    { "source": "/(.*)", "destination": "/index.html" }
  ]
}
```
This is a client-side-routing fallback only — Vercel serves an actual matching static
file first if one exists, so this doesn't interfere with the JS/CSS bundle requests.

## Acceptance criteria (observable outcomes, show real output — don't assert "Pass")

1. `cd order-ui && rm -rf dist && npx ng build` (or `npm run build`) — **BUILD SUCCESS**,
   actual output shown, including the "Output location" line.
2. Confirm the production environment values actually made it into the bundle:
   `grep -r "saga-auth.dnit.be" order-ui/dist/browser/*.js` returns at least one match.
   This is the real proof `fileReplacements` fired — a passing build with the dev
   strings still baked in would be a silent failure this specific check catches.
3. Same two checks for `inventory-ui` (grep for `saga-inventory.dnit.be` and
   `saga-auth.dnit.be`).
4. Confirm dev behavior is unchanged: start `ng serve` for one of the two UIs, load it in
   a browser, and confirm the network tab shows requests still going to the relative
   `/api/...` paths (proxied to localhost as before) — no hardcoded prod origin leaking
   into a dev build. State explicitly if you don't have a way to check this in a browser
   from your environment, rather than asserting it passed.
5. `git status --short` clean after commit; confirm `dist/` was not accidentally staged
   (already `.gitignore`d via `**/dist/`, but double-check).
6. In your report, state the exact confirmed Output Directory (`dist/browser`) so the
   coordinator can pass it to Dimitri for the Vercel project settings — this is
   information this task discovers, not something the task changes in Vercel itself.
