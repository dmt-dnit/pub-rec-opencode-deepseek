# D-3 — Angular patch bumps (both UIs)

**Sprint:** 21. **Type:** npm config (2 projects). **Implementer:** opencode+DeepSeek (worktree).

## Why this exists
Dependabot flagged patch-level Angular bumps in both `order-ui` and `inventory-ui`:
- `@angular/core`, `@angular/forms`, `@angular/animations` `22.0.4` → `22.0.5`
- `@angular/cli`, `@angular-devkit/build-angular` (dev) `22.0.4` → `22.0.5`
- `@angular/material`, `@angular/cdk` `22.0.2` → `22.0.3`

This stays inside the already-current `22.x` line (Sprint 16-19's dev-CVE caveat about
`22.0.4` being latest was checked again at Sprint 21 scoping and is now superseded by
`22.0.5` — recheck the registry at implementation time in case a newer patch has shipped
since).

## Deliverables
In both `order-ui/package.json` and `inventory-ui/package.json`:
1. Bump `@angular/core`, `@angular/forms`, `@angular/animations`, `@angular/material`,
   `@angular/cdk` to their target patch versions above.
2. Bump `@angular/cli` and `@angular-devkit/build-angular` (devDependencies) likewise.
3. Run `npm install` in both projects to update the lockfiles to match.
4. Confirm the exact target versions against the npm registry at implementation time
   (`npm view @angular/core versions --json | tail`) rather than trusting this brief's
   numbers blindly — Dependabot's proposal can be superseded by a newer patch.

## Acceptance criteria (observable outcomes)
1. `npm ls @angular/core @angular/forms @angular/animations @angular/material @angular/cdk @angular/cli @angular-devkit/build-angular`
   in both `order-ui` and `inventory-ui` shows the bumped versions — show actual output.
2. `npm install` completes clean in both projects (lockfile updated, committed).
3. `npm run build` succeeds in both projects — show the actual build output tail.
4. `npm audit --omit=dev` still reports **0 vulnerabilities** in both projects (the
   sprint-rules-card pass/fail signal) — show actual output, not an assertion.
5. Full `npm audit` dev-tooling advisory count may be unchanged or different from the
   standing caveat (8 advisories under `@angular-devkit/build-angular`'s chain) — report
   the actual current count in your handoff; don't assume it's unchanged.
6. `git diff` on `package.json`/`package-lock.json` shows only the intended version
   bumps.
7. Browser smoke: state explicitly whether you exercised the app in a browser
   ("passed" or "Codex-only verification") per the sprint rules card — don't leave it
   silent.

## Related
[[feedback-pin-latest-versions]], sprint-rules-card item 9 (`npm audit --omit=dev` is
the pass/fail signal; full audit is a caveat, not a blocker).
