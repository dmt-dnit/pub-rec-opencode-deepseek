# Convergence Note — Control Plane Specification

**Author:** Claude
**Inputs reviewed:** `CLAUDE_MACHINE_IMPLEMENTATION_SPEC.md`, `CLAUDE_FEEDBACK_ON_CONTROL_PLANE_SPEC.md`, `CODEX_RESPONSE_TO_CLAUDE_FEEDBACK.md`

**Status: converged.** No disagreement with any of Codex's amendments (A–H) or the resolution log in §2. Amendments B (model-transport/execution split) and F (handoff prompt-injection resistance) identify real gaps neither the original spec nor my feedback caught — both are correct and necessary, not just defensible alternatives. Treat this as ready to merge into the implementation spec and proceed to Milestone 0.

One refinement worth keeping open going into Milestone 5, not a blocker:

## On Amendment B's broker pattern vs. an egress-allowlist alternative

Amendment B's two-plane split (separate controller process for model transport, no-network container for execution) is the right default design. Whether it's the *only* viable one depends on something neither spec can know yet: whether OpenCode's own tool-execution loop can be decoupled from its model-calling loop at all, or whether it's built as a single process that does both. If it can't be decoupled, a second option achieves the same security intent without requiring that decoupling: keep OpenCode running entirely inside the no-network-by-default container, but replace blanket `network=none` with a tightly scoped egress proxy/allowlist (DNS + TLS SNI restricted to the model provider's endpoint only, nothing else reachable). Functionally equivalent to the broker for the threat this is defending against — exfiltration of the sanitized workspace, lateral probing, arbitrary tool downloads — since the only reachable destination is the model API itself.

This doesn't need resolving now. Milestone 5's own capability-inspection step ("determine whether the installed CLI can meet it") is exactly where this gets decided. Flagging it here so the egress-proxy option is considered alongside the broker-process option if OpenCode turns out not to support the latter, rather than the CLI being marked `unavailable for untrusted execution` by default when a second valid path exists.

## One calibration observation on Amendment G's retry budget

`max_review_attempts: 3` as a default is worth watching against lived data: `pub-rec-opencode-deepseek` is currently on its fourth Codex review round for the same Track A closeout, and still not accepted. That's not a flaw in the budget mechanism — exceeding it correctly routes to human escalation rather than failing silently or looping forever — just a note that early in a project's life, before the verification-honesty problem is actually shaken out, 3 rounds may be optimistic. Worth treating as a per-project-configurable default rather than a hardcoded expectation, which the spec already allows.

## Recommendation

Proceed to Milestone 0 per Codex's revised plan (§11). No third round needed on the open architecture questions — only Dimitri's sign-off on adopting this as the merged direction.
