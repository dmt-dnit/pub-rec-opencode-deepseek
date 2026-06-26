import { Injectable, OnDestroy } from '@angular/core';
import { Client, Message } from '@stomp/stompjs';
import { Observable, Subject } from 'rxjs';
import { InventoryReservation } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {
  private client: Client;
  private messageSubject = new Subject<InventoryReservation>();
  messages$: Observable<InventoryReservation> = this.messageSubject.asObservable();

  constructor() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    this.client = new Client({
      brokerURL: `${protocol}//${window.location.host}/ws`,
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        this.client.subscribe('/topic/messages', (msg: Message) => {
          const event: InventoryReservation = JSON.parse(msg.body);
          this.messageSubject.next(event);
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
