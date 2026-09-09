import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';

import { AuthService } from './auth.service';
import { authGuard, safeReturnUrl } from './auth.guard';
import { roleGuard } from './role.guard';

describe('guards de autenticação', () => {
  const auth = { isAuthenticated: vi.fn(), hasRole: vi.fn() };

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
  });

  it('authGuard preserva a URL local para retornar após o login', () => {
    auth.isAuthenticated.mockReturnValue(false);
    const result = TestBed.runInInjectionContext(() =>
      authGuard(
        {} as ActivatedRouteSnapshot,
        { url: '/minha-conta?aba=perfil' } as RouterStateSnapshot,
      ),
    );

    expect(result instanceof UrlTree).toBe(true);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toContain(
      'returnUrl=%2Fminha-conta%3Faba%3Dperfil',
    );
  });

  it('roleGuard aceita apenas o perfil indicado na rota', () => {
    auth.hasRole.mockReturnValue(true);
    const result = TestBed.runInInjectionContext(() =>
      roleGuard({ data: { role: 'ATENDENTE' } } as unknown as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
    expect(result).toBe(true);
    expect(auth.hasRole).toHaveBeenCalledWith('ATENDENTE');
  });

  it('bloqueia returnUrl externo ou ambíguo', () => {
    expect(safeReturnUrl('https://example.com')).toBe('/');
    expect(safeReturnUrl('//example.com')).toBe('/');
    expect(safeReturnUrl('/\\example.com')).toBe('/');
  });
});
