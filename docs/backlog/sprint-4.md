# Sprint 4 backlog: Track A close-out, round 2

**Source:** Codex's third-round review — `reviews/sprint-3-track-a-review.md`, dated 2026-06-20/22. Verdict: **reject**. Three must-fix blockers (`G-1`, `G-3`, `G-4` all scored `FAIL`), two should-fixes (`G-2` `PARTIAL`, `G-5` `FAIL`), confirmed by re-reading every cited file/line before writing this backlog.

## Why this sprint exists

Sprint 3 made real backend progress — `order-service` now passes on OpenJ9, the embedded-Kafka-in-process crash is gone — but every fix aimed specifically at making Codex's review environment usable still falls short in practice, and `CLAUDE.md` got updated to claim success before that was actually true. This sprint is about closing the gap between "the fix looks right by inspection" and "the fix actually works when Codex runs it," which is exactly the gap that's caused three rejections in a row now. Verify by *running* things in the target environment wherever possible, not by matching a known-good template and assuming it behaves identically.

Track B (`docs/backlog/sprint-1.md` — note the correct file; Sprint 3's `CLAUDE.md` update wrongly pointed this at `sprint-3.md`, see H-5) remains gated until this sprint is verified and re-reviewed clean.

## Tasks

Recommended order: **H-1 and H-2 first** (both are "the fix doesn't actually work in the reviewer's environment" bugs, disjoint files) → **H-4 in parallel** (different module, Mockito classpath investigation) → **H-3 anytime in parallel, but budget real time for it** (the Angular major upgrade is the biggest single task in this backlog and has been deferred twice already) → **H-5 last** (doc accuracy pass, only meaningful once the above are actually true).

| Task | What | Severity it resolves |
|---|---|---|
| H-1 | Fix the actual runtime bug in `mvnw.cmd` (`Cannot index into a null array` / `Cannot start maven from wrapper`) | Must-fix 1 |
| H-2 | Make `inventory-service`'s Testcontainers test skip gracefully (not fail the build) when no Docker/Podman engine is available | Must-fix 2 |
| H-3 | Execute the Angular major-version upgrade for real (both UIs) — no more re-running `npm update` and reporting "unchanged" | Must-fix 3 |
| H-4 | Find why `order-service`'s Mockito subclass-mock-maker override isn't taking effect at runtime | Should-fix 1 |
| H-5 | Rewrite `CLAUDE.md`'s status snapshot to match this review's actual scorecard, and fix the Track B file pointer | Should-fix 2 |

Each task's full brief is in `docs/backlog/tasks/sprint-4/`. Track B does not start until all five are merged and Codex's next review scores every one of `G/H` `PASS` — not "looks right," actually exercised in the reviewer's environment.
