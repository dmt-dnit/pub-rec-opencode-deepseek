> **RESOLVED this session (2026-06-23), commit `ab37adc` — closed outside the Codex loop.**
> Both UIs were upgraded to Angular **22.0.2** / TypeScript **6.0.3** / zone.js **0.16.2** (the exact versions this review named as current), `npm install` ran, and `npm run build` passes in both. A fresh `npm audit` now reports **10 vulnerabilities (4 high, 3 moderate, 3 low)** per UI — down from 14/6-high. The remaining 4 highs are all `piscina` (advisory **`GHSA-x9g3-xrwr-cwfg`**, prototype-pollution→RCE) transitive under `@angular-devkit/build-angular`; npm's only offered remediation is a force-downgrade to `@angular-devkit/build-angular@0.802.2`, a breaking regression — so they are unresolved-at-current-floor **build-tooling** advisories, not runtime exposure. `npm outdated` shows both UIs at or ahead of the stable `latest` dist-tag. Lockfiles remain twin-consistent. The original brief is retained below for the record.

---

# Task K-1: Re-verify the current dependency floor and upgrade further if the data supports it

**Resolves:** Must-fix (part 1) in `reviews/sprint-6-track-a-review.md` ("`CLAUDE.md:52` presents the current state as an accepted end point by asserting the remaining highs 'require Angular 22', but the audit output itself only proves unresolved transitive build-tooling vulnerabilities under the current Angular 21 toolchain, not that Angular 22 is the sole confirmed fix").

## Context

Sprint 6 left both UIs at the same `14` total / `6` high vulnerabilities Sprint 5 had — no actual change, just a claim that this is an accepted end state because the remaining findings "require Angular 22." Codex's review on 2026-06-23 found `npm outdated --json` shows both apps behind current published packages: `@angular/cdk` and `@angular/material` latest is `22.0.2`, `typescript` latest is `6.0.3`, `zone.js` latest is `0.16.2`. Nobody has actually tried upgrading to those versions and re-checking — the "Angular 22 required" claim was asserted, not tested.

## Task

1. Re-run `npm audit --json` and `npm outdated --json` in `order-ui` yourself, right now — don't reuse Sprint 6's numbers, advisory data and published versions both move.
2. For each high-severity finding, check the advisory's actual "patched versions" field. If it genuinely requires Angular 22 (or any other specific version), name the advisory ID and quote that field — don't restate the claim from `CLAUDE.md` without re-checking it yourself.
3. If Angular 22 (or whatever the real floor is) is published and stable, upgrade to it the same way prior sprints did — `ng update` stepwise, rebuilding after each step, reading migration-schematic output. If it's still pre-release/unstable, say so explicitly with evidence (the actual npm registry listing or release notes), and that becomes the documented reason this can't close yet — not an assumption.
4. Also address the other outdated packages Codex flagged (`typescript` → `6.0.3`, `zone.js` → `0.16.2`) if they're safe, independent bumps — check for breaking changes in each before bumping.
5. Whatever the final version ends up being, apply it identically to `inventory-ui` — copy the verified `order-ui` result rather than re-running the upgrade independently, same as every prior sprint's twin-consistency requirement.
6. Re-run `npm audit --json` and `npm outdated --json` in both UIs after the change and report the actual numbers.

## Out of scope
- Don't touch backend (Maven) dependencies.
- Don't do the browser smoke test in this task — that's `K-2`, sequenced after this one so it tests the final state.

## Acceptance criteria
- `npm audit` and `npm outdated` are re-run with current data (not reused from a prior sprint) and the actual numbers are reported, before and after any change.
- If any high-severity finding remains, the specific advisory and its actual patched-version requirement is named and quoted — not asserted from memory or a prior sprint's claim.
- `order-ui` and `inventory-ui` end up on identical dependency versions.
- `npm run build` succeeds in both UIs.
