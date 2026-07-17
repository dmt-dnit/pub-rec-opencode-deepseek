import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const adminGuard = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const role = auth.currentUser?.role;
  if (role && role !== 'ADMIN') {
    console.warn(`adminGuard: role "${role}" is not ADMIN, redirecting to /dashboard`);
    return router.parseUrl('/dashboard');
  }
  return true;
};
