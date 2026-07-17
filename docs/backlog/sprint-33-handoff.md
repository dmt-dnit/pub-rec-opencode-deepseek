# Sprint 33 Handoff — NG0200 circular-DI auth-token loss fix

**Coordinator:** Claude Code. **Date:** 2026-07-17.
Review-Target-Commit: 380792f

## Process note, read first

This fix was implemented **coordinator-direct**, not via opencode+DeepSeek or a worktree
agent — a deviation from this repo's default implementer routing, flagged in
`docs/backlog/sprint-33.md`. It reached `main` already (frontend, coordinator-supervised
work pushes direct per the existing merge-gate policy), so this handoff exists purely to
get a genuine independent review of logic-bearing code that, unusually for this project,
no other agent has yet looked at.

## What was done

Full technical account: `docs/backlog/sprint-33.md` and
`docs/backlog/investigation-localstorage-token-loss.md` (root-cause section at top).
Short version: `authInterceptor`'s `inject(AuthService)` was re-entering `AuthService`
while its own constructor (which calls `fetchMe()` synchronously) was still running,
throwing Angular's `NG0200` circular-dependency error. That error was silently caught by
`fetchMe()`'s RxJS `error` handler, which unconditionally logged the user out — explaining
every observed symptom, including why nothing ever appeared in the console or the Network
tab.

Fix, applied identically to `order-ui` and `inventory-ui`:
- `auth.interceptor.ts` reads the token straight from `localStorage` (`AUTH_TOKEN_KEY`,
  now exported from `auth.service.ts`) instead of injecting `AuthService` — removes the
  cycle regardless of construction order.
- `fetchMe()`'s error handler now only logs out on a real `401`/`403`
  `HttpErrorResponse`; anything else is `console.error`'d, not treated as "session
  invalid."
- `auth.guard.ts` (both apps) / `admin.guard.ts` (`order-ui`) now `console.warn` the
  reason for every redirect they trigger.

## Coordinator verification

- `cd order-ui && npx ng build --configuration production` — clean, no errors
  (`Application bundle generation complete`, output at `order-ui/dist`).
- `cd inventory-ui && npx ng build --configuration production` — clean, same result.
- `git diff --stat` reviewed directly: exactly the 7 intended files, 116
  insertions/18 deletions, no incidental changes.
- Confirmed `auth.service.ts` and `interceptors/auth.interceptor.ts` were byte-identical
  between `order-ui` and `inventory-ui` before editing (`diff` empty), so the same patch
  was applied to both rather than drifting.
- Did **not** yet verify live in a browser against the redeployed Vercel build — that
  requires an actual hard-refresh reproduction post-deploy, which the coordinator can't
  do without browser access.

## What Codex should check independently

1. Whether `inject(AuthService)` inside a functional `HttpInterceptorFn`, invoked
   synchronously from `HttpClient.get(...).subscribe(...)` called from within
   `AuthService`'s own constructor, genuinely matches Angular's documented `NG0200`
   circular-dependency trigger — confirm against Angular's DI semantics rather than
   only trusting the captured stack trace.
2. Whether restricting `fetchMe()`'s auto-logout to `401`/`403` could leave a real
   expired/invalid-token case unresolved, if `auth-server`'s resource-server config ever
   surfaces a token failure as something other than a standard `HttpErrorResponse` with
   a status code.
3. That `AUTH_TOKEN_KEY` truly has one definition per app (`auth.service.ts`) and every
   other read site imports it rather than re-declaring the literal.

## Explicitly out of scope

- No backend/auth-server changes.
- No change to guard pass/fail logic — only added logging.
- Live-browser confirmation on the redeployed site — still open, not a coordinator task.
