#!/usr/bin/env bash
# verify-review.sh — mechanically detect stale/fresh Codex review verdicts.
#
# Usage:
#   scripts/verify-review.sh <sprint-number> [expected-commit]
#   scripts/verify-review.sh --selftest
#
# See docs/backlog/review-machine-header.md for the header contract this
# script parses, and docs/backlog/tasks/sprint-20/AUTO-2-verify-review-freshness.md
# for the full spec this implements.
#
# Exit codes:
#   0 = FRESH + ACCEPT   (verdict is trustworthy, and it says accept)
#   2 = STALE            (verdict is not trustworthy — re-request review)
#   3 = FRESH + REJECT   (verdict is trustworthy, and it says reject — act on it)
#   4 = UNKNOWN / parse failure
set -euo pipefail

# ---------------------------------------------------------------------------
# pick_review_file <sprint> <reviews_dir>
#
# Globs reviews_dir/sprint-<N>-*review*.md and picks the highest-round file:
# a filename containing "round-<k>" outranks the base file; among round-<k>
# files, the max k wins; a file with no "round-<k>" token is treated as
# round 1 (the base/first review).
# ---------------------------------------------------------------------------
pick_review_file() {
  local sprint="$1" dir="$2"
  local best="" best_round=-1
  local f name round

  shopt -s nullglob
  for f in "$dir"/sprint-"$sprint"-*review*.md; do
    name="$(basename "$f")"
    if [[ "$name" =~ round-([0-9]+) ]]; then
      round="${BASH_REMATCH[1]}"
    else
      round=1
    fi
    if (( round > best_round )); then
      best_round="$round"
      best="$f"
    fi
  done
  shopt -u nullglob

  printf '%s' "$best"
}

# ---------------------------------------------------------------------------
# parse_review_file <file>
#
# Prints "<sha><US><verdict_raw>" (US = ASCII 0x1F separator) to stdout.
# Understands:
#   - the spec header: "Review-Target-Commit: <sha>" / "Verdict: <text>"
#   - the legacy freeform line: "Review target: `<sha>`[, `<sha2>`...]"
#     (first backticked sha wins), for pre-spec review files.
# ---------------------------------------------------------------------------
parse_review_file() {
  local file="$1"
  local sha="" verdict_raw=""

  sha="$(grep -m1 -iE '^Review-Target-Commit:' "$file" 2>/dev/null \
    | sed -E 's/^[^:]*:[[:space:]]*//' | tr -d '`' | awk '{print $1}')" || true

  if [[ -z "$sha" ]]; then
    sha="$(grep -m1 -iE '^Review target:' "$file" 2>/dev/null \
      | grep -oE '`[0-9a-fA-F]{6,40}`' | head -n1 | tr -d '`')" || true
  fi

  verdict_raw="$(grep -m1 -iE '^Verdict:' "$file" 2>/dev/null \
    | sed -E 's/^[^:]*:[[:space:]]*//')" || true

  printf '%s\x1f%s' "$sha" "$verdict_raw"
}

# ---------------------------------------------------------------------------
# classify_verdict <verdict_raw>
#
# Robust to negated blocker mentions ("no remaining ... blockers found") and
# to the machine-header contract. Precedence:
#   1. Standalone machine token: if the value, stripped of markdown `**` and
#      whitespace, is exactly ACCEPT or REJECT (case-insensitive), trust it
#      verbatim — this is the review-machine-header contract and beats any
#      prose heuristic.
#   2. Legacy prose: neutralize NEGATED blocker/reject phrases first
#      ("no blockers", "no remaining source-level blockers", "without
#      blockers", "zero blockers"), then keyword-match:
#        - reject|rejected|not cleared, or a surviving (non-negated) blocker(s)
#          ⇒ REJECT
#        - cleared|accept|accepted|approved ⇒ ACCEPT
#        - else UNKNOWN
# ---------------------------------------------------------------------------
classify_verdict() {
  local raw="${1:-}"

  # 1. Standalone machine token (ignore surrounding markdown ** and whitespace).
  local token
  token="$(printf '%s' "$raw" | tr -d '*' | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')"
  local token_lc
  token_lc="$(printf '%s' "$token" | tr '[:upper:]' '[:lower:]')"
  if [[ "$token_lc" == "accept" ]]; then
    printf 'ACCEPT'; return
  fi
  if [[ "$token_lc" == "reject" ]]; then
    printf 'REJECT'; return
  fi

  # 2. Legacy prose heuristic.
  local v_lc
  v_lc="$(printf '%s' "$raw" | tr '[:upper:]' '[:lower:]')"

  # Strip negated blocker phrases so they don't trip the REJECT match.
  # Covers: "no blockers", "no remaining blockers", "no remaining source-level
  # blockers", "no further blockers", "without blockers", "zero blockers".
  local v_neg="$v_lc"
  v_neg="$(printf '%s' "$v_neg" | sed -E 's/\bno ([a-z-]+ )*blockers?\b//g; s/\bwithout ([a-z-]+ )*blockers?\b//g; s/\bzero ([a-z-]+ )*blockers?\b//g')"

  if [[ "$v_neg" =~ reject|not[[:space:]]cleared|blocker ]]; then
    printf 'REJECT'
  elif [[ "$v_neg" =~ cleared|accept|approved ]]; then
    printf 'ACCEPT'
  else
    printf 'UNKNOWN'
  fi
}

# ---------------------------------------------------------------------------
# parse_handoff_target <handoff-file>
#
# Extracts the fix commit the review round was supposed to cover from the
# handoff. Prefers the machine field `Review-Target-Commit: <sha>`; tolerates
# a legacy freeform line mentioning "target commit" with a backticked sha.
# Prints the sha (empty if none found).
# ---------------------------------------------------------------------------
parse_handoff_target() {
  local file="$1"
  local sha=""
  [[ -f "$file" ]] || { printf ''; return; }

  sha="$(grep -m1 -iE '^Review-Target-Commit:' "$file" 2>/dev/null \
    | sed -E 's/^[^:]*:[[:space:]]*//' | tr -d '`' | awk '{print $1}')" || true

  if [[ -z "$sha" ]]; then
    sha="$(grep -m1 -iE 'target commit' "$file" 2>/dev/null \
      | grep -oE '`[0-9a-fA-F]{6,40}`' | head -n1 | tr -d '`')" || true
  fi

  printf '%s' "$sha"
}

# ---------------------------------------------------------------------------
# run_check <sprint> <expected-commit-or-empty> <reviews_dir> <repo_dir> <handoff_dir>
#
# Prints the one-line summary and returns the exit code documented at the
# top of this file. repo_dir is the git working tree used to resolve/compare
# commits (real repo root for normal use; a throwaway repo for --selftest).
# ---------------------------------------------------------------------------
run_check() {
  local sprint="$1" expected_in="${2:-}" reviews_dir="$3" repo_dir="$4" handoff_dir="$5"

  local file
  file="$(pick_review_file "$sprint" "$reviews_dir")"
  if [[ -z "$file" ]]; then
    echo "sprint ${sprint}: UNKNOWN — no review file found in ${reviews_dir} matching sprint-${sprint}-*review*.md"
    return 4
  fi
  echo "Selected review file: ${file}"

  local parsed sha verdict_raw
  parsed="$(parse_review_file "$file")"
  sha="${parsed%%$'\x1f'*}"
  verdict_raw="${parsed#*$'\x1f'}"

  # Resolve the "expected" commit = the fix commit the review round was meant
  # to cover. NEVER default to HEAD: HEAD drifts past the fix, which would
  # spuriously read as STALE.
  #   1. explicit $2 arg;
  #   2. else Review-Target-Commit: (or legacy "target commit `<sha>`") from
  #      the sprint handoff;
  #   3. else UNKNOWN (exit 4) — tell the caller to pass the commit or add the
  #      header. Do NOT silently fall back to HEAD.
  local handoff="${handoff_dir}/sprint-${sprint}-handoff.md"
  local expected=""
  if [[ -n "$expected_in" ]]; then
    expected="$expected_in"
  else
    expected="$(parse_handoff_target "$handoff")"
    if [[ -z "$expected" ]]; then
      echo "sprint ${sprint}: UNKNOWN — no expected commit given and no 'Review-Target-Commit:' in ${handoff}. Pass the fix commit as arg 2, or add a 'Review-Target-Commit: <sha>' line to the handoff. — file ${file}"
      return 4
    fi
  fi

  local verdict
  verdict="$(classify_verdict "$verdict_raw")"

  if [[ -z "$sha" ]]; then
    echo "sprint ${sprint}: STALE — could not parse a Review-Target-Commit / legacy \"Review target:\" sha from ${file} — verdict ${verdict} — expected ${expected:-<unresolved>} — file ${file}"
    return 2
  fi

  local reviewed_full=""
  if ! reviewed_full="$(git -C "$repo_dir" rev-parse --verify --quiet "${sha}^{commit}" 2>/dev/null)"; then
    echo "sprint ${sprint}: STALE — reviewed sha '${sha}' is not a known commit in this repo — verdict ${verdict} — expected ${expected:-<unresolved>} — file ${file}"
    return 2
  fi

  local expected_full=""
  if [[ -z "$expected" ]] || ! expected_full="$(git -C "$repo_dir" rev-parse --verify --quiet "${expected}^{commit}" 2>/dev/null)"; then
    echo "sprint ${sprint}: UNKNOWN — expected commit '${expected}' could not be resolved in this repo — file ${file}"
    return 4
  fi

  # Secondary staleness signal (warn only, non-authoritative).
  if [[ -f "$handoff" ]] && [[ ! "$file" -nt "$handoff" ]]; then
    echo "WARN: review file mtime is not newer than the handoff mtime (${file} vs ${handoff}) — corroborating staleness signal, commit ancestry below is authoritative"
  fi

  local fresh=0
  if git -C "$repo_dir" merge-base --is-ancestor "$expected_full" "$reviewed_full"; then
    fresh=1
  fi

  local short_expected short_reviewed
  short_expected="$(git -C "$repo_dir" rev-parse --short "$expected_full")"
  short_reviewed="$(git -C "$repo_dir" rev-parse --short "$reviewed_full")"

  if (( fresh == 0 )); then
    echo "sprint ${sprint}: STALE — verdict ${verdict} — reviewed ${short_reviewed} vs expected ${short_expected} — file ${file}"
    return 2
  fi

  case "$verdict" in
    ACCEPT)
      echo "sprint ${sprint}: FRESH — verdict ACCEPT — reviewed ${short_reviewed} vs expected ${short_expected} — file ${file}"
      return 0
      ;;
    REJECT)
      echo "sprint ${sprint}: FRESH — verdict REJECT — reviewed ${short_reviewed} vs expected ${short_expected} — file ${file}"
      return 3
      ;;
    *)
      echo "sprint ${sprint}: UNKNOWN — verdict text did not classify as ACCEPT or REJECT (raw: '${verdict_raw}') — reviewed ${short_reviewed} vs expected ${short_expected} — file ${file}"
      return 4
      ;;
  esac
}

# ---------------------------------------------------------------------------
# run_selftest
#
# Builds throwaway fixtures in a temp dir, including a throwaway `git init`
# repo (never the real repo), and exercises all four exit paths plus the
# round-file-selection and legacy-header-parsing logic. Prints SELFTEST PASS
# or SELFTEST FAIL.
# ---------------------------------------------------------------------------
run_selftest() {
  local tmp
  tmp="$(mktemp -d)"
  # shellcheck disable=SC2064
  trap "rm -rf '${tmp}'" RETURN

  local gitrepo="${tmp}/repo"
  local reviews="${tmp}/reviews"
  local docsdir="${tmp}/docs/backlog"
  mkdir -p "$gitrepo" "$reviews" "$docsdir"

  git -C "$gitrepo" init -q
  git -C "$gitrepo" config user.email "selftest@example.invalid"
  git -C "$gitrepo" config user.name "verify-review selftest"

  git -C "$gitrepo" commit --allow-empty -q -m "C1 root"
  git -C "$gitrepo" commit --allow-empty -q -m "C2 fix"
  local fix_sha
  fix_sha="$(git -C "$gitrepo" rev-parse HEAD)"
  git -C "$gitrepo" commit --allow-empty -q -m "C3 later"
  local later_sha
  later_sha="$(git -C "$gitrepo" rev-parse HEAD)"
  local root_sha
  root_sha="$(git -C "$gitrepo" rev-parse HEAD~2)"

  local fail=0 out rc got

  echo "--- verify-review.sh --selftest ---"

  # --- classify_verdict unit cases (the real-world strings) -----------------
  local -a cv_in cv_want
  cv_in=(
    "**B-8 cleared; no remaining source-level blockers found**"
    "B-8 cleared"
    "REJECT / not cleared"
    "1 blocker remaining before clearance"
    "ACCEPT"
    "**REJECT**"
    "ACCEPT — cleared"
    "TBD pending discussion"
  )
  cv_want=(ACCEPT ACCEPT REJECT REJECT ACCEPT REJECT ACCEPT UNKNOWN)
  local i
  for i in "${!cv_in[@]}"; do
    got="$(classify_verdict "${cv_in[$i]}")"
    if [[ "$got" == "${cv_want[$i]}" ]]; then
      echo "[ok] classify: '${cv_in[$i]}' => ${got}"
    else
      echo "[FAIL] classify: '${cv_in[$i]}' => ${got} (want ${cv_want[$i]})"
      fail=1
    fi
  done

  # Case 1: STALE — review targets root_sha, which predates the fix commit.
  cat > "${reviews}/sprint-101-track-b-review.md" <<EOF
Review-Target-Commit: ${root_sha}
Verdict: ACCEPT
EOF
  if out="$(run_check 101 "$fix_sha" "$reviews" "$gitrepo" "$docsdir" 2>&1)"; then rc=0; else rc=$?; fi
  if [[ $rc -eq 2 && "$out" == *"STALE"* ]]; then
    echo "[ok] STALE path — exit 2"
  else
    echo "[FAIL] STALE path — rc=${rc}"
    echo "$out"
    fail=1
  fi

  # Case 2: FRESH + ACCEPT — review targets later_sha, which contains the fix.
  cat > "${reviews}/sprint-102-track-b-review.md" <<EOF
Review-Target-Commit: ${later_sha}
Verdict: ACCEPT — cleared
EOF
  if out="$(run_check 102 "$fix_sha" "$reviews" "$gitrepo" "$docsdir" 2>&1)"; then rc=0; else rc=$?; fi
  if [[ $rc -eq 0 && "$out" == *"FRESH"* && "$out" == *"ACCEPT"* ]]; then
    echo "[ok] FRESH+ACCEPT path — exit 0"
  else
    echo "[FAIL] FRESH+ACCEPT path — rc=${rc}"
    echo "$out"
    fail=1
  fi

  # Case 3: FRESH + REJECT.
  cat > "${reviews}/sprint-103-track-b-review.md" <<EOF
Review-Target-Commit: ${later_sha}
Verdict: REJECT / not cleared
EOF
  if out="$(run_check 103 "$fix_sha" "$reviews" "$gitrepo" "$docsdir" 2>&1)"; then rc=0; else rc=$?; fi
  if [[ $rc -eq 3 && "$out" == *"FRESH"* && "$out" == *"REJECT"* ]]; then
    echo "[ok] FRESH+REJECT path — exit 3"
  else
    echo "[FAIL] FRESH+REJECT path — rc=${rc}"
    echo "$out"
    fail=1
  fi

  # Case 4: UNKNOWN — freshness resolves fine, but verdict text is unclassifiable.
  cat > "${reviews}/sprint-104-track-b-review.md" <<EOF
Review-Target-Commit: ${later_sha}
Verdict: TBD pending discussion
EOF
  if out="$(run_check 104 "$fix_sha" "$reviews" "$gitrepo" "$docsdir" 2>&1)"; then rc=0; else rc=$?; fi
  if [[ $rc -eq 4 && "$out" == *"UNKNOWN"* ]]; then
    echo "[ok] UNKNOWN path — exit 4"
  else
    echo "[FAIL] UNKNOWN path — rc=${rc}"
    echo "$out"
    fail=1
  fi

  # Case 5: round-file selection — base file is a stale reject, round-2 is a
  # fresh accept. The script must pick round-2, not the base file.
  cat > "${reviews}/sprint-105-track-b-review.md" <<EOF
Review target: \`${root_sha}\`
Verdict: **REJECT / not cleared**
EOF
  cat > "${reviews}/sprint-105-track-b-round-2-review.md" <<EOF
Review-Target-Commit: ${later_sha}
Verdict: ACCEPT
EOF
  if out="$(run_check 105 "$fix_sha" "$reviews" "$gitrepo" "$docsdir" 2>&1)"; then rc=0; else rc=$?; fi
  if [[ $rc -eq 0 && "$out" == *"round-2-review.md"* ]]; then
    echo "[ok] round-<k> file selection prefers highest round — exit 0"
  else
    echo "[FAIL] round-file selection — rc=${rc}"
    echo "$out"
    fail=1
  fi

  # Case 6: legacy freeform "Review target:" line with multiple backticked
  # shas — first sha wins, and it's stale (predates the fix).
  cat > "${reviews}/sprint-106-track-b-review.md" <<EOF
Review target: \`${root_sha}\`, \`${later_sha}\`
Verdict: **REJECT / not cleared**
EOF
  if out="$(run_check 106 "$fix_sha" "$reviews" "$gitrepo" "$docsdir" 2>&1)"; then rc=0; else rc=$?; fi
  if [[ $rc -eq 2 && "$out" == *"STALE"* ]]; then
    echo "[ok] legacy freeform multi-sha parse + STALE — exit 2"
  else
    echo "[FAIL] legacy freeform parse — rc=${rc}"
    echo "$out"
    fail=1
  fi

  # Case 7: no review file at all for the sprint number — UNKNOWN/parse failure.
  if out="$(run_check 999 "$fix_sha" "$reviews" "$gitrepo" "$docsdir" 2>&1)"; then rc=0; else rc=$?; fi
  if [[ $rc -eq 4 ]]; then
    echo "[ok] no-review-file-found path — exit 4"
  else
    echo "[FAIL] no-review-file-found path — rc=${rc}"
    echo "$out"
    fail=1
  fi

  # Case 8: real-world round-2 string — a "cleared; no remaining blockers"
  # accept, FRESH against the fix commit. Guards Bug A at run_check level.
  cat > "${reviews}/sprint-107-track-b-round-2-review.md" <<EOF
Review target: \`${later_sha}\`
Verdict: **B-8 cleared; no remaining source-level blockers found**
EOF
  if out="$(run_check 107 "$fix_sha" "$reviews" "$gitrepo" "$docsdir" 2>&1)"; then rc=0; else rc=$?; fi
  if [[ $rc -eq 0 && "$out" == *"FRESH"* && "$out" == *"ACCEPT"* ]]; then
    echo "[ok] real 'cleared; no remaining blockers' string => FRESH+ACCEPT — exit 0"
  else
    echo "[FAIL] real cleared-string case — rc=${rc}"
    echo "$out"
    fail=1
  fi

  # Case 9: no-arg resolution via handoff Review-Target-Commit: header.
  # Handoff carries the fix commit; script must resolve it (not HEAD) => FRESH.
  cat > "${reviews}/sprint-108-track-b-review.md" <<EOF
Review-Target-Commit: ${later_sha}
Verdict: ACCEPT
EOF
  cat > "${docsdir}/sprint-108-handoff.md" <<EOF
# Sprint 108 handoff
Review-Target-Commit: ${later_sha}
EOF
  if out="$(run_check 108 "" "$reviews" "$gitrepo" "$docsdir" 2>&1)"; then rc=0; else rc=$?; fi
  if [[ $rc -eq 0 && "$out" == *"FRESH"* && "$out" == *"ACCEPT"* ]]; then
    echo "[ok] no-arg resolves expected from handoff header => FRESH+ACCEPT — exit 0"
  else
    echo "[FAIL] no-arg handoff-resolution case — rc=${rc}"
    echo "$out"
    fail=1
  fi

  # Case 10: no-arg with NO handoff and NO arg => UNKNOWN exit 4 (never STALE,
  # never silent HEAD fallback). Guards Bug B.
  cat > "${reviews}/sprint-109-track-b-review.md" <<EOF
Review-Target-Commit: ${later_sha}
Verdict: ACCEPT
EOF
  if out="$(run_check 109 "" "$reviews" "$gitrepo" "$docsdir" 2>&1)"; then rc=0; else rc=$?; fi
  if [[ $rc -eq 4 && "$out" == *"UNKNOWN"* && "$out" != *"STALE"* ]]; then
    echo "[ok] no-arg + no handoff => UNKNOWN — exit 4"
  else
    echo "[FAIL] no-arg no-handoff case — rc=${rc}"
    echo "$out"
    fail=1
  fi

  echo "--- end selftest cases ---"

  if [[ $fail -eq 0 ]]; then
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
if [[ "${1:-}" == "--selftest" ]]; then
  run_selftest
  exit $?
fi

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $(basename "$0") <sprint-number> [expected-commit]" >&2
  echo "       $(basename "$0") --selftest" >&2
  exit 4
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

run_check "$1" "${2:-}" "${REPO_ROOT}/reviews" "${REPO_ROOT}" "${REPO_ROOT}/docs/backlog"
exit $?
