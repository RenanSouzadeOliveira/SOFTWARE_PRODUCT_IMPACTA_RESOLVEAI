import { routes } from './app.routes';
import { authGuard } from './core/auth/auth.guard';
import { roleGuard } from './core/auth/role.guard';

describe('app routes for requester tickets', () => {
  it('keeps the new-ticket route before the parameterized detail route', () => {
    const ticketPaths = routes
      .map((route) => route.path)
      .filter((path): path is string => path?.startsWith('chamados') ?? false);

    expect(ticketPaths).toEqual(['chamados/novo', 'chamados', 'chamados/:id']);
  });

  it('protects list and detail pages for authenticated requesters', () => {
    const listRoute = routes.find((route) => route.path === 'chamados');
    const detailRoute = routes.find((route) => route.path === 'chamados/:id');

    expect(listRoute?.canActivate).toEqual([authGuard, roleGuard]);
    expect(listRoute?.data).toEqual({ role: 'SOLICITANTE' });
    expect(detailRoute?.canActivate).toEqual([authGuard, roleGuard]);
    expect(detailRoute?.data).toEqual({ role: 'SOLICITANTE' });
  });
});
