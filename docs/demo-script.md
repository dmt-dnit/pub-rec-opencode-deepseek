# Live Demo Script

## Goal

Show the final Track A flow in a real browser, then connect that demo to the review loop that made it trustworthy: customer login, order placement, Kafka saga, live order confirmation, inventory reservation feed, and stock decrement.

## Prerequisites

- Java 21
- Docker Desktop or Podman
- Node.js 20+
- Dependencies installed for `order-ui`, `inventory-ui`, and `e2e`

Seeded accounts:

- Customer: `customer1@example.test` / `customer123`
- Warehouse staff: `warehouse1@example.test` / `warehouse123`

## Startup

Start Kafka:

```powershell
cd C:\projects\pub-rec-opencode-deepseek\order-service
docker compose up -d
```

Install shared contracts once, then start the Java services in separate terminals:

```powershell
cd C:\projects\pub-rec-opencode-deepseek\shared-model
.\mvnw.cmd clean install
```

```powershell
cd C:\projects\pub-rec-opencode-deepseek\auth-server
.\mvnw.cmd spring-boot:run
```

```powershell
cd C:\projects\pub-rec-opencode-deepseek\order-service
.\mvnw.cmd spring-boot:run
```

```powershell
cd C:\projects\pub-rec-opencode-deepseek\inventory-service
.\mvnw.cmd spring-boot:run
```

Start both Angular UIs in separate terminals:

```powershell
cd C:\projects\pub-rec-opencode-deepseek\order-ui
npm.cmd install
npm.cmd start
```

```powershell
cd C:\projects\pub-rec-opencode-deepseek\inventory-ui
npm.cmd install
npm.cmd start
```

Quick health checks:

```powershell
Invoke-WebRequest http://localhost:9000/oauth2/jwks
Invoke-WebRequest http://localhost:8080/actuator/health
Invoke-WebRequest http://localhost:8081/actuator/health
Invoke-WebRequest http://localhost:4200
Invoke-WebRequest http://localhost:4201
```

## Option A

Run the headed Playwright smoke:

```powershell
cd C:\projects\pub-rec-opencode-deepseek\e2e
npm.cmd install
npx.cmd playwright install chromium
npx.cmd playwright test --headed
```

Narration:

1. The customer browser logs into `order-ui`.
2. The warehouse browser logs into `inventory-ui`.
3. The test proves seeded inventory is visible before any new action.
4. The customer places one `SKU-001` order.
5. The order card moves to `CONFIRMED`.
6. The inventory feed shows `RESERVED` and the quantity drops.

What to say: the important part is not that the app works once. The important part is that this exact browser path is the gate that kept rejecting plausible fixes until the observable behavior was right.

## Option B

Manual walkthrough:

1. Open `http://localhost:4200`.
2. Log in as `customer1@example.test`.
3. Open `http://localhost:4201` in another browser or profile.
4. Log in as `warehouse1@example.test`.
5. Point out `SKU-001`, `SKU-002`, `SKU-003` and the current inventory quantities.
6. In `order-ui`, add quantity `1` for `SKU-001`.
7. Click `Place Order`.
8. Watch the newest order card become `CONFIRMED`.
9. Return to `inventory-ui` and show the `RESERVED` feed item plus decremented quantity.

What to narrate:

- The system is choreographed, not orchestrated. Order emits, inventory reacts, order consumes the reply.
- Both UIs are live STOMP/WebSocket consumers, so visible updates are part of the distributed contract.
- The demo is intentionally run against a non-empty DB because that is the state that broke earlier Playwright selectors.

## Track B Epilogue

Use this only if there is time after the Track A walkthrough.

- Sprint 14: local checks were green, but review caught a Linux CI metadata defect in the Unix Maven wrapper bits.
- Sprint 15: CI was green, but the live container smoke failed because both resource services hardcoded `localhost` for JWKS; the same smoke later proved the fix.
- Sprint 16: Playwright and OpenAPI were not enough; the security scanner itself had to be proven in live CI, which ended with Snyk SCA.
- Sprint 17: "exactly-once" still needs observable-boundary evidence. The reusable code-review anchors are:
  `order-service/src/test/java/com/example/orderservice/OrderServiceKafkaContainerTest.java`
  `inventory-service/src/main/java/com/example/inventoryservice/service/OutboxRelay.java`
  `inventory-service/src/main/java/com/example/inventoryservice/receiver/OrderEventListener.java`
  `inventory-ui/src/app/pages/dashboard/dashboard.component.ts`

## Stage Fallback

Use captured assets instead of inventing historical screenshots:

- `docs/demo-notes-sprint-14.md`
- `docs/demo-notes-sprint-15.md`
- `docs/demo-notes-sprint-16.md`
- `docs/demo-notes-sprint-17.md`
- `docs/assets/sprint17-observable-boundary-catches.png`

## Timing

5-minute slot:

1. 0:00-0:45 — setup and architecture premise
2. 0:45-2:30 — headed Playwright or fast manual flow
3. 2:30-3:45 — explain the Kafka saga and live update path
4. 3:45-5:00 — show one failure story from Track A and one from Track B

10-minute slot:

1. 0:00-1:30 — why typical AI demos miss the hard part
2. 1:30-3:30 — roles, handoffs, reviewer gate
3. 3:30-6:30 — live demo
4. 6:30-8:30 — Sprint 9/10 and Sprint 11-13 failures
5. 8:30-10:00 — Track B epilogue through Sprint 17

## Capture Note

The Sprint 17 note explicitly says no fresh Maven verify output or live duplicate-feed replay was available in that sandbox. Keep that limitation visible. Use the code paths and review framing as reusable evidence; do not substitute invented screenshots for missing runtime capture.
