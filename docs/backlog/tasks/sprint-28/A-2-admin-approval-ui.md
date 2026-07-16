# A-2 — order-ui: Admin approval page

**Sprint:** 28. **Scope:** `order-ui` only.

## Why

Approving a newly registered user currently requires the coordinator to run raw `curl`
against `GET /api/admin/users` + `PUT /api/admin/users/{id}/approve` — both endpoints
already work correctly (live-verified), there's just no UI for them. This task adds one.

## Current state (verified, re-check if it's moved)

- `order-ui/src/app/app.routes.ts` (full current content):
  ```ts
  import { Routes } from '@angular/router';
  import { authGuard } from './guards/auth.guard';

  export const routes: Routes = [
    { path: 'login', loadComponent: () => import('./pages/login/login.component').then(m => m.LoginComponent) },
    { path: 'register', loadComponent: () => import('./pages/register/register.component').then(m => m.RegisterComponent) },
    { path: 'dashboard', loadComponent: () => import('./pages/dashboard/dashboard.component').then(m => m.DashboardComponent), canActivate: [authGuard] },
    { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
    { path: '**', redirectTo: '/dashboard' }
  ];
  ```
- `order-ui/src/app/guards/auth.guard.ts` (full current content) — only checks login
  state, no role check:
  ```ts
  import { inject } from '@angular/core';
  import { Router } from '@angular/router';
  import { AuthService } from '../services/auth.service';

  export const authGuard = () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    return auth.isLoggedIn() ? true : router.parseUrl('/login');
  };
  ```
- `order-ui/src/app/models/user.model.ts` has `UserInfo { email: string; name: string;
  role: string; }` — `AuthService.user$` already emits this, `role` is already present
  (e.g. `'ADMIN'`, `'CUSTOMER'`, `'WAREHOUSE_STAFF'`) and used nowhere else in the UI yet.
- Existing components (`dashboard.component.ts`, `login.component.ts`,
  `register.component.ts`) are all standalone, inline template + inline `styles` array,
  Angular Material (`mat-card`, `mat-toolbar`, `mat-button` etc.), `*ngIf`/`*ngFor`
  (not the newer `@if`/`@for` control flow) — match this exact style, don't introduce a
  different one.
- `order-ui/src/app/services/auth.service.ts`'s `apiBase` is already
  `` `${environment.authApiBase}/api/auth` `` (Sprint 26) — the new admin service should
  use `` `${environment.authApiBase}/api/admin` `` the same way, since admin endpoints
  live on `auth-server` too.

## What to build

### 1. `admin.guard.ts` (new file, `order-ui/src/app/guards/`)

Checks the current user's role is `'ADMIN'`, redirects to `/dashboard` otherwise (not
`/login` — an unauthenticated user should already be caught by hitting `authGuard`
first if both guards are applied to the route).

`order-ui/src/app/services/auth.service.ts` currently exposes the current user only via
`user$` (a `BehaviorSubject<UserInfo | null>.asObservable()`) — the subject itself is
private, so add a small public accessor, e.g.:
```ts
get currentUser(): UserInfo | null {
  return this.userSubject.value;
}
```
Then `adminGuard` can check `auth.currentUser?.role === 'ADMIN'` synchronously.

**Known, acceptable edge case — don't try to fully solve it, just be aware:** on a hard
page refresh directly on `/admin`, `AuthService`'s constructor calls `fetchMe()`
asynchronously (see lines 15-20 of `auth.service.ts`) to repopulate `userSubject` from
the stored token — if the guard runs before that HTTP call resolves, `currentUser` will
still be `null` and a genuine admin gets redirected to `/dashboard` incorrectly. This is
a pre-existing pattern in this app (nothing else guards on role today, and `authGuard`
itself only checks token presence, not user data), so it's fine to leave as-is for this
sprint — worth a one-line comment in the guard, not worth building a "wait for the
fetch" mechanism that doesn't exist anywhere else in the app either.

### 2. `admin.service.ts` (new file, `order-ui/src/app/services/`)

```ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface AdminUser {
  id: number;
  email: string;
  name: string;
  role: string;
  status: string;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class AdminService {
  private apiBase = `${environment.authApiBase}/api/admin`;

  constructor(private http: HttpClient) {}

  listUsers(): Observable<AdminUser[]> {
    return this.http.get<AdminUser[]>(`${this.apiBase}/users`);
  }

  approveUser(id: number): Observable<any> {
    return this.http.put(`${this.apiBase}/users/${id}/approve`, {});
  }
}
```
(Match this shape to A-1's actual response fields once that task lands — if A-1 changed
the response shape at all beyond removing `password`, adjust `AdminUser` accordingly.)

### 3. `admin.component.ts` (new file, `order-ui/src/app/pages/admin/`)

- Lists all users from `listUsers()`, sorted so `PENDING` users appear first (or filter
  to show only `PENDING` with a toggle to show all — implementer's call, keep it
  simple).
- An "Approve" button per `PENDING` row, calling `approveUser(id)`; on success, update
  that row's status locally (no need to re-fetch the whole list) and show a snackbar,
  matching the existing success/error snackbar pattern used in
  `dashboard.component.ts`/`register.component.ts` (`err.error?.error || 'X failed'`).
- Match the existing visual style: `mat-toolbar` header, `mat-card` content area,
  inline styles, no new component library.

### 4. Wire it up

- Add to `app.routes.ts`: `{ path: 'admin', loadComponent: () => import('./pages/admin/admin.component').then(m => m.AdminComponent), canActivate: [authGuard, adminGuard] }`
- Add a link to `/admin` in `dashboard.component.ts`'s toolbar, **visible only when
  `user.role === 'ADMIN'`** (use `*ngIf`, matching the existing `*ngIf="user"` pattern
  already in that toolbar).

## Explicitly out of scope

- No role-promotion UI (see sprint overview) — approve only.
- No `inventory-ui` changes.
- No new unit tests — this UI currently has none (`angular.json` has no `test`
  architect target, confirmed in Sprint 26), consistent with the rest of the app.

## Acceptance criteria (show real output, don't assert "Pass")

1. `cd order-ui && npx ng build` (production config, default) — **BUILD SUCCESS**, real
   output shown.
2. Show the actual `git diff`/new-file contents for every file touched.
3. Confirm dev-mode behavior: `environment.ts` (dev) is unchanged, so
   `AdminService`'s `apiBase` still resolves to the same relative
   `/api/admin` path proxied by `ng serve` today — state this explicitly.
4. If you have a way to run `ng serve` and click through login (as
   `admin@example.test`/`admin123`, seeded) → dashboard → Admin link → see the pending
   users list → approve one, do that and report the real result. If you don't have a
   working local backend to test against, say so explicitly rather than assume it
   works from the build succeeding.
5. `git status --short` clean after commit.
