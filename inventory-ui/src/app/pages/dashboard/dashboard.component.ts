import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AuthService } from '../../services/auth.service';
import { InventoryService } from '../../services/inventory.service';
import { WebSocketService } from '../../services/websocket.service';
import { Product, InventoryReservation, UserInfo } from '../../models/user.model';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, MatToolbarModule, MatCardModule, MatButtonModule, MatListModule, MatIconModule, MatTableModule, MatChipsModule, MatSnackBarModule],
  template: `
    <mat-toolbar color="primary">
      <span>Inventory UI</span>
      <span style="flex:1"></span>
      <span *ngIf="user" style="margin-right:16px">{{ user.email }}</span>
      <button mat-button (click)="logout()">Logout</button>
    </mat-toolbar>

    <div style="max-width:900px;margin:0 auto;padding:16px">
      <mat-card>
        <mat-card-header>
          <mat-card-title>Stock Dashboard</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <table mat-table [dataSource]="products" class="full-width">

            <ng-container matColumnDef="sku">
              <th mat-header-cell *matHeaderCellDef>SKU</th>
              <td mat-cell *matCellDef="let product">{{ product.sku }}</td>
            </ng-container>

            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef>Name</th>
              <td mat-cell *matCellDef="let product">{{ product.name }}</td>
            </ng-container>

            <ng-container matColumnDef="quantityOnHand">
              <th mat-header-cell *matHeaderCellDef>Qty on Hand</th>
              <td mat-cell *matCellDef="let product">{{ product.quantityOnHand }}</td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns;"></tr>
          </table>
        </mat-card-content>
      </mat-card>

      <mat-card style="margin-top:16px">
        <mat-card-header>
          <mat-card-title>Reservation Feed</mat-card-title>
          <span [style.color]="connected ? '#4caf50' : '#f44336'" style="margin-left:8px;font-size:14px">{{ connected ? 'Connected' : 'Disconnected' }}</span>
        </mat-card-header>
        <mat-card-content>
          <mat-list *ngIf="reservations.length > 0; else emptyFeed">
            <mat-list-item *ngFor="let r of reservations">
              <mat-icon matListItemIcon [style.color]="r.status === 'RESERVED' ? '#4caf50' : '#f44336'">
                {{ r.status === 'RESERVED' ? 'check_circle' : 'cancel' }}
              </mat-icon>
              <span matListItemTitle>
                {{ r.orderId | slice:0:8 }}
                <mat-chip [style.background-color]="r.status === 'RESERVED' ? '#4caf50' : '#f44336'"
                          [style.color]="'#fff'" style="font-size:12px;min-height:20px;padding:0 8px">
                  {{ r.status }}
                </mat-chip>
              </span>
              <span matListItemLine *ngIf="r.reason">Reason: {{ r.reason }}</span>
              <span matListItemLine>{{ r.processedAt | date:'medium' }}</span>
            </mat-list-item>
          </mat-list>
          <ng-template #emptyFeed>
            <p style="text-align:center;color:#999;padding:32px">No reservation events yet.</p>
          </ng-template>
        </mat-card-content>
      </mat-card>
    </div>
  `
})
export class DashboardComponent implements OnInit, OnDestroy {
  user: UserInfo | null = null;
  products: Product[] = [];
  reservations: InventoryReservation[] = [];
  connected = false;
  columns = ['sku', 'name', 'quantityOnHand'];
  private userSub!: Subscription;
  private msgSub!: Subscription;

  constructor(
    public auth: AuthService,
    private inventoryService: InventoryService,
    private ws: WebSocketService,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.userSub = this.auth.user$.subscribe(u => this.user = u);
    this.loadProducts();
    this.ws.connect();
    this.msgSub = this.ws.messages$.subscribe(reservation => {
      this.reservations.unshift(reservation);
      this.connected = true;
      this.loadProducts();
    });
  }

  private loadProducts(): void {
    this.inventoryService.listProducts().subscribe({
      next: products => this.products = products,
      error: err => this.snack.open('Failed to load inventory', 'Close', { duration: 4000 })
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
