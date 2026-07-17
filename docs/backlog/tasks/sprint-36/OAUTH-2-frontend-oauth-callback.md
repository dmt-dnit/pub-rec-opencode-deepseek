# OAUTH-2 — frontend OAuth callback handling (`order-ui`) + remove Google button (`inventory-ui`)

**Sprint:** 36. **Track:** C — go-live, Phase 6. **Implementer:** opencode+DeepSeek (worktree).

## Why

Per Dimitri's decision (2026-07-17), Google OAuth is `order-ui` (customer) only —
`inventory-ui`'s "Login with Google" button should be removed entirely. And today
**neither UI has any code reading the OAuth callback's query params** — confirmed via
`grep -n "oauth2" order-ui/src/app/pages/login/login.component.ts`, which only shows
the button that *initiates* login (`loginWithGoogle()`), nothing handling the return
trip. This task builds the `order-ui` side of that and removes the dead-end button from
`inventory-ui`.

## The contract (matches OAUTH-1's backend redirect exactly — do not deviate)

After Google auth, `auth-server` redirects the browser to
`{frontend origin}/login` with exactly one of:
- `?oauth2=success&token=<jwt>` — log the user in with this token.
- `?oauth2=pending` — show an approval-pending message, no token.
- `?oauth2=error` — show a generic OAuth failure message, no token.

## Current state (read directly, re-verify if this has moved)

`order-ui/src/app/services/auth.service.ts` (post-Sprint-33, already exports
`AUTH_TOKEN_KEY` and has `fetchMe()` with the `401`/`403`-only logout behavior — reuse
both, don't reinvent):
```typescript
export const AUTH_TOKEN_KEY = 'auth-token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private tokenKey = AUTH_TOKEN_KEY;
  ...
  fetchMe(): void {
    this.http.get<UserInfo>(`${this.apiBase}/me`).subscribe({
      next: user => this.userSubject.next(user),
      error: (err: unknown) => { /* 401/403-only logout, see source */ }
    });
  }
  setSession(res: LoginResponse): void {
    localStorage.setItem(this.tokenKey, res.token);
    this.userSubject.next({ email: res.email, name: res.name, role: res.role });
  }
}
```

`order-ui/src/app/pages/login/login.component.ts` has `loginWithGoogle()` (initiates
login, unchanged by this task) but nothing reading query params on load.

`inventory-ui/src/app/pages/login/login.component.ts` and its template currently have
an identical Google login button + `loginWithGoogle()` method (from
`grep -n "loginWithGoogle" inventory-ui/src/app/pages/login/login.component.ts`).

## What to build

### `order-ui`

1. **New `AuthService` method** — `loginWithToken(token: string): void` — stores the
   token directly (`localStorage.setItem(this.tokenKey, token)`) and calls
   `this.fetchMe()` to populate `userSubject` from `/api/auth/me` (reuses the existing,
   already-hardened fetch path — don't duplicate that logic).
2. **`login.component.ts`**: on init, read the current route's query params
   (`ActivatedRoute`, inject it — check whether it's already injected anywhere in this
   component; if not, add it). Branch:
   - `oauth2=success` + `token` present → call `authService.loginWithToken(token)`,
     then navigate to `/dashboard`.
   - `oauth2=pending` → show a message (e.g. a `mat-error` or similar existing UI
     pattern in this component — match whatever style this component already uses for
     other messages/errors, don't invent a new one) explaining the account needs admin
     approval.
   - `oauth2=error` → show a generic "Google sign-in failed" message.
   - No `oauth2` param at all (normal page load) → no change, existing behavior.

### `inventory-ui`

1. Remove the "Login with Google" button from the template and the `loginWithGoogle()`
   method from `login.component.ts` — this UI no longer offers Google login at all,
   per Dimitri's decision. Leave email/password login untouched.

## Explicitly out of scope

- Do not touch `inventory-ui`'s email/password login path.
- Do not touch the `authInterceptor`/guards (Sprint 33's fixes) — this task only adds
  a new entry point into the existing `AuthService`, it doesn't change how the token is
  attached to requests or how routes are guarded.
- Do not build any UI for re-attempting Google login after a `pending`/`error` result
  beyond a clear message — no retry logic, no polling for approval status.

## Acceptance criteria (show real output, don't assert "Pass")

1. `ng build --configuration production` clean on both `order-ui` and `inventory-ui`.
2. Manual verification (can't test the real Google leg without real credentials, say so
   explicitly rather than claiming it): with `order-ui` running locally
   (`npm start`), navigate directly to
   `http://localhost:4200/login?oauth2=success&token=<any locally-issued JWT — e.g. log
   in normally first and copy the token from localStorage, or curl auth-server's
   /api/auth/login directly>` and confirm it logs in and reaches `/dashboard`. Then
   navigate to `?oauth2=pending` and `?oauth2=error` and confirm each shows its message
   without attempting to log in.
3. Confirm `inventory-ui`'s login page no longer shows a Google button at all
   (visual/DOM check, not just that the method was removed — check the template too).
4. Show the actual `git diff` of all changed files in both UIs.
5. `git status --short` clean after commit.
