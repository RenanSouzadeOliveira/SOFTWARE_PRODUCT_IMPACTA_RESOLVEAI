import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { environment } from '../../../environments/environment';
import { ApiService } from '../http/api.service';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  const auth = { getAccessToken: vi.fn(), logout: vi.fn(), loadCurrentUser: vi.fn() };
  let api: ApiService;
  let http: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    vi.clearAllMocks();
    auth.getAccessToken.mockReturnValue('jwt-token');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: auth },
      ],
    });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => http.verify());

  it('atualiza sessão após 403 administrativo, redireciona perfil rebaixado e preserva erro', () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    vi.spyOn(router, 'url', 'get').mockReturnValue('/admin/usuarios');
    auth.loadCurrentUser.mockReturnValue(of({ perfil: 'SOLICITANTE' }));
    let received: unknown;
    api.get('admin/usuarios').subscribe({ error: (error) => (received = error) });
    http
      .expectOne(`${environment.apiUrl}/admin/usuarios`)
      .flush({ message: 'Sem permissão' }, { status: 403, statusText: 'Forbidden' });
    expect(auth.loadCurrentUser).toHaveBeenCalledOnce();
    expect(navigate).toHaveBeenCalledWith(['/dashboard']);
    expect(received).toMatchObject({ status: 403, error: { message: 'Sem permissão' } });
    expect(auth.logout).not.toHaveBeenCalled();
  });
  it('preserva 403 de regra administrativa se perfil continua ADMIN', () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    auth.loadCurrentUser.mockReturnValue(of({ perfil: 'ADMIN' }));
    let received: unknown;
    api.patch('admin/usuarios/1', {}).subscribe({ error: (error) => (received = error) });
    http
      .expectOne(`${environment.apiUrl}/admin/usuarios/1`)
      .flush({}, { status: 403, statusText: 'Forbidden' });
    expect(received).toMatchObject({ status: 403 });
    expect(navigate).not.toHaveBeenCalled();
  });
  it('não consulta novamente se atualização de sessão falha nem em 403 comum', () => {
    auth.loadCurrentUser.mockReturnValue(throwError(() => new Error('offline')));
    api.get('admin/usuarios').subscribe({ error: () => undefined });
    http
      .expectOne(`${environment.apiUrl}/admin/usuarios`)
      .flush({}, { status: 403, statusText: 'Forbidden' });
    api.get('usuarios/me').subscribe({ error: () => undefined });
    http
      .expectOne(`${environment.apiUrl}/usuarios/me`)
      .flush({}, { status: 403, statusText: 'Forbidden' });
    expect(auth.loadCurrentUser).toHaveBeenCalledOnce();
  });

  it('adiciona Bearer em chamada protegida da API', () => {
    api.get('usuarios/me').subscribe();
    const request = http.expectOne(`${environment.apiUrl}/usuarios/me`);
    expect(request.request.headers.get('Authorization')).toBe('Bearer jwt-token');
    request.flush({});
  });

  it('não adiciona token aos endpoints de autenticação', () => {
    api.post('auth/login', { email: 'a@b.com', senha: 'senha' }).subscribe();
    const request = http.expectOne(`${environment.apiUrl}/auth/login`);
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it('limpa a sessão e redireciona ao receber 401 autenticado', () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    api.get('usuarios/me').subscribe({ error: () => undefined });
    http
      .expectOne(`${environment.apiUrl}/usuarios/me`)
      .flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(auth.logout).toHaveBeenCalledOnce();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/' } });
  });

  it('redireciona uma chamada protegida que chega sem token', () => {
    auth.getAccessToken.mockReturnValue(null);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    api.get('usuarios/me').subscribe({ error: () => undefined });
    const request = http.expectOne(`${environment.apiUrl}/usuarios/me`);
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(auth.logout).toHaveBeenCalledOnce();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/' } });
  });
});

describe('reconciliação administrativa com sessão real', () => {
  afterEach(() => sessionStorage.clear());

  it.each(['SOLICITANTE', 'ADMIN', '401'] as const)(
    'reconcilia perfil %s via usuarios/me sem repetir operação',
    (perfil) => {
      sessionStorage.clear();
      TestBed.configureTestingModule({
        providers: [
          provideHttpClient(withInterceptors([authInterceptor])),
          provideHttpClientTesting(),
          provideRouter([]),
        ],
      });
      const auth = TestBed.inject(AuthService);
      const api = TestBed.inject(ApiService);
      const http = TestBed.inject(HttpTestingController);
      const router = TestBed.inject(Router);
      vi.spyOn(router, 'url', 'get').mockReturnValue('/admin/usuarios');
      const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
      const usuario = {
        id: 1,
        nome: 'Pessoa exemplo',
        email: 'pessoa@example.com',
        perfil: 'ADMIN',
        ativo: true,
        versao: 0,
      };
      const accessToken = `header.${btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }))}.signature`;
      auth.login({ email: usuario.email, senha: 'senha1234' }).subscribe();
      http.expectOne(`${environment.apiUrl}/auth/login`).flush({
        accessToken,
        tokenType: 'Bearer',
        expiresIn: 3600,
        usuario,
      });
      let received: unknown;
      api
        .patch('admin/usuarios/2', { nome: 'Outro nome' })
        .subscribe({ error: (error) => (received = error) });
      http
        .expectOne(`${environment.apiUrl}/admin/usuarios/2`)
        .flush({ message: 'Ação recusada' }, { status: 403, statusText: 'Forbidden' });
      const refresh = http.expectOne(`${environment.apiUrl}/usuarios/me`);
      if (perfil === '401') {
        refresh.flush({}, { status: 401, statusText: 'Unauthorized' });
        expect(auth.currentUser()).toBeNull();
        expect(navigate).toHaveBeenCalledExactlyOnceWith(['/login'], {
          queryParams: { returnUrl: '/admin/usuarios' },
        });
      } else {
        refresh.flush({ ...usuario, perfil, versao: 1 });
        expect(auth.currentUser()?.perfil).toBe(perfil);
        expect(auth.hasRole('ADMIN')).toBe(perfil === 'ADMIN');
        expect(JSON.parse(sessionStorage.getItem('resolveai.auth.session')!).usuario.perfil).toBe(
          perfil,
        );
        if (perfil === 'SOLICITANTE')
          expect(navigate).toHaveBeenCalledExactlyOnceWith(['/dashboard']);
        else expect(navigate).not.toHaveBeenCalled();
      }
      expect(received).toMatchObject({ status: 403, error: { message: 'Ação recusada' } });
      http.verify();
    },
  );
});
