import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

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
      if (error instanceof HttpErrorResponse && error.status === 401 && isApiRequest && !isAuthRequest) {
        const returnUrl = safeCurrentUrl(router.url);
        auth.logout();
        void router.navigate(['/login'], { queryParams: { returnUrl } });
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
