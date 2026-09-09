import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Subject } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { Usuario } from '../../../core/auth/auth.models';
import { CadastroComponent } from './cadastro.component';

describe('CadastroComponent', () => {
  const auth = { register: vi.fn() };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [CadastroComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    }).compileComponents();
  });

  it('não possui campo de perfil e valida a senha', () => {
    const fixture = TestBed.createComponent(CadastroComponent);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[formControlName="perfil"]')).toBeNull();
    fill(fixture.nativeElement, '#cadastro-nome', 'Ana');
    fill(fixture.nativeElement, '#cadastro-email', 'ana@example.com');
    fill(fixture.nativeElement, '#cadastro-senha', 'curta');
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(auth.register).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('pelo menos 8 caracteres');
  });

  it('rejeita senha multibyte que ultrapassa o limite seguro do BCrypt', () => {
    const fixture = TestBed.createComponent(CadastroComponent);
    fixture.detectChanges();
    fill(fixture.nativeElement, '#cadastro-nome', 'Ana');
    fill(fixture.nativeElement, '#cadastro-email', 'ana@example.com');
    fill(fixture.nativeElement, '#cadastro-senha', '😀'.repeat(19));
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(auth.register).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('72 bytes em UTF-8');
  });

  it('trata e-mail duplicado e reabilita o envio', () => {
    const result = new Subject<Usuario>();
    auth.register.mockReturnValue(result);
    const fixture = TestBed.createComponent(CadastroComponent);
    fixture.detectChanges();
    fill(fixture.nativeElement, '#cadastro-nome', 'Ana');
    fill(fixture.nativeElement, '#cadastro-email', 'ana@example.com');
    fill(fixture.nativeElement, '#cadastro-senha', 'senha-segura');
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    result.error(new HttpErrorResponse({ status: 409 }));
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="cadastro-error"]')?.textContent)
      .toContain('já está cadastrado');
    expect((fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled)
      .toBe(false);
  });

  it('navega ao login depois do cadastro', () => {
    const result = new Subject<Usuario>();
    auth.register.mockReturnValue(result);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(CadastroComponent);
    fixture.detectChanges();
    fill(fixture.nativeElement, '#cadastro-nome', 'Ana');
    fill(fixture.nativeElement, '#cadastro-email', 'ana@example.com');
    fill(fixture.nativeElement, '#cadastro-senha', 'senha-segura');
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    result.next({ id: 1, nome: 'Ana', email: 'ana@example.com', perfil: 'SOLICITANTE', ativo: true });
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { cadastrado: '1' } });
  });
});

function fill(root: HTMLElement, selector: string, value: string): void {
  const input = root.querySelector<HTMLInputElement>(selector);
  if (!input) throw new Error(`Campo ${selector} não encontrado`);
  input.value = value;
  input.dispatchEvent(new Event('input'));
}
