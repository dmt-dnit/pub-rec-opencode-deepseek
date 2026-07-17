# Sprint 35 Handoff — promote Snyk CI gate to blocking (Track C Phase 5, part 2)

**Coordinator:** Claude Code. **Implementer:** opencode+DeepSeek (worktree). **Date:** 2026-07-17.
Review-Target-Commit: 0bf676c

## What was done

`.github/workflows/ci.yml`'s `snyk-security` job: removed the job-level and
step-level `continue-on-error: true` (the two that gated whether a HIGH+ finding
actually fails the build), leaving the unrelated SARIF-upload step's
`continue-on-error: true` untouched. Job `name:` and surrounding comments updated to
describe this as a blocking gate and explain why now (Sprint 34 cleared the real
blocker — 4 Maven CVEs — and the originally-assumed dev-only Angular CVE precondition
was never actually the thing gating Snyk, since it excludes devDependencies by
default).

## Coordinator verification

- Reviewed the actual diff directly (`git show bace9cd` before cherry-pick): exactly
  the 2 targeted `continue-on-error: true` lines removed, comments/job-name updated
  accurately, nothing else touched.
- `grep -n "continue-on-error" .github/workflows/ci.yml` post-change: exactly one match
  remaining, on the SARIF-upload step (line 213) — confirms the untouched line is
  genuinely the right one, not an accidental leftover.
- This gate's actual effectiveness was already live-proven *before* this sprint: the
  CI run on Sprint 34's merge commit (`29601892451`) shows all 7 Snyk targets
  (`auth-server`, `order-service`, `inventory-service`, `shared-model`, `order-ui`,
  `inventory-ui`, the e2e project) reporting `no vulnerable paths found` at the same
  HIGH+ threshold this gate now enforces — so flipping it to blocking has zero risk of
  immediately redding the next CI run.

## Explicitly out of scope

- No dependency version changes (Sprint 34's job).
- No other CI job changes.

## Pushing this

**This commit has not been pushed yet.** `.github/workflows/` changes need the
`workflow` OAuth scope, which this session's `gh` credential does not have (confirmed
in an earlier sprint — `git -c credential.helper='!gh auth git-credential' push` will
fail on a workflow-file change). Options: Dimitri pushes this commit directly, or runs
`gh auth refresh -s workflow` so the coordinator's existing credential can push it.

## What Codex should check independently

Confirm the SARIF-upload step's `continue-on-error: true` genuinely should stay (it
should — that step uploads results to the GitHub Security tab and a hiccup there
uploading shouldn't fail the whole build even though the vuln-gating decision itself is
now blocking), and that no other job in `ci.yml` implicitly depends on
`snyk-security`'s prior always-green behavior (the job was never in any other job's
`needs`, so this should be a no-op check, but worth confirming directly rather than
assuming).
