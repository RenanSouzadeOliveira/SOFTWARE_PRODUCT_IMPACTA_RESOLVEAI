import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { environment } from '../../../environments/environment';
import { ApiService } from '../http/api.service';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  const auth = { getAccessToken: vi.fn(), logout: vi.fn() };
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
    http.expectOne(`${environment.apiUrl}/usuarios/me`).flush({}, { status: 401, statusText: 'Unauthorized' });

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
