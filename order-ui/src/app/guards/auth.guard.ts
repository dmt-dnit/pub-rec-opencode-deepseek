import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authGuard = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isLoggedIn()) {
    console.warn('authGuard: no token present, redirecting to /login');
    return router.parseUrl('/login');
  }
  const role = auth.currentUser?.role;
  if (role && role !== 'CUSTOMER' && role !== 'ADMIN') {
    console.warn(`authGuard: role "${role}" is not permitted here, logging out`);
    auth.logout();
    return router.parseUrl('/login');
  }
  return true;
};
