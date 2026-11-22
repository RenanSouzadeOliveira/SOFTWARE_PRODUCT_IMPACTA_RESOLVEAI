import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { App } from './app';
import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';

@Component({ template: '' })
class PaginaTeste {}

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(routes.map(({ path }) => ({ path, component: PaginaTeste }))),
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
    expect(element.querySelector('[data-testid="nav-home"]')).not.toBeNull();
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
        versao: 0,
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
        versao: 0,
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

  it('oculta o item Início quando o usuário está autenticado', () => {
    const auth = TestBed.inject(AuthService);
    Object.defineProperty(auth, 'currentUser', {
      value: signal({
        id: 1,
        nome: 'Ana',
        email: 'ana@example.com',
        perfil: 'SOLICITANTE',
        ativo: true,
        versao: 0,
      }),
    });
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[data-testid="nav-home"]'),
    ).toBeNull();
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
        versao: 0,
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

  function autenticar(perfil: 'SOLICITANTE' | 'ATENDENTE' | 'ADMIN') {
    Object.defineProperty(TestBed.inject(AuthService), 'currentUser', {
      value: signal({
        id: 1,
        nome: 'Ana',
        email: 'ana@example.com',
        perfil,
        ativo: true,
        versao: 0,
      }),
    });
  }

  it('não exibe o botão de menu para visitantes', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[data-testid="menu-toggle"]'),
    ).toBeNull();
  });

  it('abre e fecha o menu recolhível pelo botão e pelo Escape, devolvendo o foco', () => {
    autenticar('ADMIN');
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    const toggle = element.querySelector<HTMLButtonElement>('[data-testid="menu-toggle"]')!;

    expect(toggle.getAttribute('aria-controls')).toBe('menu-principal');
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    toggle.click();
    fixture.detectChanges();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(toggle.textContent).toContain('Fechar menu');
    expect(element.querySelector('header')?.classList).toContain('site-header--aberto');

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(toggle);
  });

  it('fecha o menu após navegar', async () => {
    autenticar('SOLICITANTE');
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const toggle = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
      '[data-testid="menu-toggle"]',
    )!;
    toggle.click();
    fixture.detectChanges();

    await TestBed.inject(Router).navigateByUrl('/');
    fixture.detectChanges();

    expect(toggle.getAttribute('aria-expanded')).toBe('false');
  });

  it('mantém os itens administrativos somente para ADMIN', () => {
    autenticar('ATENDENTE');
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('a[routerLink="/atendimento"]')).not.toBeNull();
    expect(element.querySelector('a[routerLink="/admin/usuarios"]')).toBeNull();
    expect(element.querySelector('a[routerLink="/chamados/novo"]')).toBeNull();
  });

  it.each([
    ['SOLICITANTE', '/chamados?search=acesso', '/chamados'],
    ['SOLICITANTE', '/chamados/novo', '/chamados/novo'],
    ['SOLICITANTE', '/chamados/7', '/chamados'],
    ['ATENDENTE', '/atendimento?status=ABERTO', '/atendimento'],
    ['ATENDENTE', '/atendimento/me', '/atendimento'],
    ['ATENDENTE', '/atendimento/chamados/7', '/atendimento'],
    ['ADMIN', '/atendimento/todos/7', '/atendimento/todos'],
    ['ADMIN', '/consulta-chamados?search=acesso', '/consulta-chamados'],
    ['ADMIN', '/admin/usuarios', '/admin/usuarios'],
    ['ADMIN', '/admin/categorias', '/admin/usuarios'],
  ] as const)('marca apenas o item atual para %s em %s', async (perfil, url, atual) => {
    autenticar(perfil);
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await TestBed.inject(Router).navigateByUrl(url);
    await fixture.whenStable();

    const menu = (fixture.nativeElement as HTMLElement).querySelector('#menu-principal')!;
    const destinos = (seletor: string) =>
      Array.from(menu.querySelectorAll(seletor), (link) => link.getAttribute('href'));
    expect(destinos('a.active')).toEqual([atual]);
    expect(destinos('a[aria-current="page"]')).toEqual([atual]);
  });

  it('o atalho de acessibilidade move o foco para o conteúdo sem navegar', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    document.body.appendChild(element);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl');

    element.querySelector<HTMLAnchorElement>('.skip-link')!.click();

    expect(document.activeElement?.id).toBe('conteudo-principal');
    expect(navigate).not.toHaveBeenCalled();
    element.remove();
  });
});
