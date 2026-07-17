# Sprint 30 — Handoff to Codex

**Coordinator:** Claude Code. **Date:** 2026-07-17.
**Task:** fix the admin-guard false-rejection Codex found in Sprint 28 (`reviews/sprint-28-track-c-review.md`, P1).
**Implementer:** opencode+DeepSeek, isolated worktree.
**Merge-gate tier:** coordinator-supervised, pushed direct to `main` per the Sprint 21 policy.
Review-Target-Commit: 3f092b9

## Why this sprint exists

Codex's Sprint 28 review (genuine `FRESH REJECT` per `scripts/verify-review.sh 28`) found that `order-ui/src/app/guards/admin.guard.ts` synchronously required `auth.currentUser?.role === 'ADMIN'` to pass, but `AuthService` only populates `currentUser` asynchronously via `fetchMe()`. On a hard refresh directly on `/admin` — an entirely ordinary way to reach a bookmarked admin page — the guard runs before that response arrives and bounces a legitimate admin to `/dashboard`. This was a **coordinator scoping error**: the original A-2 brief described this as an acceptable rare edge case; on reflection it's the default outcome for any direct navigation/refresh, not a race that rarely fires.

## What was done (`3f092b9`)

`order-ui/src/app/guards/admin.guard.ts` now matches the already-correct, already-reviewed permissive pattern already used in `auth.guard.ts`: only reject when the role is affirmatively known and wrong, let an unresolved (`null`) role through.
```ts
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
The stale comment describing the old accepted-risk framing was removed.

**Why this is safe, not just a guard tweak that hides the problem**: the guard was never the real security boundary — `GET /api/admin/users`/`PUT /api/admin/users/{id}/approve` both require `hasRole('ADMIN')` at the actual Spring Security layer (Codex's own Sprint 28 review confirmed this is correctly enforced). A non-admin who slipped through during an unresolved-role window would just get a 403 from the real API call and see the existing error state, no data leak. Separately: the JWT Bearer token the backend actually authenticates against is available synchronously from `localStorage` regardless of whether `/me` has resolved (`auth.interceptor.ts` attaches it unconditionally), so a genuine admin who passes through during the race window has their real API calls succeed immediately anyway — this fix doesn't just avoid a bad redirect, the page actually works correctly during the race window too.

## Coordinator verification

- Diff reviewed directly: single file, matches the brief exactly, no other files touched.
- `cd order-ui && npx ng build` (production config) — BUILD SUCCESS, both on the worktree and again independently on integrated `main`.
- `git status --short` clean on `main` after the cherry-pick.

## Explicitly out of scope (unchanged from the brief)

- No change to `inventory-ui` (no admin page exists there — Sprint 28's deliberate scope).
- No change to `auth.guard.ts` (already correct, used only as the reference pattern).
- No async "wait for auth state" loading spinner — the permissive-check fix is sufficient and matches the app's existing UX-only-guard philosophy.

## Loop note

Reviewer: re-check `admin.guard.ts` now matches `auth.guard.ts`'s permissive pattern exactly (null role passes through, only an affirmatively-known-wrong role rejects), and that no other file changed in this one-finding, one-fix round.
