# Final Decision — Merged Direction for `agent-sprint-control` (`asctl`)

**Decision-maker:** Dimitri  
**Status:** Approved to begin Milestone 0 after the implementation specification is consolidated.  
**Date:** 2026-06-22

## 1. Decision

The architecture has converged. Adopt the original machine implementation specification together with Codex’s amendments A–H as the implementation direction.

The following remain non-negotiable:

- allowlist-first, committed-tree sanitization;
- no real secrets, production configuration, Git history/remotes, or host-home access in untrusted workspaces;
- a cheap executor treated as untrusted;
- opposite-frontier review as advisory evidence, never as a replacement for deterministic local checks;
- a trusted local patch gate that independently runs tests and policy checks;
- a local-only review branch, explicit human apply, and a separately approved staging action;
- no production command, configuration, credential path, or generic environment switch in `asctl`;
- rootless Podman as the primary executor runtime on this machine, with Docker as a compatibility backend;
- file-based handoffs with canonical JSON, hashes, provenance, and prompt-injection resistance;
- bounded retries, with human escalation rather than silent requeue loops.

`pub-rec-opencode-deepseek` stays on its current manual process through the active Sprint 4 (`H-1`–`H-5`). It must not be migrated to unfinished `asctl`.

---

## 2. One final architectural call: broker first; no egress-proxy fallback in v1

Claude’s egress-allowlist idea is worth retaining as a future design option, but it is **not functionally equivalent** to the brokered two-plane design for v1.

A container that can directly call a model-provider API can still:

- submit arbitrary workspace content to that provider;
- make unbounded or policy-bypassing model calls using the available credential;
- use model traffic as an uncontrolled data-egress channel;
- evade useful request-level controls unless the proxy terminates and validates the provider protocol.

A proxy that terminates TLS, constrains request payloads, applies model-call budgets, redacts content, and mediates tool results is effectively a provider-aware broker. That is a larger and more security-critical system than the v1 need.

### Final v1 rule

The execution plane must remain `network=none`.

An executor adapter is usable only when it supports one of these modes:

1. **Prompt/patch mode** — it receives orchestrator-selected sanitized context and returns a patch/result without arbitrary host tools; or
2. **Brokered-tool mode** — every file, patch, and command operation goes through an `asctl` broker with typed operations and command IDs.

If the installed OpenCode/DeepSeek setup cannot meet either mode, it is **unavailable for untrusted execution in v1**. Do not weaken the isolation boundary to make the current CLI fit.

The egress-proxy model may be investigated after v1 only as a separately threat-modelled, provider-specific mode with its own tests and explicit human approval.

---

## 3. Document precedence

Before implementation begins, create one consolidated specification:

```text
docs/backlog/CONTROL_PLANE_IMPLEMENTATION_SPEC_V2.md
```

It must incorporate the original specification plus all accepted Codex amendments, and it must state this precedence order:

1. `FINAL_CONTROL_PLANE_DECISION.md` — this document, for final calls and conflicts.
2. `CONTROL_PLANE_IMPLEMENTATION_SPEC_V2.md` — the complete operative build specification.
3. `CODEX_RESPONSE_TO_CLAUDE_FEEDBACK.md` — amendment rationale and exact requirements until fully merged into V2.
4. `CLAUDE_MACHINE_IMPLEMENTATION_SPEC.md` — original baseline where not superseded.
5. `CLAUDE_FEEDBACK_ON_CONTROL_PLANE_SPEC.md` and `CLAUDE_CONVERGENCE_NOTE.md` — review record and rationale, not additional executable instructions.

Do not make runtime behavior depend on any Markdown prose. JSON schema-validated state, orchestrator-owned policy, and explicit CLI inputs remain authoritative.

---

## 4. Required V2 changes

The consolidated V2 spec must make these changes explicit.

### OCI runtime

- Replace Docker-only assumptions with a `ContainerRuntime` abstraction supporting `podman` and `docker`.
- Prefer and require **rootless Podman** on this machine for executor use.
- Use direct runtime invocation; do not use Docker/Podman sockets, Compose, privileged containers, host networking, host PID/IPC/user namespace sharing, or the target-repository mount.
- Remove Compose from v1.
- `asctl doctor` must distinguish “installed” from “qualified for untrusted isolation.”

### Model transport vs. execution

- Split the cloud model/controller plane from the isolated execution plane.
- The controller gets no target repository mount, host home, arbitrary path reads, arbitrary shell, staging credentials, runtime configuration controls, or generic command execution.
- The execution container has no network and exposes only an orchestrator-owned sanitized workspace plus controlled output.
- Use a broker with fixed operations only:

```text
read_allowed_file(path)
list_allowed_files(prefix)
apply_unified_patch(patch)
run_allowed_command(command_id)
get_git_diff()
get_test_result(command_id)
finish(status, summary, limitations)
```

- `command_id` maps to a project-profile-owned command; it never accepts model-provided shell text.
- Any adapter that cannot prove prompt/patch or brokered-tool operation fails closed.

### Frontier adapters

- Planner and reviewer must operate in `prompt_only` or independently verified `sandboxed_workspace` mode.
- A sanitized working directory by itself is not evidence of confinement.
- No dangerous permission bypasses or “approve all tools” modes.
- Add negative tests proving a frontier adapter cannot read a planted host secret.

### Evidence and handoffs

- Executor result JSON, test JSON, transcript, Markdown handoff, filenames, patch comments, and source comments are untrusted content.
- Every evidence item has a provenance class:
  - `trusted_local`
  - `orchestrator_observed`
  - `untrusted_executor_claim`
  - `frontier_review_judgment`
  - `human_confirmation`
- JSON is canonical. Markdown is a readable rendering only.
- Artifact paths are orchestrator-owned, schema-validated, inside the sprint directory, and never read from model-generated prose.
- Prompt wrappers must explicitly label executor artifacts as untrusted data.
- Scan/redact and enforce size caps before forwarding artifacts across boundaries.

### Sanitization

- Build snapshots from the pinned committed Git tree, not by recursively walking the working directory.
- Exclude untracked and ignored files by construction.
- Preserve `--allow-dirty` only as an explicit, local-only flow with a recorded diff hash.
- Record sanitization telemetry in the manifest: basis commit, files considered/included, included bytes, exclusions by reason, redacted files, scanner strength, source filesystem type when available, and duration.

### Retry budgets

- Add `CHANGES_REQUESTED` and `HUMAN_ESCALATION_REQUIRED` states.
- Do not automatically requeue after review changes.
- Make budgets project-configurable. The initial default is:

```yaml
iteration_budget:
  max_executor_attempts: 3
  max_review_attempts: 3
  max_request_changes_requeues: 2
  max_elapsed_minutes_per_task: 90
  automatic_requeue: false
```

- Track model calls by role, timeout, elapsed time, retry reason, and provider usage only when the provider exposes reliable usage data.
- On exhaustion, refuse further model calls until a human closes the task, revises the contract, deliberately increases the budget, or starts a fresh task.

---

## 5. Implementation sequence now approved

Implement only the following before any real cloud-model or real target-project work.

### Milestone 0 — scaffolding and runtime detection

Deliver:

- repository baseline, tests, package setup, local run directory, event log;
- OCI runtime abstraction and `doctor` qualification output;
- policy that asserts no production command or config surface exists;
- provenance and retry-budget schemas;
- simulated mode that works with no runtime and no model CLI.

### Milestone 1 — committed-tree sanitization

Deliver:

- project-profile schema validation;
- committed-tree allowlist snapshot;
- blocked path and symlink escape rejection;
- redaction, fake environment generation, built-in secret prefilter, optional `gitleaks`;
- manifest and benchmark telemetry;
- malicious fixtures for secrets, `.env`, production paths, symlinks, traversal, untracked content, and oversized artifacts.

### Milestone 2 — state, handoffs, provenance, and budgets

Deliver:

- explicit state machine including changes-requested/escalation paths;
- canonical JSON and Markdown projections;
- SHA-256 integrity checks;
- typed provenance;
- artifact-path confinement;
- prompt-injection malicious fixtures;
- explicit retry and escalation enforcement.

### First checkpoint

Before Milestone 3 starts, demonstrate with tests only:

1. a snapshot is created from an exact Git commit and includes only allowed files;
2. no untracked, ignored, blocked, symlinked, or secret-bearing test file leaves the trusted project;
3. handoffs cannot inject an arbitrary command, path, policy change, approval phrase, or deployment target;
4. retry limits produce human escalation instead of another model call;
5. Podman detection reports whether rootless, no-network executor isolation is actually qualified;
6. no CLI surface or configuration accepts a production destination.

Do not wire OpenCode, Claude Code, Codex, staging, or a real demo repository before this checkpoint passes.

---

## 6. Controlled adoption rule

After Milestone 7 succeeds on a disposable fixture repository:

1. Run `sanitise preview` and profile validation against `pub-rec-opencode-deepseek` in read-only shadow mode.
2. Select one newly planned, low-risk, non-security-critical task.
3. Run it through `asctl` in shadow mode first.
4. Compare the generated review/gate evidence against the manual workflow.
5. Require explicit human approval before using `asctl` for future tasks on that repository.

No in-flight task is migrated, and staging remains disabled during this first adoption phase.

---

## 7. Definition of “done enough to advance”

A milestone may advance only with:

- focused commits;
- passing automated tests;
- a generated implementation handoff under `docs/implementation-handoffs/`;
- a concise report of enforced boundaries, assumptions, and deliberately unavailable adapters;
- no claim that a provider, secret scanner, container runtime, or deployment path is safe without direct test evidence.

This is the final build direction. Begin by consolidating V2 and then execute Milestones 0–2 only.
