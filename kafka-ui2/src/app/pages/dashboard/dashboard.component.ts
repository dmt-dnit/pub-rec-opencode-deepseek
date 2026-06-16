import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AuthService } from '../../services/auth.service';
import { ArticleService } from '../../services/article.service';
import { WebSocketService } from '../../services/websocket.service';
import { ArticleEvent, UserInfo } from '../../models/user.model';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, MatToolbarModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatListModule, MatIconModule, MatSnackBarModule],
  template: `
    <mat-toolbar color="primary">
      <span>Kafka Demo 2 UI</span>
      <span style="flex:1"></span>
      <span *ngIf="user" style="margin-right:16px">{{ user.email }}</span>
      <button mat-button (click)="logout()">Logout</button>
    </mat-toolbar>

    <div style="max-width:800px;margin:0 auto;padding:16px">
      <mat-card>
        <mat-card-header>
          <mat-card-title>Publish Article</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form (ngSubmit)="publish()">
            <mat-form-field class="full-width">
              <mat-label>Title</mat-label>
              <input matInput [(ngModel)]="title" name="title" required>
            </mat-form-field>
            <mat-form-field class="full-width">
              <mat-label>Author</mat-label>
              <input matInput [(ngModel)]="author" name="author" required>
            </mat-form-field>
            <button mat-raised-button color="accent" type="submit" [disabled]="publishing">
              {{ publishing ? 'Publishing...' : 'Publish' }}
            </button>
          </form>
        </mat-card-content>
      </mat-card>

      <mat-card>
        <mat-card-header>
          <mat-card-title>Live Messages (WebSocket)</mat-card-title>
          <span style="color:#4caf50;margin-left:8px;font-size:14px">{{ connected ? 'Connected' : 'Disconnected' }}</span>
        </mat-card-header>
        <mat-card-content>
          <mat-list *ngIf="messages.length > 0; else emptyFeed">
            <mat-list-item *ngFor="let msg of messages">
              <mat-icon matListItemIcon style="color:#ff9800">article</mat-icon>
              <span matListItemTitle>{{ msg.title }}</span>
              <span matListItemLine>by {{ msg.author }} &mdash; {{ msg.id }}</span>
            </mat-list-item>
          </mat-list>
          <ng-template #emptyFeed>
            <p style="text-align:center;color:#999;padding:32px">No messages yet. Publish from any service to see them here.</p>
          </ng-template>
        </mat-card-content>
      </mat-card>
    </div>
  `
})
export class DashboardComponent implements OnInit, OnDestroy {
  user: UserInfo | null = null;
  title = '';
  author = '';
  publishing = false;
  messages: ArticleEvent[] = [];
  connected = false;
  private userSub!: Subscription;
  private msgSub!: Subscription;

  constructor(
    public auth: AuthService,
    private articleService: ArticleService,
    private ws: WebSocketService,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.userSub = this.auth.user$.subscribe(u => this.user = u);
    this.ws.connect();
    this.msgSub = this.ws.messages$.subscribe(msg => {
      this.messages.unshift(msg);
      this.connected = true;
    });
  }

  publish(): void {
    if (!this.title || !this.author) return;
    this.publishing = true;
    this.articleService.publish({
      title: this.title,
      author: this.author,
      publishedAt: new Date().toISOString()
    }).subscribe({
      next: event => {
        this.publishing = false;
        this.snack.open(`Published: ${event.title}`, 'Close', { duration: 3000 });
        this.title = '';
        this.author = '';
      },
      error: err => {
        this.publishing = false;
        this.snack.open(err.error?.error || 'Publish failed', 'Close', { duration: 4000 });
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
