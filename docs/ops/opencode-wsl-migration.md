# opencode + DeepSeek: migrate from Windows to WSL

Status: **not started** (work-in-progress task that was lost to a session crash before any actual setup happened). Written 2026-06-20 from a cold investigation, not from memory of what was previously done — treat the "what we found" section as the actual current state, verified by checking the filesystem directly.

## Why

`opencode` (the CLI host that drives the DeepSeek implementer in this project's [multi-agent sprint workflow](../../CLAUDE.md#multi-agent-development-workflow)) was running as a Windows-native install, which caused issues. Decision was made to run it natively inside WSL instead. Reason not yet recorded — fill in once confirmed (candidates: path translation between `/mnt/c/...` and Windows paths, line-ending/permission mismatches, or general friction running a Linux-shell-oriented agent loop from a Windows binary against a WSL-mounted repo).

Alongside the migration, there's an open task to **safeguard opencode/DeepSeek from real and sensitive data** — not yet scoped in detail (see open questions below).

## What we found (verified 2026-06-20)

- `opencode` is currently only reachable via the Windows global npm install: `/mnt/c/Users/dimit/AppData/Roaming/npm/opencode`. WSL's `$PATH` picks this up via the `/mnt/c` passthrough, not via a native install.
- WSL has its own native Node/npm already: `node v24.17.0`, `npm 11.13.0`, npm prefix `/home/dnit/.local`.
- No opencode config or package exists anywhere under the WSL home directory (`~/.config`, `~/.local/lib/node_modules` both checked, both empty of opencode).
- No DeepSeek API key or opencode-related env var is set in the current shell.
- This repo's existing `.gitignore` already excludes `.env`/`.env.*`, and `docs/security/secrets-and-test-data.md` already documents app-level secret hygiene (no real OAuth secrets, in-memory JWT keys, `.test`-domain seed accounts) — but that doc is scoped to *this repo's own code*, not to the opencode/DeepSeek tooling's access to the host machine.

## Scope, confirmed with the user (2026-06-20)

opencode/DeepSeek must have **filesystem access to this project folder only** (`/mnt/c/projects/pub-rec-opencode-deepseek`), and must never be able to reach real SSH keys, API keys, or other credentials.

**This is a hard requirement, not a nice-to-have** — confirmed by direct inspection (not assumption) that the parent directory `/mnt/c/projects/` contains real sensitive material belonging to the user's other projects, sitting one level above this repo:
- `/mnt/c/projects/.env`
- `/mnt/c/projects/DNIT_INFRASTRUCTURE.md`
- `/mnt/c/projects/vps-env-vars.txt`
- `/mnt/c/projects/vps-env-vars-staging.txt`
- sibling project directories: `dupslog`, `dupslog-old`, `dupslog-redeploy-main`, `food-manager`, `portfolio-dnit`, `webshops`, `tampermonkey`, `java-demo`, `doghotel`, `agent files`

None of these were opened/read while investigating this — only their existence and filenames were confirmed. **opencode must never be launched with `/mnt/c/projects` (or any sibling project dir) as its working/project root — only `pub-rec-opencode-deepseek` itself.**

The existing Windows install (`/mnt/c/Users/dimit/AppData/Roaming/npm/opencode`, v1.17.8) has a global config at `/mnt/c/Users/dimit/.config/opencode/opencode.jsonc` (currently empty aside from the schema ref) and a global skill at `.config/opencode/skills/safe-agent-operations/SKILL.md` that already encodes *behavioral* permission tiers (what commands an agent may run without approval) — but that's a prompt-level policy, not a filesystem-level enforcement. It does not stop a process from reading files it has OS permission to read. Credentials (the DeepSeek API key, etc.) are stored in `~/.local/share/opencode/opencode.db` (SQLite) on the Windows side — not inspected for the same reason as above.

## Decision needed: how to actually enforce the filesystem boundary

Behavioral policy (telling the agent "don't read outside your folder") is not real enforcement — confining file access at the OS level is. Candidate approaches for WSL:

1. **Dedicated low-privilege Linux user.** Create a new WSL user (e.g. `opencode-agent`) with no read access to `dnit`'s home directory and no `/mnt/c/projects` access except a bind-mount/grant scoped to `pub-rec-opencode-deepseek`. Install opencode natively under that user's own npm prefix, store the DeepSeek key only in that user's environment/config. Requires `sudo` (interactive password — the assistant can't supply this; the user needs to run the privileged setup commands themselves, e.g. via the `!` prefix or directly in their own terminal).
2. **Container (Docker/Podman).** Strongest isolation — only the repo directory is bind-mounted into the container; the rest of the filesystem is simply not visible. More setup overhead, and still needs network egress to DeepSeek's API.
3. **bubblewrap (`bwrap`) sandboxed launch.** Lighter-weight namespace sandbox, applied on a per-invocation basis without a separate user/container; needs `bwrap` installed.

Not yet decided which approach to take. **Next step is to make this decision with the user, then implement it.**

## Decision history

1. First chose "dedicated restricted Linux user" — but discovered while implementing it that `/mnt/c` is mounted via `9p`/DrvFs with fixed `uid=1000,gid=1000` and no `metadata` mode (confirmed: `chmod 600` on a test file under this repo had no effect, file still showed `rwxrwxrwx`). **Unix permissions are not enforced at all on `/mnt/c`** — a second restricted user would still be able to read everything there, secrets included. Considered two fixes (enabling DrvFs metadata mode globally, or moving the working copy to native ext4) — both add real hassle (global WSL config change, or maintaining a second git checkout kept in sync).
2. **User pushed back: is a container not easier?** Yes — it sidesteps the DrvFs permission bug entirely, since a container's filesystem view is whatever's explicitly bind-mounted into it; the rest of the host (including everything under `/mnt/c/projects` except the one mounted folder) is simply not present inside the container's mount namespace, regardless of host-side permission quirks. **Decided: rootless Podman container**, scoped via `-v /mnt/c/projects/pub-rec-opencode-deepseek:/workspace` and nothing else.

## Current state (2026-06-20)

Neither `docker` nor `podman` is installed in this WSL instance yet (`command -v` for both returned nothing; no reachable Docker daemon). Distro is Ubuntu 24.04.3 LTS, which has `podman` in its default repos — no extra repo setup needed.

**Blocked on:** installing podman requires `sudo apt install -y podman`, which needs an interactive password the assistant can't supply. The user needs to run this themselves (e.g. via the `!` prefix in their own prompt, or directly in a WSL terminal):

```bash
sudo apt update && sudo apt install -y podman
```

Once podman is installed, remaining work (no further sudo needed):
1. Write a minimal Dockerfile/image with Node + opencode (and the DeepSeek provider) preinstalled, under e.g. a new top-level `ops/opencode-sandbox/` directory in this repo (not yet created).
2. Write a wrapper script that runs `podman run --rm -it -v "$(pwd):/workspace" -w /workspace --env-file <path-outside-repo> <image> opencode ...` — bind-mounts only this repo directory, passes the DeepSeek API key via `--env-file` (a file outside the repo / outside any git tracking, never baked into the image).
3. Verify containment by trying (from inside a running container) to read something outside `/workspace`, e.g. `/etc/shadow` or any path resembling `/workspace/../`, and confirming it's not visible.
4. Document the actual run command for day-to-day use in this file, replacing this "next step" section.

## Next step

Waiting on the user to run the podman install command above; resume from step 1 of "remaining work" once confirmed installed.
