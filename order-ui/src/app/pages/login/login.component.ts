import { Component, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDividerModule } from '@angular/material/divider';
import { AuthService } from '../../services/auth.service';

@Component({
    selector: 'app-login',
    imports: [FormsModule, RouterModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatSnackBarModule, MatDividerModule],
    changeDetection: ChangeDetectionStrategy.Eager,
    template: `
    <div style="display:flex;justify-content:center;align-items:center;height:100vh;background:#f5f5f5">
      <mat-card style="width:400px">
        <mat-card-header>
          <mat-card-title>Kafka Demo - Login</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form (ngSubmit)="onLogin()">
            <mat-form-field class="full-width">
              <mat-label>Email</mat-label>
              <input matInput [(ngModel)]="email" name="email" type="email" required>
            </mat-form-field>
            <mat-form-field class="full-width">
              <mat-label>Password</mat-label>
              <input matInput [(ngModel)]="password" name="password" type="password" required>
            </mat-form-field>
            <button mat-raised-button color="primary" type="submit" class="full-width" [disabled]="loading">
              {{ loading ? 'Signing in...' : 'Sign In' }}
            </button>
          </form>

          <mat-divider style="margin:20px 0"></mat-divider>

          <button mat-stroked-button class="full-width" (click)="loginWithGoogle()">
            <span style="display:flex;align-items:center;justify-content:center;gap:8px">
              Login with Google
            </span>
          </button>

          <p style="text-align:center;margin-top:16px">
            Don't have an account? <a routerLink="/register">Register</a>
          </p>
        </mat-card-content>
      </mat-card>
    </div>
  `
})
export class LoginComponent {
  email = '';
  password = '';
  loading = false;

  constructor(private auth: AuthService, private router: Router, private snack: MatSnackBar) {}

  onLogin(): void {
    if (!this.email || !this.password) return;
    this.loading = true;
    this.auth.login(this.email, this.password).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: err => {
        this.loading = false;
        this.snack.open(err.error?.error || 'Login failed', 'Close', { duration: 4000 });
      }
    });
  }

  loginWithGoogle(): void {
    window.location.href = '/oauth2/authorization/google';
  }
}
