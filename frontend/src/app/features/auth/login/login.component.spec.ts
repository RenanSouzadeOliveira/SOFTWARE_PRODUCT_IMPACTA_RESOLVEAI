import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, Subject } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { LoginResponse } from '../../../core/auth/auth.models';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  const response: LoginResponse = {
    accessToken: 'token', tokenType: 'Bearer', expiresIn: 3600,
    usuario: { id: 1, nome: 'Ana', email: 'ana@example.com', perfil: 'SOLICITANTE', ativo: true },
  };
  const auth = { login: vi.fn() };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    }).compileComponents();
  });

  it('valida campos obrigatórios antes de chamar a API', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(auth.login).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Informe o e-mail');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Informe a senha');
  });

  it('impede envio duplo e navega após autenticar', () => {
    const result = new Subject<LoginResponse>();
    auth.login.mockReturnValue(result);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    fill(fixture.nativeElement, '#login-email', 'ana@example.com');
    fill(fixture.nativeElement, '#login-senha', 'senha-segura');
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    form.dispatchEvent(new Event('submit'));
    expect(auth.login).toHaveBeenCalledOnce();

    result.next(response);
    result.complete();
    expect(navigate).toHaveBeenCalledWith('/');
  });

  it('apresenta erro de credenciais inválidas', () => {
    auth.login.mockReturnValue(of(response));
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    auth.login.mockReturnValue(
      new Subject<LoginResponse>(),
    );
    const result = auth.login.mock.results;
    fill(fixture.nativeElement, '#login-email', 'ana@example.com');
    fill(fixture.nativeElement, '#login-senha', 'senha-segura');
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    const stream = result.at(-1)?.value as Subject<LoginResponse>;
    stream.error(new HttpErrorResponse({ status: 401 }));
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="login-error"]')?.textContent)
      .toContain('E-mail ou senha inválidos');
  });
});

function fill(root: HTMLElement, selector: string, value: string): void {
  const input = root.querySelector<HTMLInputElement>(selector);
  if (!input) throw new Error(`Campo ${selector} não encontrado`);
  input.value = value;
  input.dispatchEvent(new Event('input'));
}
