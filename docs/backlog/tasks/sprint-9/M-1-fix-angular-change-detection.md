# M-1 — Fix Angular change detection: provideZoneChangeDetection + NgZone + array mutation

**Sprint:** 9  
**Priority:** Critical (blocks smoke test pass)

## Background

Codex's Sprint 8 review confirmed the full backend/Kafka/STOMP chain works correctly:
- `POST /api/orders` returns 200
- STOMP `/topic/messages` delivers the order event
- `GET /api/inventory` shows decremented quantity

Yet both Angular dashboards stayed completely stale. Root cause (read from `node_modules/@angular/core/fesm2022/_browser-chunk.mjs`):

**Angular 22 removed the automatic zone.js integration from `bootstrapApplication`.** You must now explicitly call `provideZoneChangeDetection()` in `app.config.ts`. Without it, HTTP responses and other async callbacks complete but Angular's scheduler never runs a CD cycle, so property assignments like `this.orders = [...]`, `this.placing = false`, and `this.products = products` are set in memory but the template never re-renders.

Additionally, `@stomp/stompjs` fires its WebSocket callbacks outside zone.js entirely. Even after adding `provideZoneChangeDetection()`, STOMP-triggered mutations won't trigger CD unless explicitly wrapped in `NgZone.run()`.

## Files to change (exact paths)

### 1. `order-ui/src/app/app.config.ts` — add zone provider

Current:
```typescript
import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors, withXhr } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { routes } from './app.routes';
import { authInterceptor } from './interceptors/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(withXhr(), withInterceptors([authInterceptor])),
    provideAnimations()
  ]
};
```

Required change: add `provideZoneChangeDetection({ eventCoalescing: true })` to the providers array and import it.

```typescript
import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors, withXhr } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { routes } from './app.routes';
import { authInterceptor } from './interceptors/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withXhr(), withInterceptors([authInterceptor])),
    provideAnimations()
  ]
};
```

### 2. `inventory-ui/src/app/app.config.ts` — add zone provider

Same change. Current file does not have `withXhr()` either — add it for consistency (Angular 22 defaults to `fetch` which has subtle zone-patching differences from XHR; XHR is always reliably patched by zone.js):

```typescript
import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors, withXhr } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { routes } from './app.routes';
import { authInterceptor } from './interceptors/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withXhr(), withInterceptors([authInterceptor])),
    provideAnimations()
  ]
};
```

### 3. `order-ui/src/app/services/websocket.service.ts` — NgZone wrapping

Import `NgZone` from `@angular/core`, inject it, and wrap the STOMP `onConnect` callback in `this.zone.run(...)`:

```typescript
import { Injectable, NgZone, OnDestroy } from '@angular/core';
import { Client, Message } from '@stomp/stompjs';
import { Observable, Subject } from 'rxjs';
import { Order } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {
  private client: Client;
  private messageSubject = new Subject<Order>();
  messages$: Observable<Order> = this.messageSubject.asObservable();

  constructor(private zone: NgZone) {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    this.client = new Client({
      brokerURL: `${protocol}//${window.location.host}/ws`,
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        this.client.subscribe('/topic/messages', (msg: Message) => {
          const order: Order = JSON.parse(msg.body);
          this.zone.run(() => this.messageSubject.next(order));
        });
      }
    });
  }

  connect(): void {
    if (!this.client.active) {
      this.client.activate();
    }
  }

  disconnect(): void {
    if (this.client.active) {
      this.client.deactivate();
    }
  }

  ngOnDestroy(): void {
    this.disconnect();
  }
}
```

### 4. `inventory-ui/src/app/services/websocket.service.ts` — NgZone wrapping (same pattern)

```typescript
import { Injectable, NgZone, OnDestroy } from '@angular/core';
import { Client, Message } from '@stomp/stompjs';
import { Observable, Subject } from 'rxjs';
import { InventoryReservation } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {
  private client: Client;
  private messageSubject = new Subject<InventoryReservation>();
  messages$: Observable<InventoryReservation> = this.messageSubject.asObservable();

  constructor(private zone: NgZone) {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    this.client = new Client({
      brokerURL: `${protocol}//${window.location.host}/ws`,
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        this.client.subscribe('/topic/messages', (msg: Message) => {
          const event: InventoryReservation = JSON.parse(msg.body);
          this.zone.run(() => this.messageSubject.next(event));
        });
      }
    });
  }

  connect(): void {
    if (!this.client.active) {
      this.client.activate();
    }
  }

  disconnect(): void {
    if (this.client.active) {
      this.client.deactivate();
    }
  }

  ngOnDestroy(): void {
    this.disconnect();
  }
}
```

### 5. `inventory-ui/src/app/pages/dashboard/dashboard.component.ts` — fix in-place array mutation

Line 109: `this.reservations.unshift(reservation);`

Change to an immutable prepend so Angular's binding detects the new reference:
```typescript
this.reservations = [reservation, ...this.reservations];
```

### 6. `order-ui/src/app/pages/dashboard/dashboard.component.ts` — clean up CD strategy

Line 100: `changeDetection: ChangeDetectionStrategy.Eager,`

`Eager` is a valid Angular 22 value (= 1 = Default/CheckAlways), but it's confusing. Remove the `changeDetection` line entirely (Default is the default) and remove the `ChangeDetectionStrategy` import if it becomes unused. This is a code hygiene fix, not a functional one.

Also suppress the `@stomp/stompjs` CommonJS warning by adding to `angular.json` under `build.options`:
```json
"allowedCommonJsDependencies": ["@stomp/stompjs"]
```
Add the same to `inventory-ui/angular.json`.

## Acceptance criteria

1. Run `npm run build` in `order-ui` — exits 0. Run in `inventory-ui` — exits 0. Show the actual terminal output.
2. Start both dev servers (`npm start` in each UI) and both Java services + Kafka.
3. Navigate to `http://localhost:4200`. Log in as `customer1@example.test / customer123`. The order list loads immediately showing any existing orders (no page reload needed).
4. Place an order with SKU-001 quantity 1. The button returns from "Placing..." to "Place Order" without a reload. The new order appears in the order list. If the STOMP saga completes, the order status updates from PENDING to CONFIRMED without a reload.
5. Navigate to `http://localhost:4201`. Log in as `warehouse1@example.test / warehouse123`. The stock table shows SKU-001, SKU-002, SKU-003 immediately (no reload).
6. After the order from step 4 is placed, the reservation feed shows a RESERVED event for that order without a page reload. The SKU-001 quantity in the stock table decrements.
7. Show `npm run build` output for both UIs with no errors.

**Do not report "Pass" based on the build succeeding alone. Steps 3–6 require actual browser observation. If you cannot run a browser environment, say so explicitly.**
