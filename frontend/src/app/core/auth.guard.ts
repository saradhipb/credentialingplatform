import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * auth.guard.ts
 *
 * Route guards that keep a signed-out user away from protected screens and a
 * non-administrator away from the administration screens.
 *
 * These guards are a usability measure, not the security boundary. The server
 * enforces the same rules in AuthorizationFilter, because anything decided in
 * the browser can be bypassed by the browser.
 */

/** Allows the route only when a session is open. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isSignedIn()) {
    return true;
  }
  return router.createUrlTree(['/sign-in'], { queryParams: { returnUrl: state.url } });
};

/** Allows the route only for the ADMIN role. */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.isAdmin() ? true : router.createUrlTree(['/dashboard']);
};
