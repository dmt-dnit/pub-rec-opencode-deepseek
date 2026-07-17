# Sprint 35 — promote Snyk CI gate to blocking (Track C Phase 5, part 2)

**Track:** C — go-live, Phase 5. **Date:** 2026-07-17.

## Why this sprint exists

Sprint 34 pinned the 4 vulnerable transitive Maven dependencies that were the real
blocker for Snyk's CI gate. That's now live-confirmed, not just locally verified: the
CI run on Sprint 34's merge commit (`gh run view 29601892451 --log`) shows **every one
of the 7 Snyk targets** (4 Maven modules, 2 npm UIs, the e2e project) reporting
`no vulnerable paths found` — 0 issues, HIGH+ threshold, across the board. The
precondition this follow-up was gated on is genuinely met.

## What to change

`.github/workflows/ci.yml`, `snyk-security` job:

1. Remove the job-level `continue-on-error: true` (currently line 176).
2. Remove the step-level `continue-on-error: true` on the "Snyk test — all projects..."
   step (currently line 202) — this is the one that actually determines whether a
   future HIGH+ finding fails the build.
3. **Leave the SARIF-upload step's `continue-on-error: true` (currently line 209)
   alone** — that one exists so a hiccup uploading results to the Security tab doesn't
   fail the whole job; it's unrelated to the vuln-gating decision.
4. Update the surrounding comments (currently lines 158-163, the job `name:` on line
   172, and the inline comment on line 175) — they currently describe this as a
   deliberate report-only first pass with a stated reason to promote later. Rewrite
   them to reflect that this is now a blocking gate, and that the promotion happened
   in Sprint 35 after Sprint 34 cleared the actual blocker (the 4 Maven CVEs, not the
   originally-assumed dev-only Angular CVEs — that assumption was wrong, see Sprint 34).

## Explicitly out of scope

- No other CI job changes.
- No dependency version changes (Sprint 34 already did that).
- Do not touch the dev-only Angular `npm audit` caveat documented in `CLAUDE.md` — that
  remains real and unrelated to this gate (Snyk doesn't scan devDependencies by default,
  which is why it was never actually the blocker).

## Acceptance criteria (show real output, don't assert "Pass")

1. Show the actual diff of `.github/workflows/ci.yml`.
2. Confirm via `git diff` that only the 2 targeted `continue-on-error: true` lines were
   removed and the SARIF-upload one was left untouched.
3. `git status --short` clean after commit.

## Note on pushing this

Pushing changes under `.github/workflows/` needs the `workflow` OAuth scope, which this
repo's standing `gh` credential does not have — confirmed in an earlier sprint. Commit
the change on the worktree branch; the coordinator or Dimitri will handle the actual
push to `main` (`gh auth refresh -s workflow` or Dimitri pushing directly).
