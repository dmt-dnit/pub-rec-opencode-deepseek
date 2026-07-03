# Review machine header contract

`scripts/verify-review.sh` needs a machine-readable way to know, for any
`reviews/*.md` file, (a) exactly which commit the reviewer inspected and
(b) what the verdict was — without a human re-reading prose. This file is
the contract for that.

## The header

Every review file written under this contract MUST begin with a
two-line header block, before any other content:

```
Review-Target-Commit: <full-or-abbreviated sha the reviewer actually inspected>
Verdict: ACCEPT | REJECT
```

Rules:

- `Review-Target-Commit:` names the single commit the reviewer checked out /
  diffed against. If a review spans multiple commits (e.g. a range of
  handoff commits), use the **latest** one — the one whose descendants
  determine whether a later fix is covered.
- `Verdict:` is exactly `ACCEPT` or `REJECT` (extra words are fine, e.g.
  `Verdict: REJECT / not cleared`, `Verdict: ACCEPT — cleared`, as long as
  the line still contains one of those tokens unambiguously).
- The header must appear near the top of the file so a line-oriented parser
  (`grep -m1 '^Review-Target-Commit:'` / `grep -m1 '^Verdict:'`) finds it
  without scanning the whole document.

## The handoff also carries the target commit

For the no-argument form `scripts/verify-review.sh <sprint-number>` to work,
the sprint **handoff** must tell the script which commit the round was
supposed to cover. Add the same field to
`docs/backlog/sprint-<N>-handoff.md`:

```
Review-Target-Commit: <sha of the fix commit this review round should cover>
```

Rationale: "expected" means *the fix commit the review round was meant to
inspect*, not current `HEAD`. `HEAD` drifts forward as later work lands, so
defaulting expected to `HEAD` would make an genuinely-fresh review read as
STALE. The script therefore resolves the expected commit in this order:

1. explicit second CLI arg, if given;
2. else `Review-Target-Commit:` in the sprint handoff (a legacy freeform
   line mentioning `target commit` with a backticked sha is also tolerated);
3. else **UNKNOWN / exit 4** — it never silently falls back to `HEAD`; it
   tells the caller to pass the commit or add the header.

## How `verify-review.sh` uses it

Given `scripts/verify-review.sh <sprint-number> [expected-commit]`:

1. It finds the newest review file for that sprint (highest `round-<k>` in
   the filename beats the base file; the base file counts as round 1).
2. It extracts `REVIEWED_SHA` from `Review-Target-Commit:` and classifies
   `Verdict:` into `ACCEPT` / `REJECT` / `UNKNOWN`. A standalone token value
   (`Verdict: ACCEPT` / `Verdict: **REJECT**`) is trusted verbatim and beats
   any prose heuristic. For legacy freeform prose, the classifier first
   neutralizes **negated** blocker phrases (e.g. "cleared; no remaining
   source-level blockers found" is an ACCEPT, not a REJECT) before
   keyword-matching, so an incidental negated "blocker" mention can't flip a
   clean accept to a reject.
3. It resolves the commit to check freshness against: `expected-commit` if
   given, else `HEAD`.
4. **Freshness is decided by commit ancestry, not by prose or dates:**
   `git merge-base --is-ancestor <expected> <REVIEWED_SHA>`. If the expected
   commit is an ancestor of the reviewed commit, the review covers it
   (FRESH). If not — including if `REVIEWED_SHA` can't be parsed or isn't a
   known commit — the review is **STALE** and its verdict is not trusted,
   regardless of what the verdict says.
5. Exit codes: `0` = FRESH+ACCEPT, `2` = STALE, `3` = FRESH+REJECT (a real,
   trustworthy blocker), `4` = UNKNOWN/parse failure.

See `scripts/verify-review.sh` itself (and
`docs/backlog/tasks/sprint-20/AUTO-2-verify-review-freshness.md`) for the
full behavioral spec, including the secondary (non-authoritative) mtime-vs-
handoff warning it also prints.

## Legacy freeform fallback

Review files written before this contract existed (all of Sprints 1–19)
don't have the two-line header. For those, `verify-review.sh` falls back to
parsing the freeform line that was already in use:

```
Review target: `<sha>`
```

including the variant that lists more than one backticked sha (the first
one is taken):

```
Review target: `<sha1>`, `<sha2>`
```

and looks for a line starting with `Verdict:` anywhere in the file (e.g.
`Verdict: **REJECT / not cleared**`) for the verdict text. This fallback is
best-effort only — it exists so `verify-review.sh` can still evaluate the
Sprint 1–19 review archive, not as a long-term substitute for the header.
New reviews should use the two-line header above.

## Who owns emitting this header

The header line format above is the repo-side contract only. Making the
Codex review-prompt itself actually emit `Review-Target-Commit:` /
`Verdict:` at the top of every review it writes is a change to Dimitri's
Codex-app review prompt/dispatch config, not to this repo's source — it is
out of scope for `verify-review.sh` and is not implemented by this task.
Until that prompt change lands, every review file `verify-review.sh`
encounters will go through the legacy freeform fallback above.
