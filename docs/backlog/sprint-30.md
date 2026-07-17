# Sprint 30 — fix admin guard false-rejection on hard refresh

**Track:** C — go-live, post-Phase-4 follow-up. **Origin:** Codex Sprint 28 REJECT (`reviews/sprint-28-track-c-review.md`, P1).

## Why this sprint exists

Codex's Sprint 28 review found a genuine, confirmed defect: `order-ui/src/app/guards/admin.guard.ts` checks `auth.currentUser?.role === 'ADMIN'` synchronously, but `AuthService` only populates `currentUser` asynchronously (via `fetchMe()`, triggered from its constructor when a token exists in `localStorage`). On a hard refresh directly on `/admin` — a very plausible real usage pattern for a page an admin would bookmark and revisit — the guard runs before `fetchMe()`'s response arrives, sees `currentUser` as `null`, and incorrectly bounces a legitimate admin to `/dashboard`.

**Coordinator scoping error, not an implementer miss**: the original A-2 brief described this as a "known, acceptable edge case" not worth fixing. On reflection (and per Codex's review), that framing was wrong — this isn't a rare timing race, it's the *default* outcome for any direct navigation or refresh to `/admin` while a token already exists, which is an entirely ordinary way to reach that page.

## The fix, found by comparing against an already-correct sibling

`order-ui/src/app/guards/auth.guard.ts` has the *same* underlying race (checks `auth.currentUser?.role` after confirming a token exists) but doesn't have this bug, because its logic is permissive by default:
```ts
const role = auth.currentUser?.role;
if (role && role !== 'CUSTOMER' && role !== 'ADMIN') {
  auth.logout();
  return router.parseUrl('/login');
}
return true;
```
It only rejects when the role is *affirmatively known* and wrong — an unresolved (`null`) role falls through and is allowed. `admin.guard.ts` does the opposite: it requires an affirmative `=== 'ADMIN'` match to pass, so `null` (the unresolved state) is treated as a rejection.

## What to change

`order-ui/src/app/guards/admin.guard.ts` — replace the current strict check with the same permissive pattern already used (and already reviewed/accepted) in `auth.guard.ts`:
```ts
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const adminGuard = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const role = auth.currentUser?.role;
  if (role && role !== 'ADMIN') {
    return router.parseUrl('/dashboard');
  }
  return true;
};
```
Remove the old comment describing the accepted-risk framing — it no longer applies.

## Why this is safe, not just a guard tweak that hides the problem

The guard was never the actual security boundary — `GET /api/admin/users` and `PUT /api/admin/users/{id}/approve` both require `hasRole('ADMIN')` at the real Spring Security layer (Codex's own review confirmed this is correctly enforced). If a non-admin somehow reaches `/admin` during an unresolved-role window, their actual API calls still 403 at the backend, and `admin.component.ts`'s existing error handling (`this.error = err.error?.error || 'Failed to load users'`) already surfaces that as a visible error state — no data leaks, no privilege escalation. This permissive-guard pattern is the same one already reviewed and accepted for `auth.guard.ts` in this same sprint.

Separately: the actual JWT Bearer token (which the backend authenticates against) is available synchronously from `localStorage` via `getToken()` regardless of whether the `/me` profile fetch has resolved — `auth.interceptor.ts` attaches it to every outgoing request unconditionally. So in practice, a genuine admin who gets through the guard during the race window will have their `GET /api/admin/users` call succeed immediately (correct role claim already in the token from login), even before `currentUser` locally catches up — this fix doesn't just avoid a bad redirect, it results in the page actually working correctly during the race window too.

## Explicitly out of scope

- No change to `inventory-ui` — it has no admin page (Sprint 28's deliberate scope), so no equivalent guard exists there.
- No change to `auth.guard.ts` — it's already correct, used here only as the reference pattern.
- No "wait for auth state to resolve" loading spinner or async guard — the permissive-check fix is sufficient and matches the app's existing UX-only-guard philosophy; adding a loading state would be new complexity this bug doesn't need.

## Acceptance criteria (show real output, don't assert "Pass")

1. `git diff` of `admin.guard.ts` shown in the report.
2. `cd order-ui && npx ng build` (production config) — BUILD SUCCESS.
3. If you have a way to test in a browser: log in as an admin, navigate to `/admin`, hard-refresh the page, confirm you stay on `/admin` (not bounced to `/dashboard`). If you don't have a way to test this in a real browser, say so explicitly rather than assume it from the build succeeding.
4. `git status --short` clean after commit.

## Loop note

Reviewer: this is a one-finding, one-fix round — re-check that `admin.guard.ts` now matches `auth.guard.ts`'s permissive pattern exactly (null role passes through, only an affirmatively-known-wrong role rejects), and that no other file changed.
