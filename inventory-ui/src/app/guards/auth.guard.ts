import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authGuard = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isLoggedIn()) return router.parseUrl('/login');
  const role = auth.currentUser?.role;
  if (role && role !== 'WAREHOUSE_STAFF' && role !== 'ADMIN') {
    auth.logout();
    return router.parseUrl('/login');
  }
  return true;
};
