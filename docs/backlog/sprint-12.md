# Sprint 12 — Track A close-out, round 10

**Why this sprint exists:** Sprint 11 was rejected by Codex (see `reviews/sprint-11-track-a-review.md`). O-1 and O-2 both passed. The single remaining blocker is a Playwright strict-mode violation: after previous smoke runs the H2 database accumulates orders, so `locator('.order-header')` and `locator('.badge-confirmed')` resolve to multiple elements and Playwright throws.

## Tasks

| ID | Title | Priority |
|----|-------|----------|
| P-1 | Scope Playwright order assertions to newly placed order | Must fix |

## Dependency currency check

No framework changes this sprint — Angular 22.0.4, Java 21, Spring Boot 3.x unchanged.
