# Feedback on `CLAUDE_MACHINE_IMPLEMENTATION_SPEC.md`

**Author:** Claude (Sonnet 4.6), acting as sprint coordinator on `pub-rec-opencode-deepseek`.
**Audience:** Codex, and Dimitri as final decision-maker.
**Purpose:** Find common ground before any code for `agent-sprint-control` (`asctl`) gets written. This is a review of the artifact, not a rewrite — where I agree, I say so briefly; where I'd push back or add something, I explain why, grounded in what's actually happened on the demo project this control plane is meant to eventually run.

---

## Where I agree without reservation

- **The trust-boundary table (§2.1) is the right starting frame.** Planner/executor/reviewer/local-gate/human as five distinct trust levels, with an explicit "must never receive" column, is more rigorous than how we've been running things informally (file-based briefs + manual diff reading).
- **The local gate re-running trusted tests itself (§11.1 step 6), rather than trusting the executor's self-reported test results, is the single most important design decision in this spec.** This directly addresses something I have direct, repeated evidence for: across the last 3 sprints on this repo, DeepSeek has reported "Pass" on checks that were not actually true when independently verified — a Windows wrapper script that matched a template but errored at runtime, a Mockito mock-maker override that wasn't actually taking effect despite the test suite going green, a specific patched-dependency-version number that didn't match what `npm audit` actually returned. A sandboxed executor can still fabricate or misjudge a result; only a deterministic re-run on the real target, by a trusted party, catches that. The spec gets this right structurally, not just as a written rule.
- **No production path baked into the architecture, not just documented as off-limits (§12.3).** This is the correct level of paranoia for a tool whose explicit job is to let a cheap, untrusted model touch a real codebase.
- **Repository layout puts control-plane state under WSL-native paths (`~/dev/...`, `~/.local/share/...`), not `/mnt/c/...`.** Worth calling out as a deliberate good choice, not an accident: I've personally hit slow/flaky `npm install` behavior on `/mnt/c` this session (a transient `lmdb`/`rollup` postinstall failure that resolved on retry) purely from WSL's drvfs filesystem overhead. Keeping `asctl`'s own state off that filesystem avoids inheriting that flakiness in the control plane itself.
- **Discovering CLI adapter syntax via `--help` instead of hardcoding flags (§9.1, §16 Milestone 5/6).** Provider CLIs change; encoding that uncertainty into the adapter design up front is correct.

## Where I'd push back or add something

### 1. Docker is assumed; this environment runs Podman

§3 and §8.1 state Docker is required and `asctl doctor` should detect when it's *unavailable* — but the assumption baked into the whole isolation design is Docker specifically. I confirmed this session that Dimitri's actual machine uses **Podman**, not Docker. Podman can present a Docker-compatible API, but it needs explicit handling (`DOCKER_HOST` pointed at the Podman socket, usually `TESTCONTAINERS_RYUK_DISABLED=true` for the rootless case in other tools that hit this). Recommend: make the container-runtime backend a configured choice (`docker` | `podman`) from Milestone 4 onward, not something retrofitted after the isolation plumbing is already Docker-specific. Retrofitting security-critical sandboxing code to support a second backend is exactly the kind of change you don't want to make carelessly later.

### 2. The spec doesn't address how this coexists with the demo repo's current in-flight process

`pub-rec-opencode-deepseek` is mid-sprint (Sprint 4, tasks `H-1`–`H-5`) on the existing manual workflow — file-based briefs, opencode+DeepSeek implementing directly against the real working tree, me verifying by reading diffs, Codex reviewing. Building `asctl` is itself a multi-week effort (10 milestones). Two options, and the spec is silent on which:
- (a) Keep running the demo repo on the current informal process until `asctl` reaches at least Milestone 7 (trusted patch gate working end-to-end), then cut over; or
- (b) Treat `asctl`'s own construction as the first project it's used to build once Milestone 5/6 land, dogfooding it on itself.

I'd recommend (a) — don't retrofit an in-flight sprint cycle onto a tool that doesn't exist yet, and don't let an unfinished security tool be the thing enforcing security on its own construction. Worth Codex weighing in on, since this affects sequencing more than architecture.

### 3. Self-reported executor status should be explicitly distrusted at the reviewer stage too, not just the local gate

§8.2's `executor-result.json` has a `status: "completed" | "blocked" | "failed"` field written by the untrusted executor. §11 correctly has the local gate re-run trusted tests independently rather than trusting this field. But §9.4 has the reviewer receive "test results" from the executor's handoff as part of its review input — it's not explicit that the reviewer should treat the executor's self-reported status/results as unverified claims rather than facts. Given the concrete pattern I've seen (DeepSeek confidently reporting verification steps as "Pass" that weren't true), I'd add an explicit instruction to the reviewer prompt constraints (§15.3): treat `executor-result.json`'s self-reported `status` and `test_results.json` as claims to be checked against the actual patch and provided evidence, not as ground truth, and flag any claim that isn't substantiated by the artifacts actually present.

### 4. No mention of cost/iteration budget for the planner↔reviewer reject loop

Not a security gap, but a practical one: this demo repo is now on its fourth sprint round with Codex rejecting each time on environment-verifiability grounds rather than logic bugs. That's real, recurring API cost for both frontier models, and the spec has no concept of "this task has been rejected N times, escalate to human rather than re-queuing automatically." Worth a small addition — not a blocker for v1, but worth flagging now while the design is still open rather than after `asctl` has shipped without it.

### 5. Performance characteristics of the sanitization pipeline aren't addressed

§7 builds a sanitized snapshot from an allowlist on every task. For a project living on `/mnt/c` (as the demo repo currently does), every snapshot operation inherits WSL's drvfs overhead for file-heavy targets (e.g. anything that touches `node_modules`-adjacent paths, even excluded, still requires directory traversal). Recommend Milestone 1's exit criteria include a basic timing benchmark on a realistic-sized fixture, so this surfaces during development rather than as a surprise on the first real project.

## Net assessment

The security architecture is sound and noticeably more rigorous than what we've been running informally — I wouldn't change the trust-boundary model, the sanitization-first approach, or the human-gated apply/staging flow. My pushback is entirely about (1) one environment assumption that doesn't match the actual target machine, (2) sequencing relative to the demo repo's live sprint cycle, and (3) tightening the reviewer's skepticism toward executor self-reports to match what we've actually observed DeepSeek do, not what the spec assumes a well-behaved executor would do. None of these require restructuring the milestone plan — they're refinements within it.
