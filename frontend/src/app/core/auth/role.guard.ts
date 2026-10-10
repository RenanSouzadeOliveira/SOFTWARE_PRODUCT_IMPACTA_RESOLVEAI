import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { Perfil } from './auth.models';
import { AuthService } from './auth.service';

export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const role = route.data['role'] as Perfil | undefined;
  const roles = route.data['roles'] as readonly Perfil[] | undefined;
  return (roles ? roles.some((allowed) => auth.hasRole(allowed)) : role && auth.hasRole(role))
    ? true
    : router.createUrlTree(['/'], { queryParams: { acesso: 'negado' } });
};
