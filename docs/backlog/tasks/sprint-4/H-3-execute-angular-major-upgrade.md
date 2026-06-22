# Task H-3: Execute the Angular major-version upgrade for real (both UIs)

**Resolves:** Must-fix 3 in `reviews/sprint-3-track-a-review.md` ("`npm audit --json` on 2026-06-20 is still unchanged in both apps: `50` total vulnerabilities, `30` high... That does not meet the 'materially fewer high-severity findings' acceptance target").

## Context

This is the third sprint in a row this finding has shown up (`F-6` in Sprint 2, `G-4` in Sprint 3, now `H-3`). Both prior attempts correctly determined that `npm update` within the `^18.2.0` range cannot fix it, and both correctly stopped rather than blindly forcing a major bump — that was the right call each time, **but the decision those briefs asked for ("flag it as a candidate for its own sprint task") has now been made**: do the upgrade. Don't re-run the same audit-and-stop cycle a third time.

Run `npm audit` yourself before starting — advisory data moves, and you should see something close to this (captured 2026-06-20 in `order-ui`):
```
@angular/common  <=19.2.25
Severity: high
...
fix available via `npm audit fix --force`
Will install @angular/common@22.0.2, which is a breaking change
```
The patched version floor is **above Angular 19**, not Angular 21 as a prior sprint's `CLAUDE.md` note guessed — verify the actual current advisory data yourself rather than trusting that number, it may be stale by the time you run this.

## Task

1. In `order-ui`, use Angular's official incremental upgrade path — `ng update` refuses to skip majors, so step through them one at a time, building and smoke-testing after each step:
   ```
   npx ng update @angular/core@19 @angular/cli@19
   npm run build   # confirm it still builds before moving to the next major
   npx ng update @angular/core@20 @angular/cli@20
   npm run build
   npx ng update @angular/core@21 @angular/cli@21   # if this version exists; check what's actually published
   npm run build
   ```
   Continue until you reach a version where `npm audit` no longer reports the high-severity findings tied to `@angular/core`/`@angular/common`/`@angular/compiler` (re-check the advisory's patched-version floor as you go — you may not need to go all the way to the latest major if an earlier one already clears it).
2. `ng update` will likely surface breaking-change migration schematics automatically (it runs codemods for known breaking changes) — let it, but read its output at each step rather than blindly continuing, and fix anything it flags as needing manual attention (Angular Material API changes, standalone-component config changes, build tooling changes are the most likely categories given this app's stack).
3. Pay specific attention to:
   - `app.config.ts` / bootstrap code (standalone API surface has shifted across Angular majors).
   - Anything using `@angular/material` — confirm theming/imports still resolve.
   - `proxy.conf.json` and `angular.json` build options — newer CLI majors have changed config schema before.
   - `websocket.service.ts`'s `@stomp/stompjs`/`sockjs-client` usage — unrelated to the Angular bump itself, but confirm the CommonJS optimization-bailout warning (flagged as "nice to have" in the last 2 reviews) doesn't turn into a hard build error under a newer build pipeline.
4. Once `order-ui` is fully upgraded and builds clean, apply the **exact same** final dependency versions to `inventory-ui` (same approach as prior sprints — the two UIs must stay on identical stacks per ADR-0001). Don't independently run `ng update` on `inventory-ui` and risk it landing on slightly different resolved versions; copy `order-ui`'s final `package.json` dependency block and lockfile resolution, then `npm install` and re-verify the build there too.
5. Manually smoke-test both apps in a browser after the upgrade (login, dashboard, order placement / stock view) — a major version bump is exactly the kind of change that can pass `npm run build` while still breaking runtime behavior `tsc` wouldn't catch.

## Out of scope
- Don't touch backend (Maven) dependencies.
- Don't add Dependabot/Renovate config — separate concern, not part of this fix.

## Acceptance criteria
- `npm audit` in both `order-ui` and `inventory-ui` shows the previously-reported high-severity Angular advisories resolved (re-check against current advisory data, not the 2026-06-20 snapshot, since it's a moving target) — report the actual before/after counts.
- `npm run build` succeeds in both apps post-upgrade.
- `order-ui` and `inventory-ui`'s `package.json`/lockfile dependency versions match each other exactly.
- A manual browser smoke test of both apps' core flows is reported as done (login, dashboard, order placement / stock view) — not just a clean build.
