import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

const AUTH_ENDPOINT_SUFFIXES = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout'];

/**
 * Attaches the in-memory access token as a bearer header, and on a 401 attempts one silent
 * refresh-and-retry before giving up and routing to /login. The auth endpoints themselves are
 * excluded from that retry — a failing /auth/refresh must never trigger another refresh attempt of
 * itself, or this would recurse forever.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const isAuthEndpoint = AUTH_ENDPOINT_SUFFIXES.some((suffix) => req.url.endsWith(suffix));
  const accessToken = authService.getAccessToken();
  const authorizedReq =
    accessToken && !isAuthEndpoint
      ? req.clone({ setHeaders: { Authorization: `Bearer ${accessToken}` } })
      : req;

  return next(authorizedReq).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || isAuthEndpoint) {
        return throwError(() => error);
      }

      return authService.refresh().pipe(
        switchMap((refreshed) => {
          const retriedReq = req.clone({
            setHeaders: { Authorization: `Bearer ${refreshed.accessToken}` },
          });
          return next(retriedReq);
        }),
        catchError((refreshError: unknown) => {
          router.navigateByUrl('/login');
          return throwError(() => refreshError);
        }),
      );
    }),
  );
};
