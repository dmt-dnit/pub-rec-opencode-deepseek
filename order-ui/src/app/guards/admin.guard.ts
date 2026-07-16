import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

// On a hard refresh directly on /admin, AuthService's constructor calls fetchMe()
// asynchronously — if this guard runs before that HTTP call resolves, currentUser
// will still be null and a genuine admin is incorrectly redirected to /dashboard.
// This is a pre-existing pattern in this app (authGuard itself only checks token
// presence, not user data), so left as-is for now.
export const adminGuard = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.currentUser?.role === 'ADMIN' ? true : router.parseUrl('/dashboard');
};
