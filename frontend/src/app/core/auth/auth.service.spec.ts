import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { ApiService } from '../http/api.service';
import { LoginResponse, Usuario } from './auth.models';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  const user: Usuario = {
    id: 1,
    nome: 'Ana Silva',
    email: 'ana@example.com',
    perfil: 'SOLICITANTE',
    ativo: true,
  };
  const api = {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
  };

  beforeEach(() => {
    sessionStorage.clear();
    vi.clearAllMocks();
    TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
  });

  it('normaliza credenciais e mantém a sessão somente no sessionStorage', () => {
    const response: LoginResponse = {
      accessToken: jwtIn(3600),
      tokenType: 'Bearer',
      expiresIn: 3600,
      usuario: { ...user, nome: ' Ana Silva ', email: 'ANA@EXAMPLE.COM ' },
    };
    api.post.mockReturnValue(of(response));

    const service = TestBed.inject(AuthService);
    service.login({ email: ' ANA@EXAMPLE.COM ', senha: 'senha-segura' }).subscribe();

    expect(api.post).toHaveBeenCalledWith('auth/login', {
      email: 'ana@example.com',
      senha: 'senha-segura',
    });
    expect(service.currentUser()?.nome).toBe('Ana Silva');
    expect(sessionStorage.length).toBe(1);
    expect(localStorage.length).toBe(0);
  });

  it('remove uma sessão persistida cujo JWT expirou', () => {
    sessionStorage.setItem(
      'resolveai.auth.session',
      JSON.stringify({
        accessToken: jwtIn(-60),
        tokenType: 'Bearer',
        expiresIn: 3600,
        expiresAt: Date.now() + 3_600_000,
        usuario: user,
      }),
    );

    const service = TestBed.inject(AuthService);

    expect(service.isAuthenticated()).toBe(false);
    expect(sessionStorage.length).toBe(0);
  });

  it('normaliza o cadastro sem enviar perfil', () => {
    api.post.mockReturnValue(of(user));
    const service = TestBed.inject(AuthService);

    service
      .register({ nome: ' Ana Silva ', email: ' ANA@EXAMPLE.COM ', senha: 'senha-segura' })
      .subscribe();

    expect(api.post).toHaveBeenCalledWith('auth/register', {
      nome: 'Ana Silva',
      email: 'ana@example.com',
      senha: 'senha-segura',
    });
  });
});

function jwtIn(seconds: number): string {
  const payload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + seconds }))
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
  return `header.${payload}.signature`;
}
