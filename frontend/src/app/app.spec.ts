import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { App } from './app';
import { AuthService } from './core/auth/auth.service';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: { currentUser: signal(null), logout: vi.fn() },
        },
      ],
    }).compileComponents();
  });

  it('cria o layout principal acessível', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('header')).not.toBeNull();
    expect(element.querySelector('main#conteudo-principal')).not.toBeNull();
    expect(element.querySelector('footer')).not.toBeNull();
    expect(element.querySelector('nav')?.getAttribute('aria-label')).toBe('Navegação principal');
  });

  it('encerra a sessão pelo cabeçalho e navega para o login', () => {
    const auth = TestBed.inject(AuthService);
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    Object.defineProperty(auth, 'currentUser', {
      value: signal({
        id: 1,
        nome: 'Ana',
        email: 'ana@example.com',
        perfil: 'SOLICITANTE',
        ativo: true,
      }),
    });
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.nav-button')?.click();

    expect(auth.logout).toHaveBeenCalledOnce();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('mostra o atalho de abertura somente para solicitantes', () => {
    const auth = TestBed.inject(AuthService);
    Object.defineProperty(auth, 'currentUser', {
      value: signal({
        id: 1,
        nome: 'Ana',
        email: 'ana@example.com',
        perfil: 'SOLICITANTE',
        ativo: true,
      }),
    });
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    expect(
      (fixture.nativeElement as HTMLElement).querySelector<HTMLAnchorElement>(
        'a[routerLink="/chamados/novo"]',
      ),
    ).not.toBeNull();
  });

  it('mostra o atalho de consulta somente para solicitantes', () => {
    const auth = TestBed.inject(AuthService);
    Object.defineProperty(auth, 'currentUser', {
      value: signal({
        id: 1,
        nome: 'Ana',
        email: 'ana@example.com',
        perfil: 'SOLICITANTE',
        ativo: true,
      }),
    });
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    expect(
      (fixture.nativeElement as HTMLElement).querySelector<HTMLAnchorElement>(
        'a[routerLink="/chamados"]',
      ),
    ).not.toBeNull();
  });
});
