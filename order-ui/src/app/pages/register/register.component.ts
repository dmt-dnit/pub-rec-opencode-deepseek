import { CommonModule } from '@angular/common';
import { Component, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AuthService } from '../../services/auth.service';

@Component({
    selector: 'app-register',
    imports: [CommonModule, FormsModule, RouterModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatSnackBarModule],
    changeDetection: ChangeDetectionStrategy.Eager,
    template: `
    <div style="display:flex;justify-content:center;align-items:center;height:100vh;background:#f5f5f5">
      <mat-card style="width:400px">
        <mat-card-header>
          <mat-card-title>Register</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form (ngSubmit)="onRegister()">
            <mat-form-field class="full-width">
              <mat-label>Name</mat-label>
              <input matInput [(ngModel)]="name" name="name" required>
            </mat-form-field>
            <mat-form-field class="full-width">
              <mat-label>Email</mat-label>
              <input matInput [(ngModel)]="email" name="email" type="email" required>
            </mat-form-field>
            <mat-form-field class="full-width">
              <mat-label>Password</mat-label>
              <input matInput [(ngModel)]="password" name="password" type="password" required>
            </mat-form-field>
            <button mat-raised-button color="primary" type="submit" class="full-width" [disabled]="loading">
              {{ loading ? 'Registering...' : 'Register' }}
            </button>
          </form>
          <p style="text-align:center;margin-top:16px;color:#666" *ngIf="registered">
            Registration submitted. Awaiting admin approval.
          </p>
          <p style="text-align:center;margin-top:16px">
            Already have an account? <a routerLink="/login">Login</a>
          </p>
        </mat-card-content>
      </mat-card>
    </div>
  `
})
export class RegisterComponent {
  name = '';
  email = '';
  password = '';
  loading = false;
  registered = false;

  constructor(private auth: AuthService, private snack: MatSnackBar) {}

  onRegister(): void {
    if (!this.name || !this.email || !this.password) return;
    this.loading = true;
    this.auth.register(this.email, this.password, this.name).subscribe({
      next: () => {
        this.loading = false;
        this.registered = true;
        this.snack.open('Registration submitted! Awaiting admin approval.', 'Close', { duration: 5000 });
      },
      error: err => {
        this.loading = false;
        this.snack.open(err.error?.error || 'Registration failed', 'Close', { duration: 4000 });
      }
    });
  }
}
