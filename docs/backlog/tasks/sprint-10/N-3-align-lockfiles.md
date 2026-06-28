# N-3 — Align lockfiles to Angular 22.0.4 from clean install

**Sprint:** 10  
**Priority:** Should fix

## Problem

Codex's `npm ls` showed `node_modules` had Angular packages at `22.0.4` but the committed `package-lock.json` in both UIs still pins `@angular/core`, `@angular/compiler-cli`, and `@angular-devkit/build-angular` at `22.0.2`/`22.0.3`. A `npm ci` from the committed locks would install older versions than what the build was actually run against — the build is not fully reproducible from the committed state.

## What to do

Run the following **from Windows PowerShell** (not WSL — WSL hits EACCES on `/mnt/c/` atomic renames):

```powershell
cd C:\projects\pub-rec-opencode-deepseek\order-ui
npm install --package-lock-only
cd ..\inventory-ui
npm install --package-lock-only
```

`--package-lock-only` updates the lockfile to the latest semver-compatible resolutions without touching `node_modules`. No EACCES issue since no file moves occur in `node_modules`.

After updating both lockfiles, verify:

```powershell
npm run build   # in order-ui — must exit 0
npm run build   # in inventory-ui — must exit 0
npm audit --omit=dev   # in both — must still show 0 vulnerabilities
```

Then commit both updated lockfiles.

## If running from WSL

`npm install --package-lock-only` **should** work in WSL (it skips node_modules) but if it fails with EACCES, state this explicitly and show the error. Do not fake a pass. The coordinator will note the limitation in the handoff.

## Acceptance criteria

1. Both `package-lock.json` files updated and committed — `@angular/core`, `@angular/compiler-cli`, `@angular-devkit/build-angular` all resolve to their current `22.0.x` versions (whatever `npm install --package-lock-only` resolves to).
2. `npm run build` exits 0 in both UIs after the lockfile change — show output.
3. `npm audit --omit=dev` still shows 0 vulnerabilities in both — show output.
4. Both lockfiles are twin-consistent (same Angular version pins in both).
5. If blocked by EACCES, show the error and state the limitation — do not fake a pass.
