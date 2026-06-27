# M-2 — npm audit clean pass

**Sprint:** 9  
**Priority:** High  
**Depends on:** — (independent, run in parallel with M-1)

## Background

Codex's Sprint 8 P2: both `order-ui` and `inventory-ui` report 10 vulnerabilities (4 high, 3 moderate, 3 low) in `npm audit`. The high-severity path runs through `piscina` → `@angular-devkit/build-angular` (a **dev** dependency). npm audit's suggested "fix" is a downgrade to `@angular-devkit/build-angular@21.2.17`, which is a major regression from the Angular 22 toolchain and not acceptable.

The goal of this task is to:
1. Prove that **production runtime dependencies are clean** (`npm audit --omit=dev` exits 0)
2. Check if any of the remaining 10 findings have a forward-compatible fix (a patch within Angular 22's tree, not a downgrade)
3. If any are fixable: apply `npm audit fix` (NOT `npm audit fix --force` which may downgrade)
4. Document the final posture in a way that gives Codex clear evidence to accept: which advisories remain, why they can't be fixed at the current floor, and that they are build-tooling only (not runtime exposure)

## Steps

### 1. Run production-only audit in both UIs

```bash
cd order-ui && npm audit --omit=dev
cd ../inventory-ui && npm audit --omit=dev
```

Expected: 0 vulnerabilities (all high/moderate/low are in devDependencies). Show the actual output.

### 2. Run full audit and check for safe fixes

```bash
cd order-ui && npm audit --json > /tmp/order-ui-audit.json
cd ../inventory-ui && npm audit --json > /tmp/inventory-ui-audit.json
```

Look at the `fixAvailable` field for each advisory. If `fixAvailable` is an object (not `true`), it means only a breaking change fixes it. If it's `true`, a non-breaking `npm audit fix` can resolve it.

Try:
```bash
cd order-ui && npm audit fix
cd ../inventory-ui && npm audit fix
```

Re-run `npm audit` after. Show before/after counts. Show `npm run build` still passes after.

### 3. Document remaining non-actionable items

If advisories remain after `npm audit fix`, list each one:
- Advisory ID / CVE
- Affected package (direct or transitive)
- Whether it is a devDependency (build tooling) or a production dependency
- Why there is no forward-compatible fix at the current Angular 22 floor
- The version npm audit would require (and why it's a regression)

Write this documentation as a section in the sprint-9 handoff doc (the coordinator writes that separately; your job is to produce the evidence text).

### 4. Lock file consistency check

After any `npm audit fix` runs, ensure both UIs are consistent:
- `order-ui/package-lock.json` and `inventory-ui/package-lock.json` should be committed
- `npm run build` passes in both

## Acceptance criteria

1. Show `npm audit --omit=dev` output from both UIs — expected 0 vulnerabilities.
2. Show `npm audit` full output (before and after any `npm audit fix` run).
3. Show `npm run build` exits 0 in both UIs after any changes.
4. For any remaining advisories: show the advisory detail and explain why no forward fix exists (reference the specific version npm requires and why it's a regression).
5. `package-lock.json` in both UIs is committed and consistent with `package.json`.

**Show actual command output. Do not assert "Pass" without the terminal evidence.**
