# Task F-5: Repo-wide leftover-rename sweep + `CLAUDE.md` rewrite

**Resolves:** Should-fix 2 and Should-fix 3 in `reviews/sprint-1-track-a-review.md`.

**Run this task last, after F-1 through F-4, F-6, and F-7 are all merged.** This task documents/cleans up the *true* end state of Track A — doing it before the other fixes land means describing a state that isn't real yet.

## Context
Codex's review found specific leftover references to the old `kafka-demo`/article-mirror domain that survived the Track A rename:
- `inventory-service/src/main/resources/application.yml:19` — `group-id: article-demo-group-2`.
- `shared-model/pom.xml:18` — `<description>Shared model classes for kafka-demo monorepos</description>`.
- `order-ui/src/app/services/auth.service.ts:10` and `inventory-ui/src/app/services/auth.service.ts:10` — both use `private tokenKey = 'kafka-ui-token'`.
- `CLAUDE.md` — still documents the entire old `kafka-demo`/article topology; it was never updated because the original sprint-1 backlog deliberately deferred that to a later task, but Track A still isn't far enough along to write it confidently until this sprint closes it out.

These are confirmed by direct inspection, not just the review's word — but treat the list above as a starting point, not the full extent. Part of this task is finding what else is still there.

## Task

### 1. Full repo grep sweep
Run, from the repo root:
```
grep -rni "kafkademo\|kafka-demo\b\|article" --include="*.java" --include="*.yml" --include="*.yaml" --include="*.ts" --include="*.xml" --include="*.html" --include="*.json" . 2>/dev/null | grep -v "/target/\|/node_modules/\|/dist/\|/docs/\|/reviews/\|task001_"
```
Fix everything it finds that's a genuine leftover (not, e.g., a legitimate English use of a word that happens to match — review each hit). At minimum, this should catch and fix:
- `inventory-service/src/main/resources/application.yml`: `group-id: article-demo-group-2` → `inventory-service-group` (match the naming style already used by `order-service`'s consumer group).
- `shared-model/pom.xml`: description → something like `Shared contract library for the order/inventory demo (DTOs and Kafka events only)`.
- `order-ui/src/app/services/auth.service.ts` and `inventory-ui/src/app/services/auth.service.ts`: `tokenKey` → a neutral name, e.g. `'auth-token'`. (Each app runs on its own origin/port, so there's no collision risk either way — this is purely about not leaking the old "kafka-ui" branding into apps that no longer have that name.)
- Any `kafka-demo`/`kafkademo` artifact IDs, package fragments, or comments the sweep turns up that earlier tasks missed.

### 2. Rewrite `CLAUDE.md`
Once the grep sweep and F-1–F-4/F-6/F-7 are all merged, rewrite `CLAUDE.md` to describe the **actual, verified** architecture:
- Replace every reference to the old `kafka-demo`/`kafka-demo-2`/article-mirror topology with the real `order-service`/`inventory-service` choreographed-saga description (see `docs/adr/0001-event-driven-showcase-architecture.md` for the canonical description of the saga flow, and use it — don't redescribe the architecture from scratch).
- Update the "Common commands" section: module names (`order-service`, `inventory-service`, `order-ui`, `inventory-ui`), and add a note about the Maven Enforcer Java-version pin from F-7 if it's relevant to a "build fails immediately if you're on the wrong JDK" troubleshooting note.
- Don't invent new sections or restate things already covered by the ADR/backlog docs — `CLAUDE.md` should stay a concise orientation doc, not a copy of the ADR.

## Out of scope
- Don't start any Track B work in this task, even though it's the last task before Track B would normally begin — this task is cleanup/documentation only.

## Acceptance criteria
- The grep sweep command above returns no hits outside `docs/`, `reviews/`, and `task001_share_flows_own_entities.md` (that file is a historical planning doc and is allowed to keep referencing the old domain — it's not live code or live documentation).
- `CLAUDE.md` accurately describes the shipped `order-service`/`inventory-service`/`order-ui`/`inventory-ui` architecture with no remaining mention of "article" or `kafka-demo`.
- A fresh reader of `CLAUDE.md` with no other context could correctly state what each of the 5 runnable components does and how they connect.
