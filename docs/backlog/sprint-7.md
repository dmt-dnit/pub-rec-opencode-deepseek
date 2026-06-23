# Sprint 7 backlog: Track A close-out, round 5

**Source:** `reviews/sprint-6-track-a-review.md`, dated 2026-06-23. Verdict: **reject**. One must-fix with two parts (`J-2`'s dependency floor and the still-missing real smoke test), one should-fix (`J-3` left stale Angular-18 text elsewhere in `CLAUDE.md`).

## Scope change (2026-06-23): collapsed to K-2 only

Two of this sprint's three planned tasks were closed by **direct work this session, outside the Codex implement/review loop**, so the sprint is now a single open task — the browser smoke test. The closed tasks are recorded here and in `docs/backlog/sprint-7-handoff.md` with evidence; their briefs (`K-1`, `K-3`) carry a resolution banner.

| Task | Status | Evidence |
|---|---|---|
| K-1 — dependency floor | **Resolved this session** (`ab37adc`) | Both UIs on Angular 22.0.2 / TS 6.0.3 / zone.js 0.16.2; fresh `npm audit` = 10 vulns / 4 high (down from 14/6), remaining highs are `piscina` `GHSA-x9g3-xrwr-cwfg` transitive in `@angular-devkit/build-angular` with no clean upstream fix; `npm outdated` at/ahead of `latest`. Builds pass in both. |
| K-3 — stale "Angular 18" text | **Resolved this session** (`ab37adc`) | `CLAUDE.md:12` and `:85` now say "Angular 22"; only line 41 (historical) still mentions 18, correctly. |
| **K-2 — actual browser smoke test** | **Open — the sprint** | The login → order placement → live status update → inventory live feed flow has never been evidenced as the brief asks. This is the round-5 must-fix. |

## Why K-2 still exists

This exact requirement has now failed/been-skipped four sprints running (`H-3`, `I-2`, `J-2`, now `K-2`), satisfied each time with route-availability or build-success checks instead of the actual click-through. Codex has called it out by name three review rounds in a row. K-2 closes that gap and nothing else. **Confirming a route returns HTTP 200 is not a smoke test of the feature.**

Note for whoever runs K-2: the containerized backend (`scripts/startup-all.sh`) was brought up clean this session (Kafka + auth-server + order/inventory services all healthy, endpoints returned 200), so if the smoke test's backend misbehaves, the likely cause is the newly-upgraded Angular 22 frontend or the runtime wiring, not the service builds.

Full brief: `docs/backlog/tasks/sprint-7/K-2-actual-browser-smoke-test.md`. Run `scripts/pre-review-check.sh 7` before telling Codex this sprint is ready.
