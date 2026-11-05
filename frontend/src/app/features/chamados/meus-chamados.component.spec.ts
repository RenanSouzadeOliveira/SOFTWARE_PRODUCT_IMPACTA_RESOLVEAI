import { FiltrosChamadosService } from './filtros-chamados.service';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router, provideRouter } from '@angular/router';
import { of, BehaviorSubject } from 'rxjs';
import { Pagina } from '../../shared/paginacao/paginacao.model';
import { Subject } from 'rxjs';

import { ChamadoResumo } from './chamado.model';
import { ChamadosService } from './chamados.service';
import { MeusChamadosComponent } from './meus-chamados.component';

describe('MeusChamadosComponent', () => {
  const responses: Subject<Pagina<ChamadoResumo>>[] = [];
  const chamadosService = {
    listarMeus: vi.fn(() => {
      const response = new Subject<Pagina<ChamadoResumo>>();
      responses.push(response);
      return response.asObservable();
    }),
  };

  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  beforeEach(async () => {
    params = new BehaviorSubject(convertToParamMap({}));
    responses.length = 0;
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [MeusChamadosComponent],
      providers: [
        provideRouter([]),
        {
          provide: FiltrosChamadosService,
          useValue: { listar: () => of({ categorias: [], responsaveis: [] }) },
        },
        { provide: ActivatedRoute, useValue: { queryParamMap: params } },
        { provide: ChamadosService, useValue: chamadosService },
      ],
    }).compileComponents();
  });

  it('mostra carregamento e os chamados recebidos com links para os detalhes', () => {
    const fixture = TestBed.createComponent(MeusChamadosComponent);
    fixture.detectChanges();

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[data-testid="chamados-loading"]'),
    ).not.toBeNull();

    responses[0].next(page([ticket]));
    responses[0].complete();
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[data-testid="chamados-success"]')).not.toBeNull();
    expect(element.textContent).toContain('RA-2026-0001');
    expect(element.textContent).toContain('Falha de acesso');
    expect(element.querySelector('a[href="/chamados/10"]')).not.toBeNull();
  });

  it('mostra estado vazio e atalho para abertura', () => {
    const fixture = TestBed.createComponent(MeusChamadosComponent);
    fixture.detectChanges();
    responses[0].next(page([]));
    responses[0].complete();
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[data-testid="chamados-empty"]')).not.toBeNull();
    expect(element.querySelector('a[href="/chamados/novo"]')).not.toBeNull();
  });

  it('mostra erro e permite tentar novamente', () => {
    const first = new Subject<Pagina<ChamadoResumo>>();
    const second = new Subject<Pagina<ChamadoResumo>>();
    chamadosService.listarMeus.mockReturnValueOnce(first).mockReturnValueOnce(second);
    const fixture = TestBed.createComponent(MeusChamadosComponent);
    fixture.detectChanges();

    first.error(new Error('indisponível'));
    fixture.detectChanges();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[data-testid="chamados-error"]'),
    ).not.toBeNull();

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('[data-testid="chamados-error"] .secondary-button')
      ?.click();
    expect(chamadosService.listarMeus).toHaveBeenCalledTimes(2);
    second.next(page([ticket]));
    second.complete();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('RA-2026-0001');
  });
  it('restaura URL, cancela resposta antiga e navega com a ordenação e tamanho ativos', () => {
    params.next(convertToParamMap({ page: '1', size: '20', sort: 'titulo,asc' }));
    const fixture = TestBed.createComponent(MeusChamadosComponent);
    fixture.detectChanges();
    expect(chamadosService.listarMeus).toHaveBeenLastCalledWith({
      page: 1,
      size: 20,
      sort: 'titulo,asc',
    });
    params.next(convertToParamMap({ page: '2', size: '20', sort: 'titulo,asc' }));
    expect(responses[0].observed).toBe(false);
    params.next(convertToParamMap({ page: '2', size: '20', sort: 'titulo,asc' }));
    expect(chamadosService.listarMeus).toHaveBeenCalledTimes(2);
    responses[1].next({ ...page([ticket]), page: 2, totalElements: 45, totalPages: 3 });
    fixture.detectChanges();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelectorAll('nav button')[1].hasAttribute('disabled')).toBe(true);
    element.querySelector<HTMLButtonElement>('nav button')!.click();
    expect(navigate).toHaveBeenLastCalledWith(
      [],
      expect.objectContaining({ queryParams: { page: 1 }, queryParamsHandling: 'merge' }),
    );
    const size = element.querySelector<HTMLSelectElement>('[aria-label="Itens por página"]')!;
    size.value = '50';
    size.dispatchEvent(new Event('change'));
    expect(navigate).toHaveBeenLastCalledWith(
      [],
      expect.objectContaining({
        queryParams: { page: 0, size: 50, sort: 'titulo,asc' },
        queryParamsHandling: 'merge',
      }),
    );
  });
});

const ticket: ChamadoResumo = {
  id: 10,
  protocolo: 'RA-2026-0001',
  titulo: 'Falha de acesso',
  status: 'ABERTO',
  prioridade: 'MEDIA',
  categoria: { id: 1, nome: 'Acesso' },
  criadoEm: '2026-09-09T12:00:00Z',
  atualizadoEm: '2026-09-09T12:00:00Z',
  resolvidoEm: null,
  fechadoEm: null,
};

function page(content: readonly ChamadoResumo[]): Pagina<ChamadoResumo> {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
  };
}
