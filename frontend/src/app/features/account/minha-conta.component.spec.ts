import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { Usuario } from '../../core/auth/auth.models';
import { MinhaContaComponent } from './minha-conta.component';

describe('MinhaContaComponent', () => {
  const user: Usuario = {
    id: 1,
    nome: 'Ana Silva',
    email: 'ana@example.com',
    perfil: 'SOLICITANTE',
    ativo: true,
  };
  const auth = { loadCurrentUser: vi.fn(), updateCurrentUser: vi.fn() };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [MinhaContaComponent],
      providers: [{ provide: AuthService, useValue: auth }],
    }).compileComponents();
  });

  it('mostra carregamento e os dados somente leitura recebidos', () => {
    const result = new Subject<Usuario>();
    auth.loadCurrentUser.mockReturnValue(result);
    const fixture = TestBed.createComponent(MinhaContaComponent);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="account-loading"]')).not.toBeNull();

    result.next(user);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('ana@example.com');
    expect(element.textContent).toContain('SOLICITANTE');
    expect(element.querySelector('[formControlName="email"]')).toBeNull();
  });

  it('edita apenas o nome, impede envio duplo e confirma o sucesso', () => {
    const update = new Subject<Usuario>();
    auth.loadCurrentUser.mockReturnValue(new Subject<Usuario>());
    auth.updateCurrentUser.mockReturnValue(update);
    const fixture = TestBed.createComponent(MinhaContaComponent);
    fixture.detectChanges();
    const load = auth.loadCurrentUser.mock.results[0].value as Subject<Usuario>;
    load.next(user);
    fixture.detectChanges();

    const input = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('#conta-nome');
    if (!input) throw new Error('Campo nome não encontrado');
    input.value = 'Ana Souza';
    input.dispatchEvent(new Event('input'));
    const form = (fixture.nativeElement as HTMLElement).querySelector('form');
    form?.dispatchEvent(new Event('submit'));
    form?.dispatchEvent(new Event('submit'));
    expect(auth.updateCurrentUser).toHaveBeenCalledOnce();
    expect(auth.updateCurrentUser).toHaveBeenCalledWith({ nome: 'Ana Souza' });

    update.next({ ...user, nome: 'Ana Souza' });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="account-success"]')).not.toBeNull();
  });

  it('exibe falha de carregamento e permite tentar novamente', () => {
    const first = new Subject<Usuario>();
    const second = new Subject<Usuario>();
    auth.loadCurrentUser.mockReturnValueOnce(first).mockReturnValueOnce(second);
    const fixture = TestBed.createComponent(MinhaContaComponent);
    fixture.detectChanges();
    first.error(new Error('indisponível'));
    fixture.detectChanges();
    const retry = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.secondary-button');
    expect(retry).not.toBeNull();
    retry?.click();
    expect(auth.loadCurrentUser).toHaveBeenCalledTimes(2);
  });
});
