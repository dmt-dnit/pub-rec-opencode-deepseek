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
