# Sprint 21 Demo Notes

Sprint 21 is useful as a low-drama counterexample in the story: not every agent sprint is a rescue. This one is disciplined maintenance work, and the interesting part is the process control.

- The team cleared a backlog of dependency PRs without mixing in opportunistic code changes.
- The Angular bump matters because the first naive package subset caused a real peer-dependency conflict; the fix was to move the full Angular family in lockstep, not to bypass npm with `--force`.
- The review signal here is "clean, narrow, evidence-backed" rather than "heroic fix". That helps show the workflow can reject noisy diffs and also approve boring-but-correct maintenance work.
- Good conference framing: Sprint 21 is where the loop looks mature. The coordinator scoped explicit exclusions, the implementer stayed within them, and the reviewer only had to confirm there was no hidden drift.
