# Sprint 13 — Track A close-out, round 11

**Why this sprint exists:** Sprint 12 was rejected (see `reviews/sprint-12-track-a-review.md`). The `orderCards` locator using `mat-card.filter({ has: .order-header })` matched the outer "Orders" container `mat-card` (which has all `.order-header` descendants), so `.first()` pinned to the container instead of the newest individual order card. The scoped `.order-header` and `.badge-confirmed` assertions then resolved to all orders in the container, re-triggering a strict-mode violation.

## Tasks

| ID | Title | Priority |
|----|-------|----------|
| Q-1 | Add `data-testid="order-card"` to individual order card; update smoke test locator | Must fix |

## Dependency currency check

No framework changes this sprint.
