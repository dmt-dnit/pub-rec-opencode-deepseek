# D-1 — CI Actions version bumps

**Sprint:** 21. **Type:** CI workflow config. **Implementer:** opencode+DeepSeek (worktree).

## Why this exists
Dependabot flagged two GitHub Actions used in `.github/workflows/ci.yml` as behind
current major releases:
- `actions/checkout@v5` → `v7` (PR #3, open)
- `actions/upload-artifact@v4` → `v7` (PR #1, closed unmerged — still needs doing;
  workflow is still on v4)

`actions/setup-java@v5` and `actions/setup-node@v6` are already current — leave them.
`actions/cache` is not referenced in the workflow at all — nothing to do there.

## Deliverables
In `.github/workflows/ci.yml`:
1. Bump every `uses: actions/checkout@v5` → `uses: actions/checkout@v7`.
2. Bump every `uses: actions/upload-artifact@v4` → `uses: actions/upload-artifact@v7`.
3. Leave `setup-java@v5`, `setup-node@v6`, and everything else untouched.

Check each action's own changelog for that major jump for any breaking input/output
changes relevant to how this workflow calls it (e.g. `upload-artifact` v4→v7 changed
default retention/compression behavior in past majors — confirm nothing here relies on
removed inputs). If you find a breaking change that affects this workflow's usage, note
it in your report rather than silently working around it.

## Acceptance criteria (observable outcomes)
1. `grep -n "actions/checkout\|actions/upload-artifact" .github/workflows/ci.yml` shows
   only `@v7` for both actions, no other action versions touched.
2. YAML is well-formed (`python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml'))"`
   or equivalent) — show the actual output.
3. If `actionlint` is available, run it and show output; if not available, say so
   explicitly rather than skipping the check silently.
4. This can only be fully proven green on a live CI run (push). Say so explicitly in
   your report — don't claim "Pass" from local reasoning alone.

## Related
[[feedback-ci-maven-sharedlib-inline]] — pushing workflow file changes needs the
`workflow` OAuth scope; the coordinator handles the actual push, not the implementer.
