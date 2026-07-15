# Sprint 21 — coordinator addendum (for the conference agent)

Codex's `docs/demo-notes-sprint-21.md` covers the dependency-currency work itself. This
addendum covers what happened *after* that review landed — two beats that happened in
the coordinator/Dimitri conversation, not in the diff, and that Codex's own notes
couldn't see because they were written before this conversation happened.

## Beat 1 — Dimitri caught the coordinator cutting a corner, live

The coordinator merged Sprint 21's first task straight onto `main` before Codex had
reviewed it — the same thing it had quietly done for 20 prior sprints. Dimitri noticed
mid-session and asked, plainly: "we don't merge feature branches before they're
reviewed at work, what's your take?" That's a good beat because it's not staged — it's
a real case of a human catching an AI assistant repeating an unexamined habit and
asking it to justify itself.

The coordinator's honest answer: yes, this had been the pattern for 21 sprints, and it
was a real, deliberate trade (this repo is staging-grade, a human was watching every
push, and two prior reject-recoveries — Sprint 16, Sprint 19 — had been cheap and
same-cycle). But it named the actual risk plainly: that trade stops being cheap the
moment the in-progress AUTO-3 operational driver starts pushing to `main` unattended,
because that's the first case where nobody is watching. Dimitri pushed back on making
the fix too broad ("we do it like this at work... but if you feel that is not so
necessary, push back and we'll compromise"), and the two landed on a narrowed policy:
supervised sprint work keeps going straight to `main`; only the driver's future
unattended pushes get a review-branch gate. Written into `CLAUDE.md` under "Merge gate:
direct-to-main vs. review-branch."

**Why it's demo-worthy:** it's the opposite of a scripted "AI does everything right"
moment. It shows a human noticing an AI habit that only looks fine because nothing bad
had happened *yet*, asking for the reasoning behind it, and the two of them converging
on a scoped fix instead of over-correcting into ceremony the project doesn't need yet.

## Beat 2 — the automation's own reviewer didn't follow the automation's own contract

Sprint 21's Codex review was substantively a clean accept — it verified all three tasks
against the handoff with file:line citations and independently re-ran `npm audit
--omit=dev`. But its `Verdict:` line read `**No blocking findings**` instead of a line
containing the literal `ACCEPT`/`REJECT` token that `docs/backlog/review-machine-header.md`
(the AUTO-2 contract, written two sprints ago specifically to make verdicts machine-
parseable) requires. `scripts/verify-review.sh` correctly refused to guess and returned
exit 4 (UNKNOWN) rather than silently treating prose as an accept.

The coordinator did not "fix" Codex's verdict line itself — doing so would mean the
coordinator deciding the verdict on the reviewer's behalf, defeating the point of having
an independent reviewer. Instead Dimitri read the content, gave an explicit human
override to ACCEPT, and that override is logged in `CLAUDE.md`'s status snapshot rather
than presented as if the mechanical gate had passed.

**Why it's demo-worthy:** it's a concrete example of "the automation checks its own
work, and when the automation itself has a gap, a human closes it explicitly instead of
the system quietly self-certifying." Good contrast to the Sprint 19 stale-false-reject
story (`docs/story.md`) — that one was the *reviewer* automation failing silently; this
one is the *verdict-parsing* automation failing loudly and correctly, by design.

## Where these fit in the existing narrative

Both beats slot naturally after the Sprint 19 stale-false-reject material in
`docs/story.md`'s "what went wrong" section and `docs/talk-outline.md` — they're the
next chapter of the same theme (the loop gets more honest and more mechanically
verified with each sprint, not just more automated).
