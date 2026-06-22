#!/usr/bin/env bash
# Run before telling Codex a sprint is ready to review.
# Catches the exact gap that happened in Sprint 4 round 2: work reached the
# reviewer with no commit checkpoint and no handoff doc written.
#
# Usage: scripts/pre-review-check.sh <sprint-number>

set -euo pipefail

SPRINT="${1:?Usage: scripts/pre-review-check.sh <sprint-number>}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

FAIL=0

echo "== Pre-review check: Sprint $SPRINT =="

# 1. Working tree must be clean (everything committed).
if [[ -n "$(git status --short)" ]]; then
  echo "[FAIL] Uncommitted changes present. Commit before sending to Codex:"
  git status --short
  FAIL=1
else
  echo "[ok]   Working tree is clean."
fi

# 2. A handoff doc for this sprint must exist.
HANDOFF="docs/backlog/sprint-${SPRINT}-handoff.md"
if [[ ! -f "$HANDOFF" ]]; then
  echo "[FAIL] Missing $HANDOFF — write the coordinator's diff-verified status check before review."
  FAIL=1
else
  echo "[ok]   $HANDOFF exists."
fi

# 3. Every task brief for this sprint should be reflected in the handoff
#    (a brief with no mention in the handoff likely means it was never verified).
BRIEFS_DIR="docs/backlog/tasks/sprint-${SPRINT}"
if [[ -d "$BRIEFS_DIR" ]]; then
  for brief in "$BRIEFS_DIR"/*.md; do
    task_id="$(basename "$brief" | grep -oE '^[A-Z]+-[0-9]+')"
    if [[ -n "$task_id" ]] && [[ -f "$HANDOFF" ]] && ! grep -q "$task_id" "$HANDOFF"; then
      echo "[FAIL] Task $task_id has a brief but is not mentioned in $HANDOFF."
      FAIL=1
    fi
  done
fi

if [[ "$FAIL" -eq 0 ]]; then
  echo "== All checks passed. Safe to tell Codex this sprint is ready. =="
else
  echo "== Not ready. Fix the items above before notifying Codex. =="
  exit 1
fi
