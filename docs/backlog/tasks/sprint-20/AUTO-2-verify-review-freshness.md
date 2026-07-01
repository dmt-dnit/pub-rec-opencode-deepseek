# AUTO-2 — Close the loop: mechanical stale-review detection

**Sprint:** 20 (Track B Sprint 7) — candidate
**Type:** Coordinator-side repo code (bash script) + review-header spec doc
**Implementer:** dispatched agent (worktree). Coordinator verifies diff + self-test output.

## Why this exists

Sprint 19 round-1 produced a **stale false-reject**: Codex "resumed" an old thread and
re-stated round-1 findings against pre-fix code, giving a REJECT that did not reflect
the pushed fix (`9b8216e`). The coordinator did **not** catch this proactively — there
is no trigger when a review lands, so trustworthiness of a verdict rode on a human
noticing something felt off. Every tell was mechanical, though:

- review file mtime **predated** the handoff it claimed to review;
- findings cited **line numbers absent** from the fixed file;
- `grep <fix-sha> review.md` → **0** (a real review of that commit names it);
- **no round-2 file existed** — only the round-1 filename.

This task makes staleness **scriptable and fail-loud** so a bad verdict is caught in
seconds instead of by luck. It is the precondition for running a review round
unattended.

## Deliverables

### 1. `scripts/verify-review.sh` (new, mode 100755)

Contract:

```
scripts/verify-review.sh <sprint-number> [expected-commit]
scripts/verify-review.sh --selftest
```

Behavior for the `<sprint-number>` form:

1. **Locate the newest review** for the sprint: glob `reviews/sprint-<N>-*review*.md`,
   pick the highest round (a file whose name contains `round-<k>` outranks the
   base file; among `round-<k>` pick max k; base file = round 1). Print which file
   was chosen.
2. **Parse the machine header** (see deliverable 2). Extract:
   - `Review-Target-Commit:` → `REVIEWED_SHA` (also accept the legacy freeform
     `Review target:` line with a backticked sha, for pre-spec files).
   - `Verdict:` → `VERDICT_LINE`. Classify: **ACCEPT** if it matches
     `accept|cleared|approved` (case-insensitive) and does **not** also match
     `reject|not cleared|blocker`; **REJECT** if it matches `reject|not cleared|blocker`;
     else **UNKNOWN**.
3. **Determine expected commit:** use `$2` if given; else default to
   `git rev-parse HEAD`.
4. **Freshness (authoritative = commit ancestry):**
   - `git merge-base --is-ancestor <expected> <REVIEWED_SHA>` → exit 0 means the
     reviewed commit **contains** our fix ⇒ **FRESH**.
   - exit 1 ⇒ reviewed commit predates/excludes the fix ⇒ **STALE**.
   - If `REVIEWED_SHA` can't be parsed or isn't a known commit ⇒ **STALE**
     (can't prove freshness = don't trust).
5. **Secondary staleness signal (warn, don't override):** if the review file mtime
   ≤ the handoff mtime (`docs/backlog/sprint-<N>-handoff.md`), print a WARN line.
   Commit ancestry is authoritative; this is a human-readable corroboration.
6. **Output + exit codes** (so automation can branch):
   - print a one-line summary: `sprint <N>: <FRESH|STALE> — verdict <ACCEPT|REJECT|UNKNOWN> — reviewed <sha> vs expected <sha> — file <path>`
   - exit **0** = FRESH + ACCEPT
   - exit **2** = STALE (verdict not trustworthy — re-request review)
   - exit **3** = FRESH + REJECT (genuine blocker — act on it)
   - exit **4** = UNKNOWN / parse failure
   - Use `set -euo pipefail`; keep it POSIX-bash, no non-standard deps (git + coreutils only).

Behavior for `--selftest`: build throwaway fixtures in a temp dir / temp git repo and
assert all four exit paths (STALE, FRESH+ACCEPT, FRESH+REJECT, UNKNOWN). Print
`SELFTEST PASS` / `SELFTEST FAIL` and exit non-zero on any failure. This is the
evidence the coordinator will read — it must run green with **no** live Codex review
present.

### 2. `docs/backlog/review-machine-header.md` (new)

Short spec: every Codex review file MUST begin with a machine-readable header block:

```
Review-Target-Commit: <full-or-abbrev sha the reviewer actually inspected>
Verdict: ACCEPT | REJECT
```

Document that `verify-review.sh` parses these; note the legacy freeform fallback the
script tolerates for pre-spec reviews; and note that the Codex review-prompt change to
emit this header is **Dimitri's** (Codex app), tracked here as the repo-side contract.

## Acceptance criteria (observable — show command output)

1. `bash scripts/verify-review.sh --selftest` prints `SELFTEST PASS`, exit 0.
2. `bash scripts/verify-review.sh 19` against the real repo prints **FRESH — verdict
   ACCEPT** (the round-2 file `reviews/sprint-19-track-b-round-2-review.md` targets
   `9b8216e`, which is in HEAD), exit 0. Show the output.
3. Simulate stale: `bash scripts/verify-review.sh 19 <a-commit-AFTER-the-review's-target>`
   (e.g. current HEAD if it is newer than the reviewed sha) — actually demonstrate the
   STALE path with a commit the review's target does NOT contain; exit 2. Show output.
4. `git ls-files -s scripts/verify-review.sh` shows mode **100755**.
5. `docs/backlog/review-machine-header.md` exists and documents the two header lines +
   legacy fallback.

## Constraints

- No changes outside `scripts/verify-review.sh` and `docs/backlog/review-machine-header.md`.
- Do not modify existing review files, `startup-all.sh`, or any service code.
- Script must run on Linux bash (this repo's CI/coordinator env); avoid GNU-only flags
  where a portable form exists, but GNU coreutils is acceptable (that's the CI env).

## Related

- Stale false-reject write-up: CLAUDE.md snapshot (2026-07-01), commit `b040b00`.
- Companion automation-hardening brief: `AUTO-1-codex-review-dedup-round-aware.md`.
- Real fresh accept this closes around: `reviews/sprint-19-track-b-round-2-review.md`.
