# Task G-4: Finish Angular dependency remediation (both UIs)

**Resolves:** Must-fix 1 in `reviews/sprint-2-track-a-review.md` ("`F-6` is still open... the current `npm audit --json` result on 2026-06-20 is still `50` vulnerabilities (`30 high`, `13 moderate`, `7 low`) in each app... `npm outdated` also shows the installed stack is unchanged within range").

## Context

This is a direct carry-over of Sprint 2's `F-6` (`docs/backlog/tasks/sprint-2/F-6-angular-dependency-remediation.md`) — read that brief in full, it's still accurate and this task doesn't replace it. Codex confirms the vulnerability count (`30 high`) is **byte-for-byte unchanged** from before Sprint 2 ran, and `@angular/core`/`@angular/cli` are still pinned at exactly `18.2.14`/`18.2.21` with no movement even within the existing `^18.2.0` range. Whatever was attempted last sprint either didn't run or didn't take effect — re-verify from scratch rather than assuming partial progress exists to build on.

## Task

Same steps as the original F-6 brief, repeated here because last sprint's attempt left no trace:

1. In both `order-ui` and `inventory-ui`: `npm install`, then `npm audit --json` and `npm outdated` to get a fresh, current baseline (advisory databases move — don't reuse the 2026-06-20 numbers as ground truth, re-pull them).
2. Run `npm update` in both (stays within the `^18.2.0` ranges already in `package.json`) and re-audit. If this doesn't move the numbers at all, that's itself informative — check whether `npm update` is actually resolving to newer in-range versions or whether the lockfile is pinned tighter than the range allows (e.g. an `package-lock.json` with exact resolved versions that `npm update` isn't touching — may need `npm update --save` or deleting `package-lock.json` and reinstalling within range).
3. If high-severity advisories remain that specifically require a newer Angular major per the advisory's "patched versions" field (not a guess), **stop and report back** with the specific advisories and required version — don't silently bump the major version. This was true last sprint and is still true now.
4. Whatever changes, apply identically to both UIs — they're meant to be twins (see ADR-0001).
5. **New for this task:** after making changes, re-run `npm audit --json` and explicitly diff the before/after vulnerability counts in the task report (counts by severity, not just "fixed it"). Last sprint's failure mode was plausibly "ran the commands but didn't actually verify/commit the result" — close that gap by showing the actual before/after numbers.

## Out of scope
- Same as F-6: no backend (Maven) dependency changes, no Dependabot/Renovate config.

## Acceptance criteria
- `npm audit` in both `order-ui` and `inventory-ui` shows materially fewer high-severity findings than the current re-baselined count (re-pull the baseline at the start of this task, don't reuse `30`) — if any remain, the report names the specific advisory and explains why (e.g. "requires Angular 19, needs its own sprint") rather than being silently left unresolved.
- `npm run build` still succeeds in both apps after the dependency changes.
- `order-ui` and `inventory-ui`'s `package.json`/`package-lock.json` dependency versions match each other.
- The task report includes the actual before/after `npm audit` numbers (not a summary claim) so the next reviewer doesn't have to re-derive whether anything changed.
