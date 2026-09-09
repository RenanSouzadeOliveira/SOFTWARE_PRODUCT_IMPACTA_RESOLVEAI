import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { Usuario } from '../../core/auth/auth.models';
import { HomeComponent } from './home.component';

describe('HomeComponent', () => {
  const auth = { currentUser: signal<Usuario | null>(null) };

  beforeEach(async () => {
    auth.currentUser.set(null);

    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    }).compileComponents();
  });

  it('explica o problema resolvido e o funcionamento do sistema', () => {
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('#home-title')?.textContent).toContain(
      'Transforme problemas em atendimentos acompanháveis',
    );
    expect(element.querySelector('#problem-title')?.textContent).toContain(
      'Solicitações não deveriam se perder',
    );
    expect(element.querySelectorAll('.steps li')).toHaveLength(3);
  });

  it('oferece cadastro e login ao visitante', () => {
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('[data-testid="home-register"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="home-login"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="home-open-ticket"]')).toBeNull();
  });

  it('oferece abertura e consulta de chamados ao solicitante autenticado', () => {
    auth.currentUser.set({
      id: 1,
      nome: 'Ana',
      email: 'ana@example.com',
      perfil: 'SOLICITANTE',
      ativo: true,
    });
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('[data-testid="home-open-ticket"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="home-my-tickets"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="home-register"]')).toBeNull();
  });
});
