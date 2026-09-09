import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';

import { Categoria, Chamado } from './chamado.model';
import { ChamadosService } from './chamados.service';
import { NovoChamadoComponent } from './novo-chamado.component';

describe('NovoChamadoComponent', () => {
  const categoria: Categoria = { id: 1, nome: 'Acesso' };
  const categorias: Subject<readonly Categoria[]>[] = [];
  const chamados = {
    listarCategorias: vi.fn(() => {
      const response = new Subject<readonly Categoria[]>();
      categorias.push(response);
      return response.asObservable();
    }),
    criar: vi.fn(),
  };

  beforeEach(async () => {
    categorias.length = 0;
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [NovoChamadoComponent],
      providers: [{ provide: ChamadosService, useValue: chamados }],
    }).compileComponents();
  });

  it('valida título, descrição, categoria e prioridade antes de enviar', () => {
    const fixture = createFixture();
    submit(fixture.nativeElement as HTMLElement);
    fixture.detectChanges();

    expect(chamados.criar).not.toHaveBeenCalled();
    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text).toContain('Informe um título');
    expect(text).toContain('Informe uma descrição');
    expect(text).toContain('Selecione uma categoria');
    expect(text).toContain('Selecione uma prioridade');
  });

  it('calcula os limites de texto depois de remover espaços externos', () => {
    const fixture = createFixture();
    completeCategories();
    fixture.detectChanges();
    fill(fixture.nativeElement as HTMLElement, '#chamado-titulo', '    x    ');
    fill(fixture.nativeElement as HTMLElement, '#chamado-descricao', '                   curta                   ');
    selectCategory(fixture.nativeElement as HTMLElement, '1');
    selectPriority(fixture.nativeElement as HTMLElement, 'MEDIA');

    submit(fixture.nativeElement as HTMLElement);
    fixture.detectChanges();

    expect(chamados.criar).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'O título deve ter pelo menos 5 caracteres.',
    );
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'A descrição deve ter pelo menos 20 caracteres.',
    );
  });

  it('mostra erro das categorias, permite retry e trata lista vazia', () => {
    const fixture = createFixture();
    categorias[0].error(new HttpErrorResponse({ status: 503, error: { message: 'Serviço indisponível' } }));
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="categorias-error"]')?.textContent)
      .toContain('Serviço indisponível');
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('[data-testid="categorias-error"] button')?.click();
    expect(chamados.listarCategorias).toHaveBeenCalledTimes(2);

    categorias[1].next([]);
    categorias[1].complete();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="categorias-empty"]')).not.toBeNull();
  });

  it('envia uma vez, preserva dados e mostra violações quando a API falha', () => {
    const fixture = createFixture();
    completeCategories();
    fixture.detectChanges();
    fill(fixture.nativeElement as HTMLElement, '#chamado-titulo', '  Sem acesso  ');
    fill(fixture.nativeElement as HTMLElement, '#chamado-descricao', 'A conta não consegue acessar o portal acadêmico.');
    selectCategory(fixture.nativeElement as HTMLElement, '1');
    selectPriority(fixture.nativeElement as HTMLElement, 'ALTA');

    const result = new Subject<Chamado>();
    chamados.criar.mockReturnValue(result);
    const form = (fixture.nativeElement as HTMLElement).querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    form.dispatchEvent(new Event('submit'));
    expect(chamados.criar).toHaveBeenCalledOnce();
    expect(chamados.criar).toHaveBeenCalledWith({
      titulo: 'Sem acesso',
      descricao: 'A conta não consegue acessar o portal acadêmico.',
      categoriaId: 1,
      prioridade: 'ALTA',
    });

    result.error(
      new HttpErrorResponse({
        status: 400,
        error: {
          message: 'Revise os dados informados.',
          violations: [{ field: 'titulo', message: 'O título já está em uso.' }],
        },
      }),
    );
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[data-testid="chamado-error"]')?.textContent).toContain('Revise os dados');
    expect(element.textContent).toContain('O título já está em uso.');
    expect(element.querySelector<HTMLInputElement>('#chamado-titulo')?.value).toBe('  Sem acesso  ');
    expect(element.querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled).toBe(false);
  });

  it('mostra o protocolo no sucesso e permite abrir outro chamado', () => {
    const fixture = createFixture();
    completeCategories();
    fixture.detectChanges();
    fill(fixture.nativeElement as HTMLElement, '#chamado-titulo', 'Sem acesso');
    fill(fixture.nativeElement as HTMLElement, '#chamado-descricao', 'A conta não consegue acessar o portal acadêmico.');
    selectCategory(fixture.nativeElement as HTMLElement, '1');
    selectPriority(fixture.nativeElement as HTMLElement, 'MEDIA');

    const result = new Subject<Chamado>();
    chamados.criar.mockReturnValue(result);
    submit(fixture.nativeElement as HTMLElement);
    result.next(createdTicket);
    result.complete();
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[data-testid="chamado-protocolo"]')?.textContent).toContain('RA-2026-0001');
    element.querySelector<HTMLButtonElement>('[data-testid="chamado-success"] button')?.click();
    fixture.detectChanges();
    expect(element.querySelector('[data-testid="chamado-success"]')).toBeNull();
    expect(element.querySelector<HTMLInputElement>('#chamado-titulo')?.value).toBe('');
  });

  function createFixture() {
    const fixture = TestBed.createComponent(NovoChamadoComponent);
    fixture.detectChanges();
    return fixture;
  }

  function completeCategories(): void {
    categorias[0].next([categoria]);
    categorias[0].complete();
  }
});

const createdTicket: Chamado = {
  id: 10,
  protocolo: 'RA-2026-0001',
  titulo: 'Sem acesso',
  descricao: 'A conta não consegue acessar o portal acadêmico.',
  status: 'ABERTO',
  prioridade: 'MEDIA',
  solicitante: { id: 1, nome: 'Ana', email: 'ana@example.com', perfil: 'SOLICITANTE' },
  atendente: null,
  categoria: { id: 1, nome: 'Acesso' },
  criadoEm: '2026-09-09T12:00:00Z',
  atualizadoEm: '2026-09-09T12:00:00Z',
};

function submit(root: HTMLElement): void {
  root.querySelector('form')?.dispatchEvent(new Event('submit'));
}

function fill(root: HTMLElement, selector: string, value: string): void {
  const control = root.querySelector<HTMLInputElement | HTMLTextAreaElement>(selector);
  if (!control) throw new Error(`Campo ${selector} não encontrado`);
  control.value = value;
  control.dispatchEvent(new Event('input'));
}

function selectCategory(root: HTMLElement, value: string): void {
  const select = root.querySelector<HTMLSelectElement>('#chamado-categoria');
  if (!select) throw new Error('Categoria não encontrada');
  const option = Array.from(select.options).find((candidate) => candidate.textContent && candidate.value.includes(value));
  if (!option) throw new Error(`Categoria ${value} não encontrada`);
  select.value = option.value;
  select.dispatchEvent(new Event('change'));
}

function selectPriority(root: HTMLElement, value: string): void {
  const input = root.querySelector<HTMLInputElement>(`input[value="${value}"]`);
  if (!input) throw new Error(`Prioridade ${value} não encontrada`);
  input.click();
}
