import { Component, OnInit, OnDestroy, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AuthService } from '../../services/auth.service';
import { OrderService } from '../../services/order.service';
import { WebSocketService } from '../../services/websocket.service';
import { Order, OrderLineItemRequest, UserInfo } from '../../models/user.model';
import { Subscription } from 'rxjs';

@Component({
    selector: 'app-dashboard',
    changeDetection: ChangeDetectionStrategy.Eager,
    imports: [CommonModule, FormsModule, MatToolbarModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, MatSnackBarModule],
    template: `
    <mat-toolbar color="primary">
      <span>Order UI</span>
      <span style="flex:1"></span>
      <span *ngIf="user" style="margin-right:16px">{{ user.email }}</span>
      <button mat-button (click)="logout()">Logout</button>
    </mat-toolbar>

    <div style="max-width:800px;margin:0 auto;padding:16px">
      <mat-card>
        <mat-card-header>
          <mat-card-title>Place Order</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form (ngSubmit)="placeOrder()">
            <p style="color:#666;margin:0 0 16px">Placing order as: {{ user?.email }}</p>

            <div class="sku-row">
              <span class="sku-label">SKU-001 (Widget)</span>
              <div class="sku-qty">
                <button type="button" mat-icon-button (click)="decrement('sku001')"><mat-icon>remove</mat-icon></button>
                <input type="number" matInput [(ngModel)]="sku001Qty" name="sku001Qty" min="0" style="width:60px;text-align:center">
                <button type="button" mat-icon-button (click)="increment('sku001')"><mat-icon>add</mat-icon></button>
              </div>
            </div>

            <div class="sku-row">
              <span class="sku-label">SKU-002 (Gadget)</span>
              <div class="sku-qty">
                <button type="button" mat-icon-button (click)="decrement('sku002')"><mat-icon>remove</mat-icon></button>
                <input type="number" matInput [(ngModel)]="sku002Qty" name="sku002Qty" min="0" style="width:60px;text-align:center">
                <button type="button" mat-icon-button (click)="increment('sku002')"><mat-icon>add</mat-icon></button>
              </div>
            </div>

            <div class="sku-row">
              <span class="sku-label">SKU-003 (Gizmo)</span>
              <div class="sku-qty">
                <button type="button" mat-icon-button (click)="decrement('sku003')"><mat-icon>remove</mat-icon></button>
                <input type="number" matInput [(ngModel)]="sku003Qty" name="sku003Qty" min="0" style="width:60px;text-align:center">
                <button type="button" mat-icon-button (click)="increment('sku003')"><mat-icon>add</mat-icon></button>
              </div>
            </div>

            <button mat-raised-button color="accent" type="submit" [disabled]="placing">
              {{ placing ? 'Placing...' : 'Place Order' }}
            </button>
          </form>
        </mat-card-content>
      </mat-card>

      <mat-card style="margin-top:16px">
        <mat-card-header>
          <mat-card-title>Orders</mat-card-title>
          <span [style.color]="connected ? '#4caf50' : '#f44336'" style="margin-left:8px;font-size:14px">{{ connected ? 'Connected' : 'Disconnected' }}</span>
        </mat-card-header>
        <mat-card-content>
          <mat-card *ngFor="let order of orders" style="margin-bottom:12px" data-testid="order-card">
            <mat-card-content>
              <div class="order-header">
                <strong>{{ order.orderId.substring(0, 8) }}</strong>
                <span [class]="'badge badge-' + order.status.toLowerCase()">{{ order.status }}</span>
              </div>
              <div class="order-body">
                <div>{{ order.customerEmail }}</div>
                <div>{{ order.totalAmount | currency }}</div>
                <div style="font-size:12px;color:#999">{{ order.placedAt | date:'medium' }}</div>
              </div>
              <div *ngIf="order.items && order.items.length" class="order-items">
                <div *ngFor="let item of order.items" class="order-item">
                  {{ item.sku }} &times; {{ item.quantity }}
                </div>
              </div>
            </mat-card-content>
          </mat-card>
          <p *ngIf="orders.length === 0" style="text-align:center;color:#999;padding:32px">No orders yet. Place an order to see it here.</p>
        </mat-card-content>
      </mat-card>
    </div>
  `,
    styles: [`
    .full-width { width: 100%; }
    .sku-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
    .sku-label { font-weight: 500; }
    .sku-qty { display: flex; align-items: center; gap: 4px; }
    .order-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
    .order-body { display: flex; gap: 16px; align-items: baseline; margin-bottom: 4px; }
    .order-items { margin-top: 8px; border-top: 1px solid #eee; padding-top: 8px; }
    .order-item { font-size: 13px; color: #666; }
    .badge { padding: 2px 8px; border-radius: 12px; font-size: 12px; font-weight: 600; color: #fff; }
    .badge-pending { background-color: #9e9e9e; }
    .badge-confirmed { background-color: #4caf50; }
    .badge-rejected { background-color: #f44336; }
    button[type="submit"] { margin-top: 12px; }
  `]
})
export class DashboardComponent implements OnInit, OnDestroy {
  user: UserInfo | null = null;
  sku001Qty = 0;
  sku002Qty = 0;
  sku003Qty = 0;
  placing = false;
  orders: Order[] = [];
  connected = false;
  private userSub!: Subscription;
  private msgSub!: Subscription;

  constructor(
    public auth: AuthService,
    private orderService: OrderService,
    private ws: WebSocketService,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.userSub = this.auth.user$.subscribe(u => {
      this.user = u;
    });

    this.orderService.listOrders().subscribe({
      next: orders => this.orders = orders.reverse(),
      error: () => {}
    });

    this.ws.connect();
    this.msgSub = this.ws.messages$.subscribe(order => {
      this.connected = true;
      const idx = this.orders.findIndex(o => o.orderId === order.orderId);
      if (idx >= 0) {
        this.orders = [...this.orders.slice(0, idx), order, ...this.orders.slice(idx + 1)];
      } else {
        this.orders = [order, ...this.orders];
      }
    });
  }

  increment(sku: string): void {
    if (sku === 'sku001') this.sku001Qty++;
    else if (sku === 'sku002') this.sku002Qty++;
    else if (sku === 'sku003') this.sku003Qty++;
  }

  decrement(sku: string): void {
    if (sku === 'sku001' && this.sku001Qty > 0) this.sku001Qty--;
    else if (sku === 'sku002' && this.sku002Qty > 0) this.sku002Qty--;
    else if (sku === 'sku003' && this.sku003Qty > 0) this.sku003Qty--;
  }

  placeOrder(): void {
    const items: OrderLineItemRequest[] = [];
    if (this.sku001Qty > 0) items.push({ sku: 'SKU-001', quantity: this.sku001Qty });
    if (this.sku002Qty > 0) items.push({ sku: 'SKU-002', quantity: this.sku002Qty });
    if (this.sku003Qty > 0) items.push({ sku: 'SKU-003', quantity: this.sku003Qty });

    if (items.length === 0) {
      this.snack.open('Add at least one item to the order', 'Close', { duration: 4000 });
      return;
    }

    this.placing = true;
    this.orderService.placeOrder(items).subscribe({
      next: order => {
        this.placing = false;
        this.snack.open(`Order ${order.orderId.substring(0, 8)} placed — total: $${order.totalAmount.toFixed(2)}`, 'Close', { duration: 5000 });
        this.sku001Qty = 0;
        this.sku002Qty = 0;
        this.sku003Qty = 0;
        this.orders = [order, ...this.orders];
      },
      error: err => {
        this.placing = false;
        this.snack.open(err.error?.error || 'Order failed', 'Close', { duration: 4000 });
      }
    });
  }

  logout(): void {
    this.auth.logout();
  }

  ngOnDestroy(): void {
    this.userSub?.unsubscribe();
    this.msgSub?.unsubscribe();
    this.ws.disconnect();
  }
}
