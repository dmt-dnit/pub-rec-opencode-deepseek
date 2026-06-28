# Sprint 11 — Track A close-out, round 9

**Why this sprint exists:** Sprint 10 was rejected by Codex (see `reviews/sprint-10-track-a-review.md`). Two blockers remain:

1. **inventory-ui `mat-table` renders empty rows** even though `/api/inventory` returns stock data and `ChangeDetectionStrategy.Eager` is set on the component. The DOM diagnostic confirmed `rows: []`, `cells: []` after a successful HTTP response.
2. **Both committed `package-lock.json` files contain stale `sockjs-client` / `@types/sockjs-client` entries** that are absent from `package.json`. The N-3 lockfile regen used `--package-lock-only` against a `node_modules` directory that still had the old packages installed, causing the lock to re-include them.

Both were fixed directly by the coordinator in this sprint (no agent worktree needed — each is a targeted, well-understood change with high confidence).

## Tasks

| ID | Title | Priority |
|----|-------|----------|
| O-1 | Fix inventory-ui `mat-table` — use `MatTableDataSource` | Must fix |
| O-2 | Remove stale `sockjs-client` entries from both lockfiles | Must fix |

## Recommended order

O-2 then O-1 (lockfile fix has no risk, mat-table fix depends on build verification by Codex).

## Dependency currency check (start of sprint)

- Angular 22.0.4 — current stable, no newer release yet. No LTS concern.
- Spring Boot 3.x — check `pom.xml` pinned version on next sprint start.
- Java 21 — LTS through September 2026 minimum. No action needed.
- Kafka client — check on Track B sprint start.
