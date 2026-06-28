# O-2 — Remove stale `sockjs-client` entries from both lockfiles

**Sprint:** 11  
**Priority:** Must fix

## Problem

Both committed `package-lock.json` files contain stale entries for `sockjs-client` and `@types/sockjs-client`, even though these packages are absent from `package.json` (they were removed in Sprint 8 L-1). They appear in:

- The root `""` package section's `dependencies` and `devDependencies` (lines 23 and 31 of both lockfiles) — these should mirror `package.json` exactly.
- The `node_modules/sockjs-client`, `node_modules/sockjs-client/node_modules/debug`, `node_modules/sockjs-client/node_modules/eventsource`, and `node_modules/@types/sockjs-client` package sections.

This happened because Sprint 10 N-3 ran `npm install --package-lock-only` against an existing `node_modules` directory (installed from Windows before the removal) that still had sockjs-client present. `--package-lock-only` reflects what is in `node_modules`, not just what is in `package.json`.

Codex confirmed: running `npm install` from PowerShell (which writes to `node_modules` and then generates the lockfile) removed these entries. Commit `df998a5` did not cleanly regenerate the lockfiles.

## What was changed

A Node.js script surgically removed the stale entries from both lockfiles:

```javascript
const root = pkg.packages[''];
delete root.dependencies['sockjs-client'];
delete root.devDependencies['@types/sockjs-client'];
// Remove all node_modules/sockjs-client* and @types/sockjs-client sections
Object.keys(pkg.packages)
  .filter(k => k.startsWith('node_modules/sockjs-client') || k === 'node_modules/@types/sockjs-client')
  .forEach(k => delete pkg.packages[k]);
```

Entries removed from each lockfile:
- `node_modules/@types/sockjs-client`
- `node_modules/sockjs-client`
- `node_modules/sockjs-client/node_modules/debug`
- `node_modules/sockjs-client/node_modules/eventsource`

`sockjs` (server-side, `node_modules/sockjs`) and `@types/sockjs` remain — these are legitimate transitive dependencies of `webpack-dev-server` which is required by `@angular-devkit/build-angular`. Only the `*-client` variants (direct deps of the old WebSocket implementation) were removed.

## Acceptance criteria

1. `grep -r "sockjs-client" order-ui/package-lock.json inventory-ui/package-lock.json` returns no output.
2. The `sockjs` and `@types/sockjs` entries (without `-client`) are still present in both lockfiles (they are legitimate transitive deps).
3. `npm run build` exits 0 in both UIs after the lockfile change.
4. `npm audit --omit=dev` still shows 0 vulnerabilities in both UIs.
5. `npm ls sockjs-client` (from Windows PowerShell in each UI dir) shows no installed package — the package is cleanly absent from the dependency tree.

## WSL limitation

`npm ls` and `npm audit` can be run from WSL with `--package-lock-only` flag if node_modules access fails, but the canonical verification is PowerShell `npm.cmd ls sockjs-client` which checks what's actually installed.
