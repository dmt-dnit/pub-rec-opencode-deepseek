# Conference / YouTube Prep Handoff — for Codex

**Prepared by:** Claude Code (coordinator)  
**Date:** 2026-06-28  
**Context:** Track A is closed. Dimitri is considering presenting this project — "Build Smart with Agents" — at dev conferences or on YouTube. This handoff asks Codex to produce the documentation and visual assets that require a running stack and a real browser, which the coordinator and opencode cannot do from WSL.

---

## Why this is worth presenting

Most "build with AI" demos show the happy path. This project shows the real workflow:

- A multi-agent loop with separated roles (implementer / coordinator / reviewer)
- Honest failure modes (13 sprints to close Track A, including a CD bug introduced by the coordinator)
- Rules that emerged from actual pain, not theory
- A retro that names root causes instead of glossing them

The demo itself (Kafka choreographed saga + JWT SSO + Angular) is recognisable enterprise territory — not a toy, but explainable in 10 minutes.

**The five pivotal moments worth highlighting in any talk:**
1. **Sprint 1** — workflow established; concurrent edits on one shared tree caused cross-file contamination; worktrees added
2. **Sprint 4** — reached Codex with 2 of 5 tasks unstarted and no commit checkpoint; `pre-review-check.sh` added
3. **Sprint 9 M-1** — *coordinator* introduced an Angular CD bug by removing `ChangeDetectionStrategy.Eager` as "code hygiene"; cost two sprint rounds; the honest "agents aren't magic" moment
4. **Sprint 11** — `mat-table` empty despite all CD fixes; root cause required reading compiled Angular 22 source; fixed with `MatTableDataSource`
5. **Sprint 13** — Track A approved; full Playwright smoke passes against a non-empty local DB

**The retro** (`docs/backlog/track-a-retro.md`) captures all three perspectives (coordinator, reviewer, implementer) and the working rules for Track B. It is the densest single source of "what we actually learned."

---

## What Codex is asked to produce

### 1. Root `README.md`

Rewrite (or create if missing) `README.md` at the repo root. Audience: a developer who found this repo from a talk or YouTube link. Must cover:

- **What this system does** — 2–3 sentences: JWT SSO auth server + order/inventory saga over Kafka + two Angular SPAs
- **The agent workflow** — one short section: who the three roles are (implementer, coordinator, reviewer), how they hand off, what tooling each uses. A simple ASCII or Mermaid flow diagram is enough.
- **Architecture diagram** — the saga flow: `order-ui → order-service → order-events (Kafka) → inventory-service → inventory-events → order-service → order-ui`. Mermaid preferred.
- **How to run the demo locally** — the minimal command sequence to get the full stack up (Kafka via Docker Compose, then the four Spring services, then both Angular UIs)
- **Screenshots** — at minimum: the order-ui dashboard showing a placed order with CONFIRMED status, and the inventory-ui dashboard showing the SKU table and a RESERVED chip in the reservation feed. Real screenshots from the running app, not mocks.
- **Link to the retro and the talk outline** (once produced)

### 2. `docs/story.md` — The narrative arc

A prose document (800–1200 words) suitable for a blog post or talk notes. Structure:

1. **The premise** — why a multi-agent loop instead of one developer or one AI session
2. **The workflow** — the three-role setup and how a sprint works end to end
3. **What went wrong** — the five pivotal moments above, honestly told
4. **What we changed** — the rules that came out of each failure
5. **Where it landed** — Track A closed, what the retro said, what Track B will validate
6. **What this means for "build smart with agents"** — 2–3 concrete takeaways for a developer who wants to try this

### 3. `docs/demo-script.md` — Live demo presenter guide

A step-by-step script for running the demo on stage or on camera. Should include:

- Prerequisites and startup sequence (exact commands)
- How to run Playwright in **headed** (non-headless) mode so the audience sees the browser:
  ```powershell
  cd e2e
  npx playwright test --headed
  ```
  Or manually walk through the scenario step by step in the browser if that reads better on camera.
- What to narrate at each step (what the audience should be looking at and why it matters)
- What to do if something goes wrong on stage (fallback: screenshots already captured)
- Approximate runtime for a 5-minute demo slot and a 10-minute demo slot

### 4. `docs/talk-outline.md` — Slide structure

A rough outline (not the actual slide deck — just the structure) for a 30-minute conference talk or a 15-minute YouTube video. Suggested sections:

1. The problem with typical AI coding demos (2 min)
2. The workflow we used instead — roles, handoffs, tooling (5 min)
3. Three things that failed and the root cause of each (8 min) — pick the three most instructive from the retro
4. The rules that came out of the retro (3 min)
5. Live demo or recorded walkthrough (5 min)
6. What Track B will validate / where this goes next (2 min)
7. Q&A prompt: "What would you do differently?" (open)

Include speaker notes for the three failure sections — these are the credibility anchors of the talk.

---

## Technical notes for running the demo

**Full stack startup (from PowerShell):**
```powershell
# 1. Kafka + Zookeeper
cd C:\projects\pub-rec-opencode-deepseek\order-service
docker compose up -d

# 2. Java services (each in its own terminal)
cd C:\projects\pub-rec-opencode-deepseek\auth-server && .\mvnw.cmd spring-boot:run
cd C:\projects\pub-rec-opencode-deepseek\order-service && .\mvnw.cmd spring-boot:run
cd C:\projects\pub-rec-opencode-deepseek\inventory-service && .\mvnw.cmd spring-boot:run

# 3. Angular UIs (each in its own terminal)
cd C:\projects\pub-rec-opencode-deepseek\order-ui && npm.cmd start
cd C:\projects\pub-rec-opencode-deepseek\inventory-ui && npm.cmd start
```

**Seeded accounts:**
- `customer1@example.test` / `customer123` — use for order-ui (port 4200)
- `warehouse1@example.test` / `warehouse123` — use for inventory-ui (port 4201)
- Admin: see `auth-server` DataSeeder for admin credentials

**Playwright headed run (for demo capture):**
```powershell
cd C:\projects\pub-rec-opencode-deepseek\e2e
npm install
npx playwright install chromium
npx playwright test --headed
```

The smoke test (`smoke.spec.ts`) exercises the full saga end-to-end and is already the demo script. Running it headed shows two browser windows side-by-side: order-ui (customer) and inventory-ui (warehouse staff).

**Screenshots:** capture at minimum —
- inventory-ui after login: SKU table with SKU-001, SKU-002, SKU-003 rows visible
- order-ui after placing an order: order card showing CONFIRMED status badge
- inventory-ui after saga completes: reservation feed showing RESERVED chip, SKU-001 quantity decremented

---

## Files to read for context

- `docs/backlog/track-a-retro.md` — the full retro with all three perspectives and synthesis; this is the content source for the talk
- `CLAUDE.md` — current status snapshot, workflow rules, architecture description
- `reviews/sprint-9-track-a-review.md` — the Sprint 9 failure that caught the coordinator's Angular CD bug (the most honest and interesting failure)
- `reviews/sprint-13-track-a-review.md` — Track A approval; the DOM diagnostic that finally cleared the browser gate
- `docs/adr/0001-event-driven-showcase-architecture.md` — the original architecture decision record

---

## Scope and constraints

- Do not modify any source code (`.ts`, `.java`, `.json`) as part of this task — these are documentation and asset deliverables only
- Screenshots should be real captures from the running app, not edited or mocked
- If the full stack can't be started for any reason, note it and produce the written deliverables without the visual assets — do not fake screenshots
- Commit all four deliverables (`README.md`, `docs/story.md`, `docs/demo-script.md`, `docs/talk-outline.md`) in a single commit with message `docs: add conference/YouTube presentation assets`
