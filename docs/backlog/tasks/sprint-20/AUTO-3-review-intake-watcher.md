# AUTO-3 — Review-intake watcher: detect a landed verdict and drive verify-review

**Sprint:** 20 (Track B Sprint 7) — candidate
**Type:** Coordinator-side repo code (bash script) + operational wiring note
**Implementer:** dispatched agent (worktree). Coordinator verifies diff + self-test + real-repo output.

## Why this exists

`verify-review.sh` (AUTO-2) *validates* a verdict, but nothing *invokes* it when a
verdict arrives. That 3→5 edge of the loop —

1. coordinator writes handoff →
2. Codex-app dispatcher sends it *(AUTO-1 fixes re-fire)* →
3. Codex writes `reviews/sprint-N-…-review.md` to disk →
4. **← nothing watches here** →
5. run `verify-review.sh` *(AUTO-2)* →
6. act on the exit code

— has been **human-driven**. Proof: Codex's genuine Sprint 19 round-2 ACCEPT was
written `2026-07-01 17:58` and then sat **untracked for ~2 days**, invisible, until a
human noticed and pointed the coordinator at it. Two smaller symptoms of the same gap:
the review + `demo-notes-sprint-N.md` files just sit **uncommitted** (Codex can't push),
and on a STALE verdict the re-request is manual too.

AUTO-3 closes this edge: a coordinator-side **intake** step that detects a new/updated
review file, runs `verify-review.sh`, and takes the right action per exit code.

**Relationship to the others:** AUTO-1 fixes the *outbound* re-fire (Codex gets asked on
an updated handoff). AUTO-3 fixes the *inbound* intake (the coordinator notices Codex's
answer). Together they close both directions of the auto-review loop. AUTO-3 **reuses**
`scripts/verify-review.sh` — do not reimplement freshness/verdict logic.

## Deliverables

### 1. `scripts/review-intake.sh` (new, mode 100755)

A single deterministic **one pass** (the recurring driver is operational — see wiring
note; the script itself must NOT loop, sleep, push, or notify beyond stdout).

Contract:

```
scripts/review-intake.sh            # one intake pass over reviews/
scripts/review-intake.sh --selftest
```

Behavior of a pass:

1. **Detect unprocessed reviews.** For each `reviews/sprint-<N>-*review*.md`, compute a
   signature = `path + content-hash` (e.g. `git hash-object` or `sha1sum`). Compare to a
   **git-ignored ledger** `.review-intake-state` (one line per review: `path<TAB>sig<TAB>outcome`).
   A review is *unprocessed* if its signature is absent from the ledger or differs from
   the recorded one (i.e. new file, or an updated round). Unchanged, already-recorded
   files are skipped. Use content-hash, not mtime, so a `git checkout`/clone that resets
   mtimes doesn't cause false reprocessing.
2. **For each unprocessed review**, resolve its sprint number `N` from the filename and
   run `scripts/verify-review.sh <N>` (no explicit commit arg — it resolves
   `Review-Target-Commit:` from the handoff). Branch on its exit code:
   - **0 FRESH+ACCEPT** → `git add` + commit the review file **and** the matching
     `docs/demo-notes-sprint-<N>.md` if present (message: `docs(review): land Codex sprint <N> verdict (intake)`); print `CLEARED sprint <N>`; record outcome `cleared`.
   - **3 FRESH+REJECT** → commit the review file (it's the record of the reject) but do
     **not** close; print `REJECTED sprint <N> — surface blockers to next sprint backlog`
     and echo the review's findings section; record `rejected`.
   - **2 STALE** → do **not** commit; print `STALE sprint <N> — reviewed <X> vs expected
     <Y>; re-request a fresh review (AUTO-1 dedup symptom)`; record `stale`.
   - **4 UNKNOWN** → do **not** commit; print `UNKNOWN sprint <N> — fix handoff
     Review-Target-Commit / verdict metadata`; record `unknown`.
   Recording the outcome+signature in the ledger prevents re-alerting every pass, while a
   later signature change (an updated review) re-triggers intake.
3. **Print a summary line**: `intake: <c> cleared, <r> rejected, <s> stale, <u> unknown, <k> skipped`.
   Exit **0** if the pass completed cleanly (regardless of individual verdicts — a reject
   is a successful *intake*); exit non-zero only on an internal error (e.g. can't run
   verify-review.sh). Do **not** conflate "found a reject" with "script failed."
4. **Never** `git push` and **never** run an infinite loop from this script. Pushing and
   scheduling are the operational wrapper's job (safe-agent-operations: don't bake an
   autonomous push into a self-firing script).

Add `.review-intake-state` to `.gitignore`.

### 2. `--selftest`

Throwaway fixtures (temp dir + throwaway `git init` repo, a fake `reviews/`, fake
handoffs with `Review-Target-Commit:`, a stub `verify-review.sh` **or** the real one
pointed at the fixture tree — your call, but assert real behavior). Prove:
- new review → processed, correct action per each exit-code branch (0/2/3/4);
- unchanged review on a second pass → **skipped** (ledger idempotency);
- updated review (content changed) → **reprocessed**;
- ACCEPT branch actually commits the review + demo note in the fixture repo;
- STALE/UNKNOWN branch does **not** commit.
Print `SELFTEST PASS`/`SELFTEST FAIL`, exit non-zero on any failure.

## Acceptance criteria (observable — show command output)

1. `bash scripts/review-intake.sh --selftest` → `SELFTEST PASS`, exit 0.
2. On the **real repo** (sprint-19 review already committed in `2d7f427`): a pass reports
   sprint 19 as **skipped** (already processed / unchanged) — i.e. idempotent, no
   re-commit, no duplicate action. Show output.
3. Run the real pass **twice**; the second is a clean no-op. Show both.
4. `git ls-files -s scripts/review-intake.sh` → mode **100755**.
5. `.gitignore` contains `.review-intake-state`.

## Constraints

- Only add `scripts/review-intake.sh`, the `.gitignore` line, and any doc note. Do **not**
  modify `verify-review.sh` (reuse it) or existing review files.
- Bash on Linux (coordinator/CI env). `set -euo pipefail`. git + coreutils only.

## Operational wiring note (decided at wire-time, not part of this script)

The recurring driver that calls `review-intake.sh` on an interval, then **pushes** any
commits it made and **notifies** on cleared/stale/reject, is separate — a coordinator
`/loop` or a scheduled cloud agent. Keep push + notify + scheduling there, so the
detection/verify/commit core stays deterministic and testable. Poll cadence is an
operational choice (a review lands on Codex-time, not sub-minute — minutes-scale polling
is fine). Do not wire the driver as part of AUTO-3 without Dimitri's sign-off.

## Related

- `AUTO-1-codex-review-dedup-round-aware.md` (outbound re-fire), `AUTO-2-verify-review-freshness.md` (verify core, reused here).
- Trigger case: `reviews/sprint-19-track-b-round-2-review.md` sat untracked ~2 days.
- CLAUDE.md cadence step 4.5 + coordinator rule 11 (the manual step AUTO-3 automates).
