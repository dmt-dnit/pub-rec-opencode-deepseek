# Sprint 7 handoff — Track A close-out, round 5

Snapshot date: 2026-06-23. Unlike prior handoffs (which are diff-level coherence passes), this one records that two of the three planned tasks were **completed by direct work this session, outside the Codex implement/review loop**, and reports the evidence for those closures. The remaining task (`K-2`) is left for Codex as the sole open must-fix. Per the established role split, deep verification of `K-2` (running the live browser flow) is Codex's job; this handoff does not perform it.

## Status by task

| Task | Status | Notes |
|---|---|---|
| K-1 — re-verify dependency floor | **Resolved this session** (`ab37adc`) | Both UIs upgraded to Angular **22.0.2** / TypeScript **6.0.3** / zone.js **0.16.2** — the versions Sprint 6's review named as current. `npm run build` passes in both. Fresh `npm audit` (run this session, not reused): **10 vulns / 4 high / 3 moderate / 3 low** per UI, down from Sprint 6's 14/6-high. The 4 highs are all `piscina` advisory **`GHSA-x9g3-xrwr-cwfg`** (prototype-pollution→RCE), transitive under `@angular-devkit/build-angular`; the only npm-offered fix is a force-downgrade to `@angular-devkit/build-angular@0.802.2` (breaking regression), so they remain unfixable at the current floor and are build-tooling-only, not runtime. `npm outdated` shows both UIs at/ahead of the stable `latest` dist-tag. Lockfiles twin-consistent. |
| K-2 — actual browser smoke test | **OPEN — for Codex** | Not performed. This is the round-5 must-fix and the only reason Sprint 7 isn't closed. The brief now points at `scripts/startup-all.sh` (containerized backend, verified healthy this session) as the simplest way to stand the stack up. |
| K-3 — fix stale "Angular 18" text | **Resolved this session** (`ab37adc`) | `CLAUDE.md:12` and `:85` now read "Angular 22"; line 41's historical note still says 18, correctly (left per the brief). |

## What Codex should focus on

- **K-2 only.** Perform the real login → order placement → live status update → inventory live feed flow against the now-Angular-22 UIs, and document the specific account / SKU / outcome / any console errors — not a route-availability check. This requirement has been substituted with a lighter check four sprints running; this round is specifically about evidencing the actual flow.
- For **K-1**, if you want to independently confirm: re-run `npm audit` / `npm outdated` in both UIs and check the `piscina` advisory's patched-versions field yourself — the numbers above were produced this session and are the basis for treating the dependency floor as closed.

## Caveat on the role split

K-1 and K-3 were closed by the coordinator directly rather than via a Codex task, which deviates from the normal implement-then-review flow. They are committed (`ab37adc`) and the K-1 evidence is real command output, but if the loop's independence matters for sign-off, treat the K-1 audit numbers as "coordinator-reported, Codex re-verify" rather than independently reviewed.
