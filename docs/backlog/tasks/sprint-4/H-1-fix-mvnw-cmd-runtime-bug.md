# Task H-1: Fix the actual runtime bug in `mvnw.cmd`

**Resolves:** Must-fix 1 in `reviews/sprint-3-track-a-review.md` ("the new Windows wrapper exists, but it is not usable from this Windows/PowerShell review environment. Running `.\mvnw.cmd -v` in `shared-model` fails with `Cannot index into a null array` and `Cannot start maven from wrapper`").

## Context

Sprint 3's `G-1` generated `mvnw.cmd` via `mvn -N wrapper:wrapper -Dmaven=3.9.9` in all 4 modules and verified it by (a) confirming the file matched the genuine Apache Maven Wrapper 3.3.4 template byte-for-byte and (b) running the *bash* `mvnw -v`, which worked. Neither check actually executed `mvnw.cmd` in PowerShell — Codex's review is the first real execution, and it fails at `shared-model/mvnw.cmd:35` with `Cannot index into a null array` / `Cannot start maven from wrapper`. **A template matching a known-good reference is not the same as the script working** — this task exists because that gap produced a false "done."

The wrapper is generated with `distributionType=only-script` (see `shared-model/.mvn/wrapper/maven-wrapper.properties`), which is the newer hybrid-script mode that doesn't ship a `maven-wrapper.jar`. This mode's `.cmd` script does its own inline parsing/bootstrapping in PowerShell, and there are known compatibility issues with this specific generation mode across different Maven Wrapper plugin versions and PowerShell configurations — the null-array error is consistent with the script expecting a property (likely something only populated when `distributionType` is the older jar-based mode) that isn't present in `only-script` mode's properties file.

## Task

1. Reproduce the failure if you have any Windows/PowerShell access. If you don't, you'll have to work from the error message and the actual script content alone — read `shared-model/mvnw.cmd` around line 35 and trace what variable/array is null at that point, cross-referencing against `shared-model/.mvn/wrapper/maven-wrapper.properties`.
2. The most likely fix paths, in order of preference:
   - Try a different `wrapperVersion` (regenerate with an older or newer Maven Wrapper plugin release than 3.3.4 — check the plugin's changelog/issue tracker for known `only-script` + Windows bugs around this version).
   - If `only-script` mode is the root cause, switch `distributionType` to the classic jar-based mode instead (regenerate without `-Dtype=only-script`, restoring `maven-wrapper.jar`) — this is the older, more battle-tested `.cmd` path and was working before Sprint 3 touched it. Sprint 3's switch to `only-script` wasn't a requirement of the original brief, just what the wrapper plugin defaulted to — reverting it is not a regression.
   - Only as a last resort, hand-patch the generated `.cmd` script — if you do, document exactly what was wrong and why, since hand-patching a generated file means it'll silently break again the next time someone regenerates it.
3. Apply the same fix identically across all 4 modules (`shared-model`, `auth-server`, `order-service`, `inventory-service`) — they must stay consistent.

## Out of scope
- Don't touch the bash `mvnw` scripts — Codex confirms those work; this is `.cmd`-only.
- Don't touch the Java toolchain enforcer or any `pom.xml`.

## Acceptance criteria
- `.\mvnw.cmd -v` succeeds in all 4 modules on an actual Windows/PowerShell environment, reporting the pinned Maven version — not just "the file matches a template." If you cannot test on real Windows/PowerShell yourself, say so explicitly in the report and explain precisely why the chosen fix should resolve the specific error Codex hit, so the next review is the real confirmation rather than a second blind guess.
- `./mvnw -v` still works on Linux/macOS in all 4 modules (no regression).
- All 4 modules' wrapper config stay consistent with each other (same `distributionType`, same `wrapperVersion`, same Maven version).
