# Secure Agent Sprint Control Plane — Machine Implementation Specification

**Audience:** Claude Code operating on the developer's Windows + WSL2 machine  
**Primary goal:** Build a local, security-first control plane that runs structured development sprints using three model layers, produces machine-readable handoffs, keeps untrusted models away from real secrets and production, and requires a human before staging or production-adjacent actions.

> **Read this whole document before changing anything.** Treat it as the source of truth. Make a best-effort implementation without waiting for clarification. Record any assumptions or unavoidable deviations in the generated handoff files.

---

## 1. Outcome to Build

Create a standalone repository and local CLI named `agent-sprint-control` (CLI: `asctl`) that orchestrates this development cycle:

1. **Frontier planner** — Claude Code *or* Codex CLI analyses a sanitized project snapshot and turns a sprint request into small, ordered implementation tasks.
2. **Cheap executor** — DeepSeek through OpenCode (or another low-cost provider behind an adapter) implements one task at a time inside a locked-down, sanitized workspace.
3. **Opposite frontier reviewer** — the frontier provider not used for planning reviews the task output, tests, risks, and patch in a second sanitized workspace.
4. **Trusted local gate** — local deterministic checks validate the patch before it can be applied to a protected local review branch.
5. **Human gate** — a human explicitly approves any staging deployment. Production is intentionally outside this control plane and must remain inaccessible to all agents.

Every stage must create a Markdown handoff and a JSON state record. The next stage reads those artifacts automatically rather than relying on conversational memory.

The first usable version must support a generic Git repository. It must not assume a specific language, framework, CI provider, or cloud.

---

## 2. Non-Negotiable Safety Rules

These rules are more important than automation convenience.

### 2.1 Model trust boundaries

| Layer | Typical provider | May receive | Must never receive |
|---|---|---|---|
| Planner | Claude Code or Codex CLI | Sanitized code snapshot, architecture notes, public/non-secret configuration shape, task request | `.env` files, credentials, keys, production configs, database dumps, customer data, private certificates, SSH config, cloud tokens |
| Executor | DeepSeek via OpenCode | Minimal sanitized task workspace, generated mocks, task contract, test commands | Any real secret, remote Git token, SSH key, browser profile, host home directory, Docker socket, production/staging credentials, complete repository history |
| Reviewer | Opposite frontier provider | Sanitized patch, sanitized relevant files, planner/executor handoffs, test output | Same restricted material as planner |
| Trusted local gate | Local scripts only | Real repository checkout and patch after deterministic screening | Must not send repository content anywhere |
| Human | Developer / release owner | Local review branch, test/deploy evidence | N/A |

### 2.2 Environment separation

- **Production is not configured in this tool.** Do not create an `asctl deploy-prod` command, a production credential file, a production endpoint setting, or a generic environment flag that could accidentally target production.
- Staging uses a **separate credential source** from production, ideally a separate cloud account/project/tenant. The staging credential is only read by a trusted local deployment command after explicit human confirmation.
- The DeepSeek/OpenCode container must have **no network by default** and no mount to the host home directory, Docker socket, SSH agent, `.gitconfig`, Windows profile, browser files, cloud config, or real repository checkout.
- Use a temporary, sanitized Git repository for executor work. It must have no remote URL, no original Git history, and no credentials.
- Handoff files, prompts, logs, and patches are potentially external-facing material. Run secret scanning before writing or forwarding them.

### 2.3 Host safety

- Build and run everything under **WSL2**, not directly under Windows, except optional PowerShell wrappers for voice intake or launching WSL commands.
- Never use `sudo` from orchestration scripts.
- Never run `rm -rf` on a path that has not first been verified to be inside the configured control-plane run directory.
- Never modify a target project's default branch directly. Use a review branch such as `agent/sprint-<id>/review`.
- Never make a Git push automatically. The first version is local-first. A later, separately approved feature may create a pull request, but that is out of scope for v1.

---

## 3. Assumed Machine Baseline

Implement with graceful diagnostics, not silent assumptions.

- Windows 11 with WSL2 installed.
- A Linux distribution available in WSL (Ubuntu is expected, but do not hard-code Ubuntu-specific behavior except in optional install notes).
- Git installed in WSL.
- Python 3.11+ installed in WSL.
- Docker Desktop with WSL integration **or** another Docker-compatible daemon available from WSL. Docker is required for untrusted executor isolation. `asctl doctor` must detect and explain when it is unavailable.
- Claude Code CLI and Codex CLI may be installed in WSL. Do not assume their exact binary names or command flags; discover them with `--help` and implement adapters accordingly.
- OpenCode may be installed in WSL. The DeepSeek model/provider configuration remains outside the repository and must be referenced only by a local profile name.
- Optional local `whisper.cpp` or equivalent may be installed later for voice-to-text intake.

The tool must work in a “dry-run / simulated agent” mode even when one or more model CLIs are absent. This makes the security plumbing testable before API subscriptions or providers are wired in.

---

## 4. Recommended Repository Layout

Create the control plane in its own Git repository. Use Python for the orchestrator because it is cross-platform inside WSL, easy to test, and can invoke existing CLIs without embedding provider credentials.

```text
~/dev/agent-sprint-control/
├── README.md
├── CLAUDE_MACHINE_IMPLEMENTATION_SPEC.md      # copy of this document
├── pyproject.toml
├── .gitignore
├── .env.example                               # no secret values
├── config/
│   ├── defaults.yml
│   ├── profiles/
│   │   ├── local.yml
│   │   └── example-project.yml
│   └── schemas/
│       ├── project-config.schema.json
│       └── handoff.schema.json
├── policy/
│   ├── sensitive-paths.yml
│   ├── untrusted-allowed-paths.yml
│   ├── command-allowlist.yml
│   ├── redaction-patterns.yml
│   └── staging-approval.yml
├── src/agent_sprint/
│   ├── __init__.py
│   ├── cli.py
│   ├── config.py
│   ├── doctor.py
│   ├── state.py
│   ├── handoff.py
│   ├── sanitise.py
│   ├── secrets.py
│   ├── patch_gate.py
│   ├── workspaces.py
│   ├── runner.py
│   ├── approvals.py
│   ├── staging.py
│   ├── voice.py
│   ├── adapters/
│   │   ├── base.py
│   │   ├── claude_code.py
│   │   ├── codex.py
│   │   ├── opencode.py
│   │   └── simulated.py
│   └── templates/
│       ├── handoff.md.j2
│       ├── planner_prompt.md.j2
│       ├── executor_prompt.md.j2
│       └── reviewer_prompt.md.j2
├── scripts/
│   ├── bootstrap-wsl.sh
│   ├── run-untrusted-container.sh
│   ├── safe-clean-run.sh
│   ├── voice-intake.ps1
│   └── launch-asctl.ps1
├── docker/
│   ├── Dockerfile.executor
│   ├── compose.executor.yml
│   └── entrypoint.sh
├── tests/
│   ├── unit/
│   ├── integration/
│   └── fixtures/
├── docs/
│   ├── threat-model.md
│   ├── operator-guide.md
│   ├── adapter-guide.md
│   └── project-onboarding.md
└── examples/
    └── sample-project-profile.yml
```

Use a conventional Python package layout and `pytest`. Prefer small, standard-library-heavy code. Add external dependencies only where they materially improve reliability (for example: `PyYAML`, `jsonschema`, `jinja2`, and a tested CLI package such as `typer` or `click`). Pin versions in `pyproject.toml`.

---

## 5. Core Design

### 5.1 Terminology

- **Target project:** the real local Git repository being changed, for example `~/dev/prizeli` or a work project clone.
- **Sprint:** one parent request broken into one or more independently reviewable tasks.
- **Run:** one execution of one task through a stage.
- **Sanitized snapshot:** a generated copy of only allowed project material, with sensitive paths excluded and configured text redactions applied.
- **Untrusted workspace:** an isolated temporary Git repository given to the executor; it is disposable.
- **Review workspace:** another sanitized copy used by the reviewer; it contains the executor patch and relevant task artifacts, but not the real target project.
- **Trusted review branch:** local branch in the target project where a patch may be applied only after deterministic gates pass.
- **Handoff:** a Markdown + JSON record that describes what happened, evidence, risks, and exactly what the next stage must do.

### 5.2 State machine

Implement an explicit state machine. Do not infer completion merely from a file existing.

```text
DRAFT_REQUEST
  -> PLANNING
  -> PLAN_READY
  -> EXECUTING_TASK
  -> EXECUTOR_READY
  -> REVIEWING_TASK
  -> REVIEW_READY
  -> LOCAL_GATE_RUNNING
  -> READY_FOR_HUMAN_APPLY
  -> APPLIED_TO_REVIEW_BRANCH
  -> READY_FOR_HUMAN_STAGING_APPROVAL
  -> STAGING_DEPLOYED
  -> COMPLETE

Any state -> BLOCKED | FAILED | CANCELLED
```

Store state under a per-sprint run directory, for example:

```text
~/.local/share/agent-sprint-control/
├── projects/<project-id>/
│   └── project.yml
├── runs/<sprint-id>/
│   ├── sprint-state.json
│   ├── request.md
│   ├── planner/
│   │   ├── handoff.md
│   │   ├── handoff.json
│   │   └── task-plan.json
│   ├── tasks/T-001/
│   │   ├── executor/
│   │   ├── reviewer/
│   │   ├── gate/
│   │   └── combined-handoff.md
│   └── audit/
│       └── events.jsonl
└── cache/
```

Every state transition must append an event to `audit/events.jsonl` with timestamp, user, command, previous state, next state, input file hashes, and outcome. Avoid writing secret values into this log.

### 5.3 Immutable inputs and reproducibility

- Hash all generated prompt inputs, handoff files, patches, and sanitized snapshots with SHA-256.
- Record the target-project commit hash used as the basis for the sprint.
- Refuse to apply a patch if the target project HEAD has changed from the recorded basis unless the human explicitly starts a rebase/revalidation flow.
- A task patch must be stored as a file and never only inside model output.
- Preserve executor and reviewer stdout/stderr as logs after redaction, but cap log sizes and keep raw logs only in the local run directory.

---

## 6. Project Profile Format

Implement a project profile that is deliberately explicit. The user must register a project before running a sprint.

Example: `config/profiles/example-project.yml`

```yaml
project_id: example-web-app
repository_path: /home/<user>/dev/example-web-app
primary_branch: main

# Only these paths are eligible to enter a DeepSeek/untrusted workspace.
untrusted_allowed_paths:
  - src/**
  - test/**
  - tests/**
  - docs/public/**
  - package.json
  - package-lock.json
  - pnpm-lock.yaml
  - pyproject.toml
  - poetry.lock
  - pom.xml
  - build.gradle
  - settings.gradle
  - README.md
  - AGENTS.md
  - SKILL.md

# These paths are blocked even when a broad allowlist would otherwise include them.
blocked_paths:
  - .env
  - .env.*
  - '**/*secret*'
  - '**/*credential*'
  - '**/*token*'
  - '**/*private*key*'
  - .git/**
  - .github/workflows/**
  - infra/prod/**
  - infrastructure/prod/**
  - terraform/prod/**
  - kubernetes/prod/**
  - helm/prod/**
  - docker-compose.prod.yml
  - config/prod/**
  - data/**
  - backups/**
  - scripts/deploy-prod.*

# Commands are trusted-project commands. They run only in the local trusted gate,
# never inside the untrusted executor unless separately mapped to a safe command.
trusted_test_commands:
  - ./mvnw test
  - npm test -- --runInBand

# Commands exposed inside the untrusted container. Prefer unit tests and formatters.
untrusted_test_commands:
  - npm test -- --runInBand

# Mocks are generated or copied from a dedicated non-secret fixtures directory.
mock_strategy:
  fixture_root: tests/fixtures/agent-mocks
  required_env:
    STRIPE_SECRET_KEY: sk_test_agent_mock
    DATABASE_URL: postgresql://agent:agent@mock-db:5432/app
  prohibited_env_names:
    - AWS_ACCESS_KEY_ID
    - AWS_SECRET_ACCESS_KEY
    - STRIPE_SECRET_KEY_LIVE
    - DATABASE_URL_PROD

staging:
  enabled: false
  command: ./scripts/deploy-staging.sh
  required_approval_phrase: DEPLOY TO STAGING
  credential_command: /home/<user>/.local/bin/load-example-staging-env
  allowed_hosts:
    - staging.example.internal

model_routing:
  planner: claude_code
  executor: opencode_deepseek
  reviewer: codex
```

Notes:

- The profile must be validated against JSON Schema.
- Reject profiles where an allowed path overlaps a blocked path in a way that could leak sensitive material.
- `required_env` is for **fake values only**. Validate that they match known fake/test patterns where possible.
- Do not store real staging credentials in project YAML or in the control-plane repository.

---

## 7. Sanitization Pipeline

The sanitization pipeline is the principal protection for untrusted models. Implement it before any model adapters.

### 7.1 Required behavior

Given a target project and task scope, `asctl sanitise` must:

1. Resolve the target repository path and verify it is a Git worktree.
2. Resolve its current commit hash and refuse a dirty working tree by default. Offer an explicit `--allow-dirty` mode that records the diff as a local-only input; do not silently include it.
3. Build a temporary snapshot from an allowlist, never from a “copy everything then delete secrets” approach.
4. Reject symlinks that escape the source root. For v1, exclude all symlinks from externalized workspaces unless a future explicit policy permits them.
5. Exclude `.git`, home-directory references, caches, binaries over a configurable size, node modules, build artifacts, VCS hooks, and blocked paths.
6. Apply text redaction patterns to eligible text files. Preserve line count where practical so diagnostics remain understandable.
7. Generate deterministic mocks and fake environment values from the project profile. Do not copy application databases or production-derived test data.
8. Initialize a new Git repo inside the sanitized snapshot with a single local baseline commit and no remotes.
9. Run secret scanning over the snapshot. Fail closed when a scanner finds likely secrets.
10. Write a manifest containing every included file, excluded file/reason, redaction count, SHA-256 hashes, and scanner result.

### 7.2 Scanner strategy

Implement two levels:

1. **Built-in prefilter**: filename/path blocks plus carefully selected regexes for common private key headers, AWS-like access key patterns, GitHub tokens, OpenAI/Anthropic-style keys, Stripe live secret keys, JWTs in configuration fields, database connection strings with non-test hosts, and high-entropy assignment values.
2. **Optional external scanner**: integrate `gitleaks` when present. `asctl doctor` should identify whether it is installed. The tool must still operate with the built-in prefilter, but mark runs as `scanner_strength: reduced` in handoffs.

Never claim secret scanning provides a guarantee. It is a gate that reduces risk, not proof of absence.

### 7.3 Redaction rules

Use `policy/redaction-patterns.yml` with named patterns and replacement markers such as:

```yaml
patterns:
  - id: private_key
    regex: '-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----[\s\S]*?-----END (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'
    replacement: '[REDACTED_PRIVATE_KEY]'
    severity: critical
  - id: stripe_live_key
    regex: 'sk_live_[A-Za-z0-9_]+'
    replacement: '[REDACTED_STRIPE_LIVE_KEY]'
    severity: critical
  - id: generic_secret_assignment
    regex: '(?i)(password|secret|token|api[_-]?key)\s*[:=]\s*["'"']?[^\s"'"']{12,}'
    replacement: '\\1=[REDACTED]'
    severity: warning
```

Redaction must be auditable. The manifest stores counts and pattern IDs, never the original secret values.

---

## 8. Untrusted Executor Isolation

The executor is the least trusted layer. It must work only on a disposable sanitized copy.

### 8.1 Docker requirements

Build `docker/Dockerfile.executor` with a minimal non-root image. The exact base image is flexible; prefer a maintained slim image that supports the target project’s required tooling through a controlled build argument or project-specific executor image later.

When starting the executor container:

- Use `--network none` by default.
- Run as a non-root user.
- Mount only the sanitized workspace and a small output directory. Do not mount the original target repository.
- Do not mount `/var/run/docker.sock`, `$HOME`, `/mnt/c`, SSH agents, credential stores, cloud config, or host Git configuration.
- Set a neutral `$HOME` inside the container.
- Set an empty/controlled Git config and ensure `git remote -v` yields no remotes.
- Pass fake environment variables only from the generated `.agent-env` file.
- Enforce CPU, memory, process, and execution-time limits where Docker supports them.
- Make the container write only inside `/workspace` and `/output`.
- Capture a redacted transcript and exit code.

### 8.2 Executor contract

The executor receives:

- `TASK.md`: one bounded implementation task.
- `CONTEXT.md`: relevant sanitized files, architectural constraints, and allowed commands.
- `MOCKS.md`: generated test/mocking expectations.
- `HANDOFF_IN.md`: planner handoff.
- A local Git baseline.

The executor must produce:

```text
/output/
├── changes.patch
├── executor-result.json
├── executor-handoff.md
├── test-results.json
└── transcript.redacted.log
```

`executor-result.json` must contain at least:

```json
{
  "status": "completed | blocked | failed",
  "summary": "...",
  "files_changed": ["src/example.ts"],
  "commands_run": [{"command": "npm test -- --runInBand", "exit_code": 0}],
  "known_limitations": ["..."],
  "requires_human_decision": false,
  "patch_sha256": "..."
}
```

### 8.3 Forbidden executor actions

Reject executor output or container activity attempting to:

- modify the control-plane policy files;
- change CI, deployment, or production infrastructure files unless the project profile explicitly allows that task type and only in the reviewer/trusted stage;
- access network services;
- add a new remote;
- embed secrets or endpoints in source;
- alter the task scope without recording it;
- write outside `/workspace` or `/output`.

For v1, these are hard blocks, not warnings.

---

## 9. Frontier Planner and Reviewer Adapters

### 9.1 Adapter design

Create a small provider abstraction in `src/agent_sprint/adapters/base.py`.

Suggested interface:

```python
class AgentAdapter(Protocol):
    name: str

    def is_available(self) -> Availability: ...
    def run(self, request: AgentRequest, workdir: Path) -> AgentResult: ...
```

`AgentRequest` should contain only sanitized prompt text, expected output path, model role, timeout, and allowed command context. The adapters must not read provider keys themselves; each provider CLI manages its own local authentication.

Implement four adapters:

- `ClaudeCodeAdapter`
- `CodexAdapter`
- `OpenCodeAdapter`
- `SimulatedAdapter`

Avoid hard-coding undocumented command syntax. During implementation, inspect each installed CLI’s `--help`. Encapsulate provider-specific invocation in only its adapter. Write unit tests around command construction and parser behavior.

### 9.2 Planner requirements

The planner may receive a broader sanitized architecture slice than one executor task, but still no secret material. It must output both:

- a human-readable plan (`planner-plan.md`); and
- structured `task-plan.json` validated against schema.

Each task must be independently executable and include:

- ID (for example `T-001`)
- title
- goal
- allowed paths
- blocked paths
- dependencies
- acceptance criteria
- test commands
- mocked/external integrations
- risk level (`low`, `medium`, `high`)
- whether a task needs human design approval before execution

Reject plans with tasks that touch blocked paths or have undefined test/acceptance criteria.

### 9.3 Opposite-provider review rule

The reviewer must be chosen automatically as the *other* configured frontier provider:

- planner `claude_code` -> reviewer `codex`
- planner `codex` -> reviewer `claude_code`

If the opposite provider is unavailable, do not silently reuse the planner. Mark the run `BLOCKED` unless the human explicitly invokes a documented emergency override. That override must be prominently recorded in the handoff.

### 9.4 Reviewer requirements

The reviewer receives the sanitized base snapshot, executor patch, executor handoff, test results, planner task contract, and policy summaries. It must return a structured decision:

```json
{
  "decision": "approve | request_changes | reject | blocked",
  "summary": "...",
  "findings": [
    {
      "severity": "critical | high | medium | low | info",
      "file": "src/...",
      "line_hint": "...",
      "description": "...",
      "required_action": "..."
    }
  ],
  "test_assessment": "...",
  "security_assessment": "...",
  "follow_up_tasks": []
}
```

Only `approve` may proceed to the trusted local patch gate. A reviewer approval is advisory; deterministic local checks and human gates still apply.

---

## 10. Handoff Protocol

### 10.1 Required handoff after every stage

Generate a Markdown and JSON handoff after:

- intake / voice transcription;
- planning;
- executor completion;
- review;
- local patch gate;
- patch application;
- staging deployment or staging failure.

The next stage must read the previous handoff path and verify its SHA-256 before proceeding.

### 10.2 Markdown handoff template

Use a stable format like this:

```markdown
# Agent Sprint Handoff

- Sprint: `<sprint-id>`
- Task: `<task-id or N/A>`
- Stage: `planner | executor | reviewer | local-gate | apply | staging`
- Status: `completed | blocked | failed | approved`
- Generated at: `<ISO-8601 UTC>`
- Input hash: `<sha256>`
- Output hash: `<sha256>`
- Target project basis: `<git commit>`

## Objective
<bounded goal>

## What happened
<short factual summary>

## Files / artifacts
- Included snapshot manifest: `<path>`
- Patch: `<path + sha256>`
- Test evidence: `<path>`
- Transcript: `<path>`

## Validation evidence
- `<command>` — exit `<code>`
- Secret scan — `<pass/fail/reduced>`
- Policy gate — `<pass/fail>`

## Risks and limitations
- <none or explicit items>

## Decision needed from next stage
<precise instruction>

## Human escalation required?
`yes | no`

## Do not assume
- No real external integration was exercised unless explicitly stated.
- Passing mocked tests does not prove staging or production compatibility.
```

Render Markdown from structured data, not model prose alone. The JSON is canonical; Markdown is the readable projection.

### 10.3 Handoff integrity

- Validate JSON using schema.
- Redact scan before saving handoff content.
- Reject path references that escape the sprint directory.
- The CLI must print only a concise summary and artifact paths, never dump full transcripts to the terminal by default.

---

## 11. Trusted Local Patch Gate

The local gate runs on the host against a temporary trusted review worktree of the real target repository. It is not given to DeepSeek.

### 11.1 Required sequence

1. Validate task state and reviewer decision.
2. Validate patch format and SHA-256 against executor record.
3. Ensure every changed file matches the task allowlist and does not match a global blocked path.
4. Run secret scanning on the patch and resulting review worktree.
5. Apply with `git apply --check`, then apply to a new local branch `agent/sprint-<id>/review`.
6. Run trusted tests from the project profile.
7. Run project-specific static checks when configured.
8. Generate a local-gate handoff with a diff summary and test evidence.
9. Stop at `READY_FOR_HUMAN_APPLY`; do not merge or push.

### 11.2 Patch policy

Reject changes that:

- edit policy files or the control plane itself when operating on another target project;
- change blocked paths;
- add binary blobs unless explicitly allowed;
- add dependencies without explicit task acceptance criteria;
- introduce likely secrets;
- alter test configuration to hide failures;
- remove tests without a task rationale and reviewer approval;
- modify deployment/prod infrastructure in v1.

Use a clear failure message and a handoff that explains the exact rejected paths or gate rules.

---

## 12. Human Approval and Staging Flow

### 12.1 Apply is explicit

The human must run a command such as:

```bash
asctl task apply --sprint S-20260622-001 --task T-001
```

The command must:

- print a concise diffstat, reviewer decision, local test results, policy status, and current branch;
- require an exact typed confirmation phrase, for example `APPLY PATCH TO REVIEW BRANCH`;
- apply only to the local review branch;
- never merge to `main` and never push;
- write an application handoff.

### 12.2 Staging is a separate explicit approval

Staging must be disabled by default per project profile. When enabled, require:

```bash
asctl staging deploy --sprint S-20260622-001 --task T-001
```

Before executing, it must:

1. Verify that a human applied the task to the review branch.
2. Verify all local gate results passed.
3. Print the exact staging command, staging hostname allowlist, review branch, commit, and deployment limitations.
4. Require the exact configured approval phrase.
5. Load staging credentials only inside the trusted deploy subprocess. Do not write them to logs, environment manifests, handoffs, or prompts.
6. Reject commands that refer to a non-allowlisted host or an obvious production hostname.
7. Generate a deployment handoff with redacted logs and deployment evidence.

### 12.3 Production hard barrier

Implement and test these barriers:

- There is no production deploy command, config object, profile property, or environment switch in `asctl`.
- `asctl` treats names including `prod`, `production`, `live`, and configured production aliases as forbidden targets.
- The control-plane test suite asserts no `deploy-prod` command exists and no project config accepts a production destination.
- Document that production promotion must use the company’s separate release process with separate credentials and mandatory human review.

---

## 13. Voice-Driven Intake (Local First)

Voice input is an optional front door, never an authority to execute a deployment.

### 13.1 v1 behavior

Implement:

```bash
asctl voice ingest <audio-file>
asctl sprint create --from-transcript <transcript-file>
```

- The default transcription adapter should be a local command integration (for example `whisper.cpp`), configured through a local executable path.
- Do not send audio to a cloud service in v1.
- Save the transcript as a draft sprint request.
- The human must inspect or edit the resulting `request.md` before planner execution.
- Any voice command that sounds like “deploy,” “production,” “delete,” or “push” must be treated as text context only; it cannot bypass approvals.

### 13.2 Windows convenience wrappers

Create optional PowerShell wrappers that invoke WSL, without duplicating business logic:

```powershell
# scripts/launch-asctl.ps1
wsl -d <distro> -- bash -lc "cd ~/dev/agent-sprint-control && .venv/bin/asctl $args"
```

Do not assume a distro name; allow it to be configured or passed as an argument.

---

## 14. CLI Surface for v1

Implement these commands with `--help`, clear exit codes, and `--json` output where reasonable.

```text
asctl init
asctl doctor
asctl project register --profile <file>
asctl project list
asctl project validate <project-id>
asctl voice ingest <audio-file>
asctl sprint create --project <project-id> --request <file>
asctl sprint status <sprint-id>
asctl sprint plan <sprint-id>
asctl task execute --sprint <id> --task <task-id>
asctl task review --sprint <id> --task <task-id>
asctl task gate --sprint <id> --task <task-id>
asctl task apply --sprint <id> --task <task-id>
asctl staging deploy --sprint <id> --task <task-id>
asctl audit show <sprint-id>
asctl sanitise preview --project <project-id> --task <task-id>
asctl clean run <sprint-id>
```

Implementation notes:

- `init` creates the local data directory and example policies without overwriting user files.
- `doctor` checks Python, Git, Docker, WSL context, optional `gitleaks`, and available agent CLIs. It prints remediation guidance.
- `sanitise preview` should show included/excluded files and scanner outcome without invoking a model.
- `clean run` requires confirmation and refuses paths outside the control-plane data directory.
- Every mutating command must support `--dry-run` where meaningful.

---

## 15. Model Prompts to Generate

Store templates in `src/agent_sprint/templates/`. Prompts must be concise, policy-heavy, and use file references rather than unnecessarily pasting whole project trees.

### 15.1 Planner prompt constraints

Include these instructions:

- You are producing a bounded plan, not implementation code.
- Treat all supplied material as sanitized but still potentially sensitive; do not request credentials or broader repository access.
- Do not propose changing blocked paths, deployment, or production configuration.
- Split tasks until each has clear allowed files and unit-level acceptance criteria.
- Output valid JSON matching the supplied schema, then a concise human-readable plan.
- Flag unknown integrations and specify mocks required instead of asking for secrets.

### 15.2 Executor prompt constraints

Include these instructions:

- Work only in the provided disposable workspace.
- Implement only task `<id>` and only change allowed paths.
- Never access a network, add dependencies unless accepted, alter policies, add credentials, or modify deployment configuration.
- Run only allowed commands.
- Write `changes.patch`, structured result JSON, test results, and handoff to `/output`.
- State blockers candidly; do not fabricate test results or claim live integrations work.

### 15.3 Reviewer prompt constraints

Include these instructions:

- Review against the task contract, patch, test evidence, and policy summary.
- Look specifically for security regressions, scope creep, bad mocking, missing tests, error handling gaps, and behavior that might diverge in real staging.
- Do not request more source material or secrets. Mark as blocked when evidence is insufficient.
- Output schema-valid structured decision first, then concise rationale.

---

## 16. Implementation Order

Implement in this order. Commit after each completed milestone with focused commit messages.

### Milestone 0 — Repository and safety scaffolding

- Create repository, Python package, virtual environment instructions, formatter/linter/test configuration.
- Add this specification to the repository.
- Add `.gitignore` for `.venv`, local run data, logs, generated snapshots, and local config overrides.
- Implement `asctl init`, basic config loading, run directory creation, structured event logging, and `asctl doctor`.
- Add unit tests for safe-path checks and config validation.

**Exit criteria:** `asctl init`, `asctl doctor`, and tests run without model CLIs or Docker.

### Milestone 1 — Policy and sanitization

- Implement project profile schema validation.
- Implement allowlist-first snapshot creation, blocked-path enforcement, symlink denial, manifest generation, redaction, fake environment generation, and local Git baseline initialization.
- Implement built-in secret prefilter and optional `gitleaks` integration.
- Add malicious fixtures: `.env`, fake private key, unsafe symlink, production path, high-entropy token, and a test fixture that must remain allowed.

**Exit criteria:** all unsafe fixtures are rejected or excluded; a safe source fixture produces a deterministic sanitized repo and manifest.

### Milestone 2 — Handoff and state machine

- Implement schemas, Markdown rendering, JSON canonical records, file hashing, transition validation, and `asctl sprint create/status`.
- Ensure failed states create handoffs too.
- Add tests for invalid state transitions and tampered handoff hash rejection.

**Exit criteria:** a fake sprint can traverse draft → planning → blocked with auditable artifacts.

### Milestone 3 — Adapter abstraction and simulated flow

- Implement provider adapter interface and `SimulatedAdapter`.
- Build planner and reviewer JSON parsing/validation.
- Implement planning with a simulated planner fixture and opposite-provider routing logic.
- Add clear availability checks for real CLIs.

**Exit criteria:** an end-to-end simulated sprint produces a plan and reviewer decision without external providers.

### Milestone 4 — Docker executor isolation

- Add executor Dockerfile/compose or direct Docker runner.
- Implement safe mount construction, no-network default, non-root user, controlled env, output collection, and timeout/resource limits.
- Implement a simulated executor mode before wiring OpenCode.
- Add integration tests that assert the container cannot see a mounted fake host secret and has no network.

**Exit criteria:** executor creates a patch only in `/output`, with no host credentials available.

### Milestone 5 — OpenCode executor adapter

- Inspect the installed OpenCode CLI help text.
- Implement the adapter in one encapsulated module.
- Configure provider/model only by local profile name, never committed API tokens.
- Test with a deliberately trivial fixture task, such as adding a pure function and unit test.

**Exit criteria:** a real OpenCode run can change a sanitized fixture repo, produce a patch, and complete handoff artifacts.

### Milestone 6 — Frontier planner/reviewer adapters

- Inspect installed Claude Code and Codex CLI help text.
- Implement adapters without duplicating credential handling.
- Use planner/reviewer output schema validation and robust error messages.
- Test that planner and reviewer must be different providers unless an explicit documented local override exists.

**Exit criteria:** one real or simulated end-to-end task can plan with one frontier adapter and review with the other.

### Milestone 7 — Trusted patch gate and local review branch

- Implement patch validation, allowlist enforcement, secret re-scan, `git apply --check`, branch creation, trusted test execution, and human-confirmed apply.
- Write diff summaries without exposing sensitive lines.
- Add tests for rejected production file modifications and secret-containing patches.

**Exit criteria:** a safe fixture patch applies only to an `agent/sprint-*` review branch after confirmation; unsafe patches fail closed.

### Milestone 8 — Staging approval wrapper

- Implement staging disabled by default, explicit confirmation phrase, hostname allowlist validation, credential subprocess isolation, and redacted deploy logging.
- Do not implement production support.
- Test that all production-like target names fail.

**Exit criteria:** staging dry run works only with a configured explicit staging profile and cannot route to a production name.

### Milestone 9 — Voice intake and operator docs

- Implement local voice command adapter and transcript-to-draft workflow.
- Add PowerShell wrappers.
- Write operator guide, threat model, project onboarding guide, and adapter guide.

**Exit criteria:** audio/text intake creates a draft request; no voice input can perform automatic apply/deploy.

---

## 17. Tests That Must Exist

Build a meaningful test suite. At minimum include:

### Unit tests

- project profile accepts safe config and rejects overlapping unsafe allow/block paths;
- safe path resolver rejects path traversal and symlink escape;
- secret prefilter finds each malicious fixture;
- redaction preserves expected file usability and records an audit entry;
- invalid state transitions are rejected;
- tampered handoff file hashes are rejected;
- opposite-frontier routing is enforced;
- production-like hostnames and command names are rejected;
- confirmation phrases are required;
- runner command construction never includes `$HOME`, Docker socket, or source repository mount.

### Integration tests

- a sample project runs through the full simulated lifecycle;
- sanitized workspace has no remote Git URL and excludes `.env` / `infra/prod` / fixtures with secrets;
- untrusted container cannot access network and cannot read a fake host secret;
- executor output is rejected when it edits a blocked file;
- a reviewer-approved safe patch gets applied to a local review branch only after a human-confirmation test harness;
- staging disabled configuration cannot deploy;
- a production hostname is rejected even if it appears in a staging command;
- failed model execution produces an informative, schema-valid handoff and leaves the source repository untouched.

### Manual smoke tests

Document a short, repeatable smoke-test script using a disposable sample repository. The first real target project should not be used until these pass.

---

## 18. Documentation to Produce

Write these documents as part of the implementation:

1. **README.md** — what this tool does, safety model, quick start, and explicit statement that it does not deploy production.
2. **docs/threat-model.md** — assets, threat actors, trust boundaries, attack paths, mitigations, known limitations.
3. **docs/operator-guide.md** — daily workflow from request to staged validation, recovery from blocked/failed runs, logs/artifact locations, and emergency stop procedure.
4. **docs/project-onboarding.md** — how to create an allowlist-first project profile and fake integration fixtures without copying secrets.
5. **docs/adapter-guide.md** — how to add/change a model adapter without leaking credentials and how to verify CLI behavior.
6. **docs/first-project-checklist.md** — a one-page checklist for onboarding a real repository safely.

Be candid in documentation: sanitization and scanning lower risk but cannot mathematically guarantee that sensitive information is absent. A human still owns provider trust decisions and staging approval.

---

## 19. Acceptance Criteria for the Whole System

The implementation is complete enough for a first real project when all of the following are true:

- A developer can register a target Git repo using an allowlist-first profile.
- `asctl sanitise preview` shows exactly what can leave the trusted repo and rejects test secrets / unsafe paths.
- A sprint request can be entered as Markdown or created from a local voice transcript.
- A frontier model can create structured tasks from a sanitized snapshot.
- DeepSeek/OpenCode can implement a small task inside a no-network disposable container using only fake integration data.
- An opposite frontier model reviews the sanitized patch and returns a schema-valid decision.
- The system generates signed-by-hash Markdown + JSON handoffs after every stage and automatically loads the correct previous artifact.
- A deterministic local gate refuses unsafe patches and runs trusted tests before human approval.
- No command automatically pushes, merges to the primary branch, deploys staging, or has any route to production.
- Staging requires an explicit typed human confirmation and uses credentials only inside the trusted deploy subprocess.
- Tests demonstrate that executor access to host secrets, Git history/remotes, Docker socket, production paths, and network is blocked.
- The control plane functions in simulated mode for reproducible testing when cloud model CLIs are unavailable.

---

## 20. First Command Sequence for Claude Code

Perform this sequence now, adapting paths only when necessary:

```bash
# 1. Work in WSL and create a dedicated repository.
mkdir -p ~/dev/agent-sprint-control
cd ~/dev/agent-sprint-control
git init

# 2. Create the Python project and baseline files.
# Use the implementation order in this document. Do not create a production path or credential store.

# 3. Copy this specification into the repository root if it is not already present.

# 4. Implement Milestones 0–2 first, with tests.
# Do not begin Docker or real model wiring until sanitization, policy enforcement,
# state records, and handoff integrity have passing tests.

# 5. At the end of each milestone:
# - run tests;
# - inspect git diff;
# - commit a focused change;
# - write a local implementation handoff under docs/implementation-handoffs/.
```

At every milestone, favor a secure, testable thin slice over a broad but unverified automation layer.

---

## 21. Explicit Non-Goals for v1

Do **not** build these in the first implementation:

- production deployment or production credential support;
- autonomous merge or autonomous Git push;
- multi-user permissions / web UI / SaaS control plane;
- remote hosted agent workers;
- full database snapshots or real customer-data test fixtures;
- bypass mechanisms for policy gates;
- generic “run arbitrary shell command” capabilities exposed to model output;
- automatic voice-triggered execution;
- dynamic model switching that silently ignores the opposite-provider review rule.

Build these only after v1 has been used safely with a disposable sample project and reviewed by the human operator.

---

## 22. Final Reporting Required from Claude Code

When the implementation reaches a usable milestone, provide a concise report containing:

1. implemented commands and current milestone status;
2. exact security boundaries now enforced;
3. model CLI adapters detected and any assumptions about their local invocation;
4. test results and commands run;
5. files intentionally left as stubs or requiring local configuration;
6. the safest next command to run on a disposable sample project;
7. any deviation from this specification, with rationale and impact.

Do not claim that external provider behavior, secret scanning, staging compatibility, or production safety has been proven unless there is direct evidence. Record uncertainty clearly.
