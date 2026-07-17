# Investigation — auth token disappears from localStorage on page refresh

**Status:** unresolved, handed to Codex (browser-capable) for live debugging. **Date opened:** 2026-07-17.
**Severity:** real, reproducible, blocks normal use of the admin/dashboard pages across reloads — but not caused by any of the recent Sprint 26–30 code changes (all independently verified correct, see below).

## Symptom

On `order-ui` (`https://pub-rec-saga-orders-ui.vercel.app`), logged in and sitting on `/dashboard` (or `/admin`):
1. Press F5 (plain refresh) or Ctrl+F5 (hard reload).
2. Immediately land on `/login` — forced to log in again.
3. Confirmed via DevTools Console (`localStorage.getItem('auth-token')`) that the token is **already `null`** right after the reload — not merely being rejected by the backend.
4. `GET /api/auth/me` (the call that would normally fire from `AuthService`'s constructor if a token existed) **never appears in the Network tab at all** — meaning the token is gone *before* any of the app's own JavaScript runs its first check.

This is 100% reproducible for the reporting user (Dimitri), tested on:
- Two separate physical laptops, both "daily driver" browsers where cookies/storage work normally on every other site.
- Both a normal browsing window and a private/incognito window.
- Both plain F5 and Ctrl+F5.
- After removing a Chrome VPN extension that was initially suspected.

## Everything ruled out, with evidence (don't re-test these — genuinely eliminated)

1. **App-level guard logic bug.** Suspected initially (`admin.guard.ts`/`auth.guard.ts` checking `currentUser?.role` before `fetchMe()` resolves — this *was* a real, separate bug fixed in Sprint 30, but it's not this bug). Ruled out because:
   - The permissive guard pattern (`if (role && role !== X)`) only rejects when the role is *affirmatively known and wrong* — an unresolved `null` role falls through and passes. This is true for both `authGuard` and (post-Sprint-30) `adminGuard`.
   - Confirmed via live bundle inspection that the deployed code exactly matches source (`grep` on the live `main.js` for the `t&&t!=="ADMIN"?...:true` pattern — present and correct).
   - More fundamentally: if the token is already `null` by the time `AuthService`'s constructor runs (confirmed via console), the guard's `isLoggedIn()` check fails *first* and redirects — the role-check branch is never even reached. The guard is a downstream symptom-observer here, not the cause.

2. **`fetchMe()`'s error handler forcing logout.** Suspected because `AuthService.fetchMe()`'s `.subscribe({ error: () => this.logout() })` unconditionally logs out on any failure. Ruled out because `GET /api/auth/me` **never fires at all** (confirmed via Network tab, filtered and unfiltered, with "Preserve log" correctly enabled *before* the reload) — this code path is never reached.

3. **JWT expiry / backend rejecting the token.** Ruled out — the backend was never even asked (no `/me` call happens). Tested a *fresh* token directly against `GET /api/auth/me` via `curl` from the coordinator side — works fine (200, correct claims).

4. **A genuinely separate, real bug found along the way (not the cause of this symptom, but worth fixing regardless):** `auth-server`'s JWT signing `KeyPair` is generated fresh and randomly on every JVM startup (`auth-server/src/main/java/be/dnit/authserver/config/JwtConfig.java:19-23`, `KeyPairGenerator.generateKeyPair()`, no persisted key, no fixed seed). This means every backend redeploy invalidates every previously issued token. **This is real and should eventually be fixed** (persist the key pair, e.g. to a file alongside Sprint 29's H2 file, or load from an environment-provided secret) — but it can't explain *this* symptom because no backend restart happened between the user's login and the failing refresh.

5. **Other browser tabs sharing localStorage.** Ruled out — reproduces even after closing all other tabs/windows and restarting the browser fresh.

6. **Browser extensions.** A Chrome VPN extension was found active (`chrome-extension://majdfhpaihoncoakbjgbdhglocklcgno`, visible as a network entry with a "provisional headers" warning) and removed — issue persisted after removal.

7. **Enterprise/managed-browser policies.** `chrome://policy` shows no active policies (empty, just the default precedence-order header).

8. **Private/Incognito mode.** Reproduces there too — rules out anything specific to the regular profile's accumulated settings/history/sync state.

9. **Origin/domain mismatch.** The URL bar shows the *exact* same origin (`https://pub-rec-saga-orders-ui.vercel.app`) before and after the reload — only the path changes (`/dashboard` → `/login`, which is the *effect*, not a cause).

10. **Hidden redirect chain.** `curl -sIL` on `/dashboard` and `/` both return a single `200`, no redirect, from the coordinator's vantage point.

11. **`Clear-Site-Data` response header.** Checked all headers on the served document, `main.js`, and root — no such header anywhere, on any response from the Vercel origin.

12. **Injected/unexpected content in the served HTML.** Fetched the live `/dashboard` document directly — only the two expected `<script>` tags (`polyfills.js`, `main.js`), no service worker registration, no third-party analytics/beacon scripts.

13. **Application code searched exhaustively for storage manipulation.** `grep -rn "localStorage" order-ui/src` returns exactly 4 hits, all inside `auth.service.ts` (`getItem` in constructor and `getToken()`, `setItem` in `setSession()`, `removeItem` in `logout()`). No `beforeunload`/`pagehide`/`visibilitychange`/`unload` listeners anywhere in the app (`main.ts`, `app.config.ts`, `app.component.ts` are all minimal, checked in full).

14. **A DevTools breakpoint set on a `removeItem` match in the Sources panel** (`Ctrl+Shift+F` search across all files in `main.js`) **was a false lead** — it paused inside `<static_initializer>` at module-evaluation time, i.e. some *unrelated* library code (almost certainly Angular Material/CDK or RxJS internals) that happens to also use the identifier `removeItem`, not the `Storage.prototype.removeItem` call from `auth.service.ts`. Resuming past it let the page finish loading normally (landing on `/login`, as expected) with no further breakpoint hits.

15. **A "Session History Item Has Been Marked Skippable" DevTools warning was investigated and is a red herring** — this is a standard, harmless Chromium intervention flagging that a route redirect happened via `router.parseUrl(...)` (programmatic navigation) rather than a user click. It confirms a guard-triggered redirect occurred (already known) but says nothing about *why* the token was already gone.

16. **Browser identity was initially miscategorized.** Response headers (`sec-ch-ua`) revealed the browser is actually **Brave** (Chromium-based, not stock Chrome) — explains why generic `chrome://policy`/`chrome://extensions` checks might miss Brave-specific privacy features (**Shields**). Toggling Shields down for this specific site did **not** fix the issue, ruling out Brave's per-site Shields setting as the (sole) cause.

## Not yet tried / open hypotheses for the next debugging session

- **Corporate network / TLS-inspecting proxy or endpoint security software.** If these are work-managed laptops, some corporate security tools (Zscaler, Netskope, certain EDR/DLP agents) perform TLS interception and could theoretically interfere with responses in ways invisible to `chrome://policy` (which only reflects Chrome's own policy engine, not OS/network-level tooling). **Not yet tested**: reproduce from a completely different network (e.g. phone mobile hotspot, bypassing any corporate VPN/proxy).
- **Brave-wide (not per-site) privacy settings**, e.g. `brave://settings/shields` global defaults, or Brave's "Forgetful Browsing" feature (clears site data after tab/window close — though that's normally tied to closing, not refreshing, so a weaker candidate, but not directly tested).
- **A genuinely surgical breakpoint on the real `Storage.prototype.removeItem` and `Storage.prototype.getItem`**, e.g. via DevTools "Local Overrides" (Sources → Overrides → save `main.js` locally → patch in an explicit `debugger`/`console.trace()` wrapping the *actual* `logout()` invocation, verified by cross-referencing against the known source at `order-ui/src/app/services/auth.service.ts:53-57`) rather than a blind text search that can match unrelated identifiers in a 386KB minified bundle.
- **Vercel edge/CDN regional inconsistency.** All of the coordinator's `curl` checks originate from wherever the coordinator's environment is hosted, not from the user's actual geographic location — in principle a CDN edge-config propagation inconsistency could serve different behavior regionally, though this is a weak, hard-to-verify hypothesis and no evidence points to it specifically.
- **Confirm the token is genuinely being read under the exact same key.** `private tokenKey = 'auth-token'` (`order-ui/src/app/services/auth.service.ts:11`) — worth double-checking in the live Console that no OTHER key (typo variant, different casing) is present in `localStorage` after the "loss," which would suggest a key-name mismatch rather than an actual deletion. (`Object.keys(localStorage)` right after the failed reload would show this.)

## Relevant code (all independently verified correct against the live deployed bundle, not just source)

- `order-ui/src/app/services/auth.service.ts` — full file, only 4 `localStorage` touch points, all as expected.
- `order-ui/src/app/guards/auth.guard.ts` and `admin.guard.ts` — both use the permissive null-safe role pattern as of Sprint 30 (`3f092b9`).
- `order-ui/src/app/interceptors/auth.interceptor.ts` — attaches `Authorization: Bearer <token>` unconditionally when a token exists; does not touch storage.
- `order-ui/src/app/main.ts`, `app.config.ts`, `app.component.ts` — minimal, no lifecycle hooks, no service worker.
- `auth-server/src/main/java/be/dnit/authserver/controller/AuthController.java` — `/api/auth/me` reads claims off the validated `Jwt` principal; works correctly when actually called (verified via direct `curl` with a fresh token).
- `auth-server/src/main/java/be/dnit/authserver/config/JwtConfig.java` — **separate, real, already-identified bug**: ephemeral signing key regenerated on every restart (see point 4 above). Worth its own fix eventually, tracked here so it isn't lost, but not the cause of this specific symptom.

## How to reproduce

1. Go to `https://pub-rec-saga-orders-ui.vercel.app/login`.
2. Log in as `admin@example.test` / `admin123` (seeded, `ADMIN` role) — or the reporting user's own promoted account.
3. Confirm `/dashboard` loads normally, orders/data visible.
4. Open DevTools Console, run `localStorage.getItem('auth-token')` — confirm it prints the token.
5. Press F5.
6. Re-run the same console command — it returns `null`. Page has already redirected to `/login`.

## Suggested first step for whoever picks this up

Use DevTools "Local Overrides" to get a real, reliable breakpoint on the *actual* `AuthService.logout()`/`Storage.prototype.removeItem` call (not a blind minified-text search, which produced a false lead — see ruled-out item 14) and capture the real call stack at the moment the key actually disappears. If the browser's own JS never touches it (call stack shows nothing running, or the breakpoint never fires at all across a reload), that would point away from the app entirely and toward network/proxy interference or a genuinely obscure Chromium/Brave storage-eviction behavior — at which point the corporate-network/hotspot test above becomes the highest-value next step.
