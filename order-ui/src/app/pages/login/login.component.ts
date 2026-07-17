import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule, ActivatedRoute } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDividerModule } from '@angular/material/divider';
import { AuthService } from '../../services/auth.service';
import { environment } from '../../../environments/environment';

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

          @if (oauthMessage) {
            <p style="text-align:center;margin-top:16px;color:#f44336">{{ oauthMessage }}</p>
          }

          <p style="text-align:center;margin-top:16px">
            Don't have an account? <a routerLink="/register">Register</a>
          </p>
        </mat-card-content>
      </mat-card>
    </div>
  `
})
export class LoginComponent implements OnInit {
  email = '';
  password = '';
  loading = false;
  oauthMessage: string | null = null;

  constructor(private auth: AuthService, private router: Router, private route: ActivatedRoute, private snack: MatSnackBar) {}

  ngOnInit(): void {
    this.route.queryParamMap.subscribe(params => {
      const oauth2 = params.get('oauth2');
      if (!oauth2) return;

      if (oauth2 === 'success') {
        const token = params.get('token');
        if (token) {
          this.auth.loginWithToken(token);
          this.router.navigate(['/dashboard']);
        }
      } else if (oauth2 === 'pending') {
        this.oauthMessage = 'Your account is pending admin approval. Please wait for an administrator to activate your account.';
      } else if (oauth2 === 'error') {
        this.oauthMessage = 'Google sign-in failed. Please try again or use email/password login.';
      }
    });
  }

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
    window.location.href = `${environment.authApiBase}/oauth2/authorization/google`;
  }
}
