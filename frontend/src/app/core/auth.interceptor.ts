import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * auth.interceptor.ts
 *
 * Attaches the session token to every outbound API call and reacts to an
 * expired session centrally.
 *
 * Handling 401 here rather than in each component means that when a session
 * times out, the user is returned to the sign-in screen once, from wherever
 * they were, instead of every open screen showing its own error.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.token();

  const authorised = token
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request;

  return next(authorised).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !request.url.endsWith('/api/auth/login')) {
        auth.clearSession();
        void router.navigate(['/sign-in'], { queryParams: { expired: '1' } });
      }
      return throwError(() => error);
    })
  );
};
