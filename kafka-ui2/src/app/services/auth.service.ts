import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { LoginResponse, UserInfo } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private apiBase = '/api/auth';
  private tokenKey = 'kafka-ui-token';
  private userSubject = new BehaviorSubject<UserInfo | null>(null);
  user$ = this.userSubject.asObservable();

  constructor(private http: HttpClient, private router: Router) {
    const token = localStorage.getItem(this.tokenKey);
    if (token) {
      this.fetchMe();
    }
  }

  login(email: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.apiBase}/login`, { email, password })
      .pipe(tap(res => this.setSession(res)));
  }

  register(email: string, password: string, name: string): Observable<any> {
    return this.http.post(`${this.apiBase}/register`, { email, password, name });
  }

  fetchMe(): void {
    this.http.get<UserInfo>(`${this.apiBase}/me`).subscribe({
      next: user => this.userSubject.next(user),
      error: () => this.logout()
    });
  }

  setSession(res: LoginResponse): void {
    localStorage.setItem(this.tokenKey, res.token);
    this.userSubject.next({ email: res.email, name: res.name, role: res.role });
  }

  getToken(): string | null {
    return localStorage.getItem(this.tokenKey);
  }

  isLoggedIn(): boolean {
    return !!this.getToken();
  }

  logout(): void {
    localStorage.removeItem(this.tokenKey);
    this.userSubject.next(null);
    this.router.navigate(['/login']);
  }
}
