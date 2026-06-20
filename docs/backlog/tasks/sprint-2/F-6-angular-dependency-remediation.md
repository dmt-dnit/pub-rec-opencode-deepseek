# Task F-6: Angular dependency remediation (both UIs)

**Resolves:** Blocker 4 in `reviews/sprint-1-track-a-review.md`.

## Context
Codex's review found `order-ui`'s dependency tree (Angular 18.2.14 runtime, 18.2.21 CLI/build tooling) reporting 50 `npm audit` vulnerabilities (30 high, 13 moderate, 7 low), including advisories against `@angular/core`/`@angular/common`/`@angular/compiler` themselves and transitive tooling (`vite`, `webpack-dev-server`, `rollup`, `tar`, `picomatch`, `piscina`). `inventory-ui` uses the same stack and almost certainly has the same exposure — Codex only audited one to save time, not because the other is clean.

Re-run the audit yourself before fixing anything — advisory databases and registry contents move, and the exact numbers above are a point-in-time snapshot from 2026-06-20, not a fixed target.

## Task

### 1. Re-baseline
In both `order-ui` and `inventory-ui`:
```
npm install
npm audit --json > /tmp/audit-before.json   # or wherever, just keep a before/after record
npm outdated
```

### 2. Fix within the current major version first
Run `npm update` (stays within the `^18.2.0` ranges already pinned in `package.json`) and re-audit. Angular patch/minor releases routinely backport security fixes — this alone may close most of the high-severity findings without any breaking change.

### 3. Escalate only if needed
If high-severity advisories remain after step 2 that specifically require a newer Angular major to resolve (check the advisory's "patched versions" field, don't guess), that's a real decision — **stop and report back rather than silently bumping the major version**. A jump from Angular 18 to 19/20 can involve breaking changes across `@angular/material`, standalone-component APIs, and build tooling, and isn't something to do as a side effect of a dependency-hygiene task. Flag it as a candidate for its own sprint-3 task with the specific advisories that force the issue.

### 4. Apply identically to both UIs
Whatever ends up changed in `order-ui`'s `package.json`/`package-lock.json`, apply the same dependency versions to `inventory-ui` — they're meant to stay on identical stacks (see `docs/adr/0001-event-driven-showcase-architecture.md`, both UIs are deliberately structurally identical). Divergent dependency versions between two apps that are supposed to be twins is its own kind of drift to avoid.

## Out of scope
- Don't touch backend (Maven) dependencies in this task — that's unrelated to this finding.
- Don't add a Dependabot/Renovate config or similar automation in this task — that's a reasonable Track B candidate, not part of fixing the current finding.

## Acceptance criteria
- `npm audit` in both `order-ui` and `inventory-ui` shows materially fewer high-severity findings than the 2026-06-20 baseline (30 high in `order-ui`) — if any remain, the report explains why (e.g. "requires Angular 19, flagged for sprint 3") rather than the task being silently marked done with vulnerabilities still present.
- `npm run build` still succeeds in both apps after the dependency changes.
- `order-ui` and `inventory-ui`'s `package.json` dependency versions match each other.
