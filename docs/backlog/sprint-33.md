# Sprint 33 — fix NG0200 circular-DI auth-token loss on refresh

**Track:** C — go-live, closes `docs/backlog/investigation-localstorage-token-loss.md`.
**Date:** 2026-07-17.

## Why this sprint exists, and why it's retroactive

This wasn't scoped in advance through the normal cadence — it's the direct fix for the
real, reproducible bug tracked in the investigation doc, and it was written **coordinator-
direct**, not by opencode+DeepSeek or a Claude/Codex worktree agent. That's a deviation
from this repo's own "who implements what" rule (`CLAUDE.md`): coordinator-direct is
meant for trivially obvious, non-logic changes only, and this is a logic fix (breaking a
circular DI cycle, changing `fetchMe()`'s error-handling behavior). It happened this way
because the fix flowed directly out of a live browser-debugging session the moment the
real stack trace was captured, rather than being scoped as a separate task brief first.

This sprint doc exists to give it the same paper trail as anything else that reaches
Codex, and to flag the process deviation rather than let it pass silently.

## What was wrong

Confirmed via a captured DevTools stack trace (not inferred): on a cold page load,
`AuthService`'s constructor synchronously calls `fetchMe()` (a token is present in
`localStorage`), which calls `HttpClient.get()`. Subscribing to that runs the functional
interceptor chain synchronously, and `authInterceptor`'s `inject(AuthService)` re-enters
`AuthService` while it's still under construction — Angular throws `NG0200` (circular
dependency). That error lands in `fetchMe()`'s `subscribe({ error: () => this.logout() })`
— a *handled* RxJS error, so nothing was ever logged to console despite firing on every
single reload — and `logout()` unconditionally wipes the token. This only manifests on a
cold boot (empty DI container); a normal in-session `fetchMe()` call never re-triggers
construction, which is why the bug never showed up mid-session, only on F5.

## What changed (commit `380792f`)

- `order-ui` and `inventory-ui` `auth.interceptor.ts`: no longer injects `AuthService` —
  reads the token directly from `localStorage` via a new exported `AUTH_TOKEN_KEY`
  constant in `auth.service.ts`. Removes the cycle regardless of construction order.
- `auth.service.ts` (both apps): `fetchMe()`'s error handler now only calls `logout()` on
  a genuine `401`/`403` `HttpErrorResponse`; any other error is `console.error`'d instead
  of silently destroying a potentially-valid session.
- `auth.guard.ts` (both apps) and `admin.guard.ts` (`order-ui` only): now `console.warn`
  the specific reason for every redirect they trigger.

## Explicitly out of scope

- No change to token issuance, expiry, or backend auth logic — frontend-only.
- No change to the guards' actual pass/fail logic, only added logging.

## Acceptance criteria (show real output, don't assert "Pass")

1. `ng build --configuration production` clean on both `order-ui` and `inventory-ui`.
2. Show the actual diff of all 7 changed files.
3. Confirm the NG0200-cycle explanation is architecturally sound: does
   `inject(AuthService)` inside a functional interceptor, called synchronously from
   within `AuthService`'s own constructor via `HttpClient.get(...).subscribe(...)`,
   genuinely trigger Angular's circular-dependency detection? (Reviewer: this is the
   one thing worth independently confirming against Angular's own DI semantics, not
   just trusting the captured stack trace at face value.)
4. Confirm the fix's `AUTH_TOKEN_KEY` constant is genuinely the single source of truth
   in both files that reference it (no leftover duplicate string).
5. Confirm the `error` handler's `401`/`403`-only logout doesn't silently swallow an
   actual expired-token case reachable some other way than `HttpErrorResponse` — i.e.
   check whether `/api/auth/me`'s failure mode from the resource-server side is always
   surfaced as a real `HttpErrorResponse` with a status code, not some other shape.
6. `git status --short` clean after commit — already true, this is a closure doc.

## Loop note

Not yet live-verified in a real browser against the redeployed Vercel build. That's the
one thing this sprint doc can't close out from the coordinator's side — needs Dimitri (or
whoever's watching the live site) to hard-refresh `/dashboard` post-redeploy and confirm
the session survives.
