# Sprint 28 Track C Review - Admin Approval / Role Scope

Review-Target-Commit: `2feba3a`  
Handoff: `docs/backlog/sprint-28-handoff.md`  
Verdict: REJECT

## Findings

- **[P1] The new `/admin` page is unreliable for legitimate admins on a hard refresh because `adminGuard` reads `currentUser` before `fetchMe()` completes.** `AuthService` only populates `userSubject` asynchronously in its constructor via `fetchMe()` when a token exists at [order-ui/src/app/services/auth.service.ts](C:/projects/pub-rec-opencode-deepseek/order-ui/src/app/services/auth.service.ts:20), [order-ui/src/app/services/auth.service.ts](C:/projects/pub-rec-opencode-deepseek/order-ui/src/app/services/auth.service.ts:22), and [order-ui/src/app/services/auth.service.ts](C:/projects/pub-rec-opencode-deepseek/order-ui/src/app/services/auth.service.ts:35). The new `adminGuard` then synchronously checks `auth.currentUser?.role === 'ADMIN'` and otherwise redirects to `/dashboard` at [order-ui/src/app/guards/admin.guard.ts](C:/projects/pub-rec-opencode-deepseek/order-ui/src/app/guards/admin.guard.ts:13). That means a real admin who reloads directly on `/admin` can be bounced away before the `/api/auth/me` response arrives. The code comment in the guard explicitly acknowledges the bug at [order-ui/src/app/guards/admin.guard.ts](C:/projects/pub-rec-opencode-deepseek/order-ui/src/app/guards/admin.guard.ts:5), so this is a known unresolved defect in the delivered feature, not a hypothetical edge case.

## Notes

- The password-hash leak fix is correct by source inspection: `@JsonIgnore` is on the `password` field itself at [auth-server/src/main/java/be/dnit/authserver/model/UserEntity.java](C:/projects/pub-rec-opencode-deepseek/auth-server/src/main/java/be/dnit/authserver/model/UserEntity.java:18), and `AuthController` / `CustomUserDetailsService` still use the field internally for authentication rather than JSON serialization.
- The backend role restrictions in `order-service` and `inventory-service` are correctly enforced at the real security boundary by `hasAnyRole(...)` in [order-service/src/main/java/be/dnit/orderservice/config/SecurityConfig.java](C:/projects/pub-rec-opencode-deepseek/order-service/src/main/java/be/dnit/orderservice/config/SecurityConfig.java:31) and [inventory-service/src/main/java/be/dnit/inventoryservice/config/SecurityConfig.java](C:/projects/pub-rec-opencode-deepseek/inventory-service/src/main/java/be/dnit/inventoryservice/config/SecurityConfig.java:31).
- The frontend role checks in the normal `authGuard` files are UX-only helpers, not the security boundary.

## Residual Checks Not Reproduced Here

- I did not perform the live backend redeploys or the separate manual role-promotion action described in the handoff.
- I did not reproduce the Angular builds from this session.
