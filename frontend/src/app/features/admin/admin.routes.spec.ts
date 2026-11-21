import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import { routes } from '../../app.routes';
import { AuthService } from '../../core/auth/auth.service';
import { Perfil } from '../../core/auth/auth.models';
import { AdminService } from './admin.service';
import { AdminComponent } from './admin.component';

describe('Rotas administrativas', () => {
  const auth = { isAuthenticated: vi.fn(), hasRole: vi.fn(), currentUser: () => null };
  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        { provide: AuthService, useValue: auth },
        {
          provide: AdminService,
          useValue: { listarUsuarios: () => of([]), listarCategorias: () => of([]) },
        },
      ],
    });
  });
  it.each(['usuarios', 'categorias'])('permite ADMIN em /admin/%s', async (tipo) => {
    auth.isAuthenticated.mockReturnValue(true);
    auth.hasRole.mockImplementation((role: Perfil) => role === 'ADMIN');
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(`/admin/${tipo}`, AdminComponent);
    expect(harness.routeNativeElement?.textContent).toContain(
      tipo === 'usuarios' ? 'Gerenciar usuários' : 'Gerenciar categorias',
    );
  });
  it.each(['SOLICITANTE', 'ATENDENTE'] as const)(
    'nega ambos os destinos ao perfil %s',
    async (perfil) => {
      auth.isAuthenticated.mockReturnValue(true);
      auth.hasRole.mockImplementation((role: Perfil) => role === perfil);
      // Destino simples evita instanciar serviços da página inicial neste teste do guard.
      const router = TestBed.inject(Router);
      router.resetConfig([
        ...routes.filter((route) => route.path?.startsWith('admin/')),
        { path: 'acesso-negado', component: AdminComponent, data: { administracao: 'categorias' } },
      ]);
      const harness = await RouterTestingHarness.create();
      for (const tipo of ['usuarios', 'categorias']) {
        await harness.navigateByUrl(`/admin/${tipo}`);
        expect(router.url).toBe('/acesso-negado');
      }
    },
  );
  it('preserva retorno ao redirecionar anônimo para login', async () => {
    auth.isAuthenticated.mockReturnValue(false);
    const router = TestBed.inject(Router);
    router.resetConfig([
      ...routes.filter((route) => route.path?.startsWith('admin/')),
      { path: 'login', component: AdminComponent, data: { administracao: 'categorias' } },
    ]);
    const harness = await RouterTestingHarness.create();
    for (const tipo of ['usuarios', 'categorias']) {
      await harness.navigateByUrl(`/admin/${tipo}`);
      expect(router.url).toBe(`/login?returnUrl=%2Fadmin%2F${tipo}`);
    }
  });
});
