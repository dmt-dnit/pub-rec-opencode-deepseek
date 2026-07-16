import { CommonModule } from '@angular/common';
import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { RouterModule } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AdminService, AdminUser } from '../../services/admin.service';

@Component({
    selector: 'app-admin',
    imports: [CommonModule, RouterModule, MatToolbarModule, MatCardModule, MatButtonModule, MatSnackBarModule],
    changeDetection: ChangeDetectionStrategy.Eager,
    template: `
    <mat-toolbar color="primary">
      <span>Admin — User Approval</span>
      <span style="flex:1"></span>
      <button mat-button routerLink="/dashboard">Back</button>
    </mat-toolbar>

    <div style="max-width:800px;margin:0 auto;padding:16px">
      <mat-card>
        <mat-card-header>
          <mat-card-title>Users ({{ users.length }})</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div *ngIf="loading" style="text-align:center;padding:32px;color:#666">Loading&hellip;</div>
          <div *ngIf="error" style="text-align:center;padding:32px;color:#f44336">{{ error }}</div>
          <mat-card *ngFor="let u of users" style="margin-bottom:8px">
            <mat-card-content>
              <div class="user-row">
                <div class="user-info">
                  <strong>{{ u.email }}</strong>
                  <span style="font-size:12px;color:#999">{{ u.name }} &middot; {{ u.role }} &middot; created {{ u.createdAt | date }}</span>
                </div>
                <div class="user-status">
                  <span [class]="'badge badge-' + u.status.toLowerCase()">{{ u.status }}</span>
                  <button *ngIf="u.status === 'PENDING'" mat-raised-button color="primary" (click)="approve(u)" [disabled]="u.id === approvingId" style="margin-left:8px">
                    {{ u.id === approvingId ? 'Approving…' : 'Approve' }}
                  </button>
                </div>
              </div>
            </mat-card-content>
          </mat-card>
          <p *ngIf="!loading && !error && users.length === 0" style="text-align:center;color:#999;padding:32px">No users found.</p>
        </mat-card-content>
      </mat-card>
    </div>
  `,
    styles: [`
    .user-row { display: flex; align-items: center; justify-content: space-between; }
    .user-info { display: flex; flex-direction: column; }
    .user-status { display: flex; align-items: center; }
    .badge { padding: 2px 8px; border-radius: 12px; font-size: 12px; font-weight: 600; color: #fff; }
    .badge-pending { background-color: #ff9800; }
    .badge-active { background-color: #4caf50; }
  `]
})
export class AdminComponent implements OnInit {
  users: AdminUser[] = [];
  loading = false;
  error?: string;
  approvingId?: number;

  constructor(
    private adminService: AdminService,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.loading = true;
    this.adminService.listUsers().subscribe({
      next: users => {
        this.loading = false;
        this.users = users.sort((a, b) => {
          if (a.status === 'PENDING' && b.status !== 'PENDING') return -1;
          if (a.status !== 'PENDING' && b.status === 'PENDING') return 1;
          return 0;
        });
      },
      error: err => {
        this.loading = false;
        this.error = err.error?.error || 'Failed to load users';
      }
    });
  }

  approve(user: AdminUser): void {
    this.approvingId = user.id;
    this.adminService.approveUser(user.id).subscribe({
      next: () => {
        this.approvingId = undefined;
        user.status = 'ACTIVE';
        this.users = this.users.sort((a, b) => {
          if (a.status === 'PENDING' && b.status !== 'PENDING') return -1;
          if (a.status !== 'PENDING' && b.status === 'PENDING') return 1;
          return 0;
        });
        this.snack.open(`User ${user.email} approved`, 'Close', { duration: 4000 });
      },
      error: err => {
        this.approvingId = undefined;
        this.snack.open(err.error?.error || 'Approval failed', 'Close', { duration: 4000 });
      }
    });
  }
}
