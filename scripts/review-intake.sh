#!/usr/bin/env bash
# review-intake.sh — one deterministic pass over reviews/ that detects a
# new/updated Codex verdict, runs scripts/verify-review.sh (AUTO-2) to
# classify it, and takes the corresponding action (commit / re-request /
# flag). This is the *inbound* half of the auto-review loop (AUTO-3); see
# docs/backlog/tasks/sprint-20/AUTO-3-review-intake-watcher.md for the spec.
#
# Usage:
#   scripts/review-intake.sh              # one intake pass over reviews/
#   scripts/review-intake.sh --selftest
#
# This script does NOT loop, sleep, push, or notify beyond stdout. It never
# runs `git push`. Scheduling/pushing/notifying is the operational wrapper's
# job (see the brief's "Operational wiring note").
#
# Exit code: 0 on a clean pass (regardless of individual verdicts — a
# REJECT is a successful *intake*, not a script failure). Non-zero only on
# an internal error (e.g. verify-review.sh could not be invoked at all).
set -euo pipefail

# ---------------------------------------------------------------------------
# content_hash <file>
#
# Content hash (NOT mtime) so a git checkout/clone that resets mtimes
# doesn't cause false reprocessing.
# ---------------------------------------------------------------------------
content_hash() {
  local file="$1"
  sha1sum -- "$file" | awk '{print $1}'
}

# ---------------------------------------------------------------------------
# ledger_lookup <ledger_file> <path>
#
# Prints "<sig>\t<outcome>" for the given path if present in the ledger,
# else prints nothing.
# ---------------------------------------------------------------------------
ledger_lookup() {
  local ledger="$1" path="$2"
  [[ -f "$ledger" ]] || return 0
  awk -F '\t' -v p="$path" '$1 == p { print $2"\t"$3; found=1 } END { exit found ? 0 : 1 }' "$ledger" || true
}

# ---------------------------------------------------------------------------
# ledger_put <ledger_file> <path> <sig> <outcome>
#
# Upserts a ledger line for <path> (replaces any existing line for that
# path, appends otherwise). Ledger format: path<TAB>sig<TAB>outcome.
# ---------------------------------------------------------------------------
ledger_put() {
  local ledger="$1" path="$2" sig="$3" outcome="$4"
  local tmp
  tmp="$(mktemp)"
  if [[ -f "$ledger" ]]; then
    awk -F '\t' -v p="$path" '$1 != p' "$ledger" > "$tmp" || true
  fi
  printf '%s\t%s\t%s\n' "$path" "$sig" "$outcome" >> "$tmp"
  mv "$tmp" "$ledger"
}

# ---------------------------------------------------------------------------
# find_review_files <reviews_dir>
#
# Prints one path per line for every file matching sprint-<N>-*review*.md,
# sorted for determinism.
# ---------------------------------------------------------------------------
find_review_files() {
  local dir="$1"
  local f name
  shopt -s nullglob
  for f in "$dir"/sprint-*-*review*.md; do
    name="$(basename "$f")"
    if [[ "$name" =~ ^sprint-([0-9]+)-.*review.*\.md$ ]]; then
      printf '%s\n' "$f"
    fi
  done | sort
  shopt -u nullglob
}

# ---------------------------------------------------------------------------
# sprint_of <path>
#
# Extracts the sprint number N from a reviews/sprint-<N>-*review*.md path.
# ---------------------------------------------------------------------------
sprint_of() {
  local name
  name="$(basename "$1")"
  [[ "$name" =~ ^sprint-([0-9]+)- ]] && printf '%s' "${BASH_REMATCH[1]}"
}

# ---------------------------------------------------------------------------
# findings_of <review_file>
#
# Echoes the "## Findings" section of a review file (from that heading up
# to, but excluding, the next "## " heading), if present.
# ---------------------------------------------------------------------------
findings_of() {
  local file="$1"
  awk '/^## Findings/{flag=1; print; next} /^## /{if (flag) exit} flag' "$file"
}

# ---------------------------------------------------------------------------
# run_intake_pass <repo_root> <verify_review_bin> <ledger_file>
#
# Core, testable logic. repo_root holds reviews/ and docs/ (and is the git
# working tree commits are made in); verify_review_bin is the
# verify-review.sh to invoke (real one for normal use, a fixture-local copy
# for --selftest, so its own REPO_ROOT resolution lands on the fixture
# tree); ledger_file is the intake ledger to read/update.
#
# Prints the per-action lines and the final summary; returns 0 on a clean
# pass, non-zero on an internal error.
# ---------------------------------------------------------------------------
run_intake_pass() {
  local repo_root="$1" verify_bin="$2" ledger="$3"
  local reviews_dir="${repo_root}/reviews"
  local docs_dir="${repo_root}/docs"

  local -a files
  mapfile -t files < <(find_review_files "$reviews_dir")

  # Group files by sprint number, preserving first-seen order.
  local -A seen_sprint=()
  local -a sprints=()
  local f n
  for f in "${files[@]}"; do
    n="$(sprint_of "$f")"
    [[ -z "$n" ]] && continue
    if [[ -z "${seen_sprint[$n]:-}" ]]; then
      seen_sprint["$n"]=1
      sprints+=("$n")
    fi
  done

  local c=0 r=0 s=0 u=0 k=0

  for n in "${sprints[@]}"; do
    local -a n_files=()
    for f in "${files[@]}"; do
      [[ "$(sprint_of "$f")" == "$n" ]] && n_files+=("$f")
    done

    # Does any file for this sprint have a new/changed signature?
    local changed=0
    local sig lookup old_sig
    for f in "${n_files[@]}"; do
      sig="$(content_hash "$f")"
      lookup="$(ledger_lookup "$ledger" "$f")"
      old_sig="${lookup%%$'\t'*}"
      if [[ -z "$lookup" || "$old_sig" != "$sig" ]]; then
        changed=1
      fi
    done

    if [[ "$changed" -eq 0 ]]; then
      echo "skip sprint ${n} — already processed, unchanged"
      k=$((k + 1))
      continue
    fi

    local out rc
    if out="$(bash "$verify_bin" "$n" 2>&1)"; then
      rc=0
    else
      rc=$?
    fi

    local selected
    selected="$(printf '%s\n' "$out" | grep -m1 -E '^Selected review file: ' | sed -E 's/^Selected review file: //')"
    if [[ -z "$selected" ]]; then
      # verify-review.sh could not even pick a file — fall back to the
      # newest file we found for this sprint so intake still has something
      # concrete to report/record against.
      selected="${n_files[-1]}"
    fi

    local outcome
    case "$rc" in
      0)
        echo "CLEARED sprint ${n}"
        echo "$out"
        git -C "$repo_root" add -- "$selected"
        local demo_note="${docs_dir}/demo-notes-sprint-${n}.md"
        if [[ -f "$demo_note" ]]; then
          git -C "$repo_root" add -- "$demo_note"
        fi
        if ! git -C "$repo_root" diff --cached --quiet -- "$selected" "$demo_note" 2>/dev/null; then
          git -C "$repo_root" commit -q -m "docs(review): land Codex sprint ${n} verdict (intake)"
        fi
        outcome="cleared"
        c=$((c + 1))
        ;;
      3)
        echo "REJECTED sprint ${n} — surface blockers to next sprint backlog"
        echo "$out"
        findings_of "$selected"
        git -C "$repo_root" add -- "$selected"
        if ! git -C "$repo_root" diff --cached --quiet -- "$selected" 2>/dev/null; then
          git -C "$repo_root" commit -q -m "docs(review): record Codex sprint ${n} reject verdict (intake)"
        fi
        outcome="rejected"
        r=$((r + 1))
        ;;
      2)
        echo "STALE sprint ${n} — re-request a fresh review (AUTO-1 dedup symptom)"
        echo "$out"
        outcome="stale"
        s=$((s + 1))
        ;;
      4)
        echo "UNKNOWN sprint ${n} — fix handoff Review-Target-Commit / verdict metadata"
        echo "$out"
        outcome="unknown"
        u=$((u + 1))
        ;;
      *)
        echo "INTERNAL ERROR: verify-review.sh returned unexpected exit code ${rc} for sprint ${n}" >&2
        return 1
        ;;
    esac

    # Record every physical review file for this sprint against the current
    # pass outcome, so unchanged sibling round files don't keep re-triggering.
    for f in "${n_files[@]}"; do
      ledger_put "$ledger" "$f" "$(content_hash "$f")" "$outcome"
    done
  done

  echo "intake: ${c} cleared, ${r} rejected, ${s} stale, ${u} unknown, ${k} skipped"
  return 0
}

# ---------------------------------------------------------------------------
# run_selftest
#
# Throwaway fixtures only — never the real repo. Builds a fixture tree with
# its own git history (mirroring verify-review.sh's own --selftest commit
# shape) and a fixture-local copy of the REAL verify-review.sh, so its
# REPO_ROOT resolution (SCRIPT_DIR/..) lands on the fixture tree and we
# exercise its genuine exit codes rather than a reimplementation.
# ---------------------------------------------------------------------------
run_selftest() {
  local real_verify="$1"
  local tmp
  tmp="$(mktemp -d)"
  # shellcheck disable=SC2064
  trap "rm -rf '${tmp}'" RETURN

  mkdir -p "${tmp}/reviews" "${tmp}/docs/backlog" "${tmp}/scripts"
  cp "$real_verify" "${tmp}/scripts/verify-review.sh"
  chmod +x "${tmp}/scripts/verify-review.sh"

  git -C "$tmp" init -q
  git -C "$tmp" config user.email "selftest@example.invalid"
  git -C "$tmp" config user.name "review-intake selftest"
  git -C "$tmp" add -A
  git -C "$tmp" commit -q -m "C0 fixture scaffold"

  git -C "$tmp" commit --allow-empty -q -m "C1 root"
  git -C "$tmp" commit --allow-empty -q -m "C2 fix"
  local fix_sha
  fix_sha="$(git -C "$tmp" rev-parse HEAD)"
  git -C "$tmp" commit --allow-empty -q -m "C3 later"
  local later_sha
  later_sha="$(git -C "$tmp" rev-parse HEAD)"
  local root_sha
  root_sha="$(git -C "$tmp" rev-parse HEAD~2)"

  local ledger="${tmp}/.review-intake-state"
  local fail=0

  echo "--- review-intake.sh --selftest ---"

  # --- Case A: sprint 201 — FRESH+ACCEPT on first pass -----------------------
  cat > "${tmp}/docs/backlog/sprint-201-handoff.md" <<EOF
# Sprint 201 handoff
Review-Target-Commit: ${fix_sha}
EOF
  cat > "${tmp}/reviews/sprint-201-track-b-review.md" <<EOF
Review-Target-Commit: ${later_sha}
Verdict: ACCEPT

## Findings

None.
EOF
  cat > "${tmp}/docs/demo-notes-sprint-201.md" <<EOF
# Demo notes sprint 201
Nothing presentation-worthy.
EOF

  local out
  out="$(run_intake_pass "$tmp" "${tmp}/scripts/verify-review.sh" "$ledger" 2>&1)" || { echo "$out"; fail=1; }
  if [[ "$out" == *"CLEARED sprint 201"* && "$out" == *"1 cleared"* ]]; then
    echo "[ok] ACCEPT branch — CLEARED, counted"
  else
    echo "[FAIL] ACCEPT branch"; echo "$out"; fail=1
  fi
  if git -C "$tmp" log --oneline -- reviews/sprint-201-track-b-review.md | grep -q "sprint 201"; then
    echo "[ok] ACCEPT branch committed the review file"
  else
    echo "[FAIL] ACCEPT branch did not commit the review file"; fail=1
  fi
  if git -C "$tmp" log --oneline -- docs/demo-notes-sprint-201.md | grep -q "sprint 201"; then
    echo "[ok] ACCEPT branch committed the matching demo note"
  else
    echo "[FAIL] ACCEPT branch did not commit the demo note"; fail=1
  fi

  # --- Idempotency: unchanged review on 2nd pass => skipped ------------------
  out="$(run_intake_pass "$tmp" "${tmp}/scripts/verify-review.sh" "$ledger" 2>&1)" || { echo "$out"; fail=1; }
  if [[ "$out" == *"skip sprint 201"* && "$out" == *"0 cleared"* && "$out" == *"1 skipped"* ]]; then
    echo "[ok] unchanged review on 2nd pass — skipped (ledger idempotency)"
  else
    echo "[FAIL] idempotency check"; echo "$out"; fail=1
  fi
  local before_head after_head
  before_head="$(git -C "$tmp" rev-parse HEAD)"

  # --- Updated review (content changed) => reprocessed -----------------------
  cat > "${tmp}/reviews/sprint-201-track-b-review.md" <<EOF
Review-Target-Commit: ${later_sha}
Verdict: ACCEPT — updated round

## Findings

None (round 2 text).
EOF
  out="$(run_intake_pass "$tmp" "${tmp}/scripts/verify-review.sh" "$ledger" 2>&1)" || { echo "$out"; fail=1; }
  after_head="$(git -C "$tmp" rev-parse HEAD)"
  if [[ "$out" == *"CLEARED sprint 201"* && "$out" == *"1 cleared"* && "$before_head" != "$after_head" ]]; then
    echo "[ok] updated review content => reprocessed and re-committed"
  else
    echo "[FAIL] updated-review reprocessing"; echo "$out"; fail=1
  fi

  # --- Case B: sprint 202 — FRESH+REJECT --------------------------------------
  cat > "${tmp}/docs/backlog/sprint-202-handoff.md" <<EOF
# Sprint 202 handoff
Review-Target-Commit: ${fix_sha}
EOF
  cat > "${tmp}/reviews/sprint-202-track-b-review.md" <<EOF
Review-Target-Commit: ${later_sha}
Verdict: REJECT / not cleared

## Findings

### P1 - fixture blocker
This is a fixture-only blocker for selftest.
EOF
  before_head="$(git -C "$tmp" rev-parse HEAD)"
  out="$(run_intake_pass "$tmp" "${tmp}/scripts/verify-review.sh" "$ledger" 2>&1)" || { echo "$out"; fail=1; }
  after_head="$(git -C "$tmp" rev-parse HEAD)"
  if [[ "$out" == *"REJECTED sprint 202"* && "$out" == *"1 rejected"* && "$out" == *"fixture blocker"* ]]; then
    echo "[ok] REJECT branch — REJECTED, findings echoed, counted"
  else
    echo "[FAIL] REJECT branch"; echo "$out"; fail=1
  fi
  if [[ "$before_head" != "$after_head" ]] && git -C "$tmp" log --oneline -- reviews/sprint-202-track-b-review.md | grep -q "sprint 202"; then
    echo "[ok] REJECT branch committed the review file"
  else
    echo "[FAIL] REJECT branch did not commit the review file"; fail=1
  fi

  # --- Case C: sprint 203 — STALE (reviewed sha predates the fix) ------------
  cat > "${tmp}/docs/backlog/sprint-203-handoff.md" <<EOF
# Sprint 203 handoff
Review-Target-Commit: ${fix_sha}
EOF
  cat > "${tmp}/reviews/sprint-203-track-b-review.md" <<EOF
Review-Target-Commit: ${root_sha}
Verdict: ACCEPT
EOF
  before_head="$(git -C "$tmp" rev-parse HEAD)"
  out="$(run_intake_pass "$tmp" "${tmp}/scripts/verify-review.sh" "$ledger" 2>&1)" || { echo "$out"; fail=1; }
  after_head="$(git -C "$tmp" rev-parse HEAD)"
  if [[ "$out" == *"STALE sprint 203"* && "$out" == *"1 stale"* && "$before_head" == "$after_head" ]]; then
    echo "[ok] STALE branch — no commit, counted"
  else
    echo "[FAIL] STALE branch"; echo "$out"; fail=1
  fi
  if git -C "$tmp" log --oneline -- reviews/sprint-203-track-b-review.md | grep -q "."; then
    echo "[FAIL] STALE branch committed the review file (must not)"; fail=1
  else
    echo "[ok] STALE branch left the review file uncommitted"
  fi

  # --- Case D: sprint 204 — UNKNOWN (unparseable verdict) ---------------------
  cat > "${tmp}/docs/backlog/sprint-204-handoff.md" <<EOF
# Sprint 204 handoff
Review-Target-Commit: ${fix_sha}
EOF
  cat > "${tmp}/reviews/sprint-204-track-b-review.md" <<EOF
Review-Target-Commit: ${later_sha}
Verdict: TBD pending discussion
EOF
  before_head="$(git -C "$tmp" rev-parse HEAD)"
  out="$(run_intake_pass "$tmp" "${tmp}/scripts/verify-review.sh" "$ledger" 2>&1)" || { echo "$out"; fail=1; }
  after_head="$(git -C "$tmp" rev-parse HEAD)"
  if [[ "$out" == *"UNKNOWN sprint 204"* && "$out" == *"1 unknown"* && "$before_head" == "$after_head" ]]; then
    echo "[ok] UNKNOWN branch — no commit, counted"
  else
    echo "[FAIL] UNKNOWN branch"; echo "$out"; fail=1
  fi
  if git -C "$tmp" log --oneline -- reviews/sprint-204-track-b-review.md | grep -q "."; then
    echo "[FAIL] UNKNOWN branch committed the review file (must not)"; fail=1
  else
    echo "[ok] UNKNOWN branch left the review file uncommitted"
  fi

  echo "--- end selftest cases ---"

  if [[ "$fail" -eq 0 ]]; then
    echo "SELFTEST PASS"
    return 0
  else
    echo "SELFTEST FAIL"
    return 1
  fi
}

# ---------------------------------------------------------------------------
# Dispatch
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

if [[ "${1:-}" == "--selftest" ]]; then
  run_selftest "${SCRIPT_DIR}/verify-review.sh"
  exit $?
fi

if [[ $# -ne 0 ]]; then
  echo "Usage: $(basename "$0")               # one intake pass over reviews/" >&2
  echo "       $(basename "$0") --selftest" >&2
  exit 1
fi

run_intake_pass "${REPO_ROOT}" "${SCRIPT_DIR}/verify-review.sh" "${REPO_ROOT}/.review-intake-state"
exit $?
