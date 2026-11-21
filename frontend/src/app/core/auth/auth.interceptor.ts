import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, take, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const isApiRequest = belongsToApi(request.url);
  const isAuthRequest = isAuthenticationEndpoint(request.url);
  const token = isApiRequest && !isAuthRequest ? auth.getAccessToken() : null;
  const authenticatedRequest = token
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request;

  return next(authenticatedRequest).pipe(
    catchError((error: unknown) => {
      if (
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        isApiRequest &&
        !isAuthRequest
      ) {
        auth.logout();
        if (!router.url.startsWith('/login')) {
          const returnUrl = safeCurrentUrl(router.url);
          void router.navigate(['/login'], { queryParams: { returnUrl } });
        }
      }
      if (
        error instanceof HttpErrorResponse &&
        error.status === 403 &&
        isApiRequest &&
        request.url.split(/[?#]/, 1)[0].includes('/admin/')
      ) {
        return auth.loadCurrentUser().pipe(
          take(1),
          catchError(() => throwError(() => error)),
          switchMap((usuario) => {
            if (usuario.perfil !== 'ADMIN' && router.url.startsWith('/admin/')) {
              void router.navigate(['/acesso-negado']);
            }
            return throwError(() => error);
          }),
        );
      }
      return throwError(() => error);
    }),
  );
};

function belongsToApi(url: string): boolean {
  const base = environment.apiUrl.replace(/\/$/, '');
  return url === base || url.startsWith(`${base}/`);
}

function isAuthenticationEndpoint(url: string): boolean {
  const path = url.split(/[?#]/, 1)[0].replace(/\/$/, '');
  return path.endsWith('/auth/login') || path.endsWith('/auth/register');
}

function safeCurrentUrl(url: string): string {
  return url.startsWith('/') && !url.startsWith('//') && !url.includes('\\') ? url : '/';
}
