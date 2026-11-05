import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { FiltrosChamadosComponent } from './filtros-chamados.component';
import { FiltrosChamadosService } from './filtros-chamados.service';

describe('FiltrosChamadosComponent', () => {
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  const service = { listar: vi.fn() };
  beforeEach(async () => {
    params = new BehaviorSubject(convertToParamMap({}));
    service.listar.mockReturnValue(
      of({
        categorias: [{ id: 2, nome: 'Rede' }],
        responsaveis: [{ id: 7, nome: 'Agente fictício' }],
      }),
    );
    await TestBed.configureTestingModule({
      imports: [FiltrosChamadosComponent],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { queryParamMap: params } },
        { provide: FiltrosChamadosService, useValue: service },
      ],
    }).compileComponents();
  });
  afterEach(() => vi.useRealTimers());
  function render() {
    const fixture = TestBed.createComponent(FiltrosChamadosComponent);
    fixture.detectChanges();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    return { fixture, element: fixture.nativeElement as HTMLElement, navigate };
  }
  function set(element: HTMLElement, key: string, value: string, event = 'change') {
    const control = element.querySelector<HTMLInputElement | HTMLSelectElement>(
      `[formControlName="${key}"]`,
    )!;
    control.value = value;
    if (control.tagName === 'INPUT' && event === 'change')
      control.dispatchEvent(new Event('input'));
    control.dispatchEvent(new Event(event));
  }
  it('restaura todos os filtros e acompanha voltar/avançar do navegador sem navegar novamente', () => {
    params.next(
      convertToParamMap({
        search: 'Portal',
        status: 'RESOLVIDO',
        prioridade: 'ALTA',
        categoriaId: '2',
        responsavel: '7',
        dataInicial: '2026-10-01',
        dataFinal: '2026-10-02',
        page: '2',
        sort: 'titulo,asc',
      }),
    );
    const { fixture, element, navigate } = render();
    expect(element.querySelector<HTMLInputElement>('#ticket-search')!.value).toBe('Portal');
    expect(element.querySelector<HTMLSelectElement>('#ticket-status')!.value).toBe('RESOLVIDO');
    expect(element.querySelector<HTMLSelectElement>('#ticket-assignee')!.value).toBe('7');
    expect(element.querySelector<HTMLInputElement>('#ticket-end')!.value).toBe('2026-10-02');
    params.next(convertToParamMap({ search: 'Anterior', prioridade: 'MEDIA' }));
    fixture.detectChanges();
    expect(element.querySelector<HTMLInputElement>('#ticket-search')!.value).toBe('Anterior');
    expect(element.querySelector<HTMLSelectElement>('#ticket-priority')!.value).toBe('MEDIA');
    expect(navigate).not.toHaveBeenCalled();
  });
  it('aplica seleção individual, combina campos e reinicia a página mantendo size/sort por merge', () => {
    const { element, navigate } = render();
    set(element, 'status', 'RESOLVIDO');
    expect(navigate).toHaveBeenLastCalledWith(
      [],
      expect.objectContaining({
        queryParamsHandling: 'merge',
        queryParams: expect.objectContaining({ page: 0, status: 'RESOLVIDO' }),
      }),
    );
    set(element, 'prioridade', 'ALTA');
    set(element, 'categoriaId', '2');
    set(element, 'responsavel', '7');
    set(element, 'dataInicial', '2026-10-01');
    set(element, 'dataFinal', '2026-10-02');
    set(element, 'search', '  Portal  ', 'input');
    element.querySelector('form')!.dispatchEvent(new Event('submit'));
    expect(navigate).toHaveBeenLastCalledWith(
      [],
      expect.objectContaining({
        queryParams: expect.objectContaining({
          page: 0,
          search: 'Portal',
          status: 'RESOLVIDO',
          prioridade: 'ALTA',
          categoriaId: '2',
          responsavel: '7',
          dataInicial: '2026-10-01',
          dataFinal: '2026-10-02',
        }),
      }),
    );
  });
  it('aguarda pausa de 350 ms e evita uma consulta a cada tecla', () => {
    vi.useFakeTimers();
    const { element, navigate } = render();
    set(element, 'search', 'p', 'input');
    vi.advanceTimersByTime(200);
    set(element, 'search', 'portal', 'input');
    vi.advanceTimersByTime(349);
    expect(navigate).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);
    expect(navigate).toHaveBeenCalledOnce();
  });
  it('reaplica o mesmo termo depois de limpar os filtros', () => {
    vi.useFakeTimers();
    const { element, navigate } = render();
    set(element, 'search', 'portal', 'input');
    vi.advanceTimersByTime(350);
    expect(navigate).toHaveBeenCalledTimes(1);
    params.next(convertToParamMap({ search: 'portal' }));
    element.querySelector<HTMLButtonElement>('.secondary-button')!.click();
    params.next(convertToParamMap({ page: '0', size: '20', sort: 'criadoEm,desc' }));
    set(element, 'search', 'portal', 'input');
    vi.advanceTimersByTime(350);
    expect(navigate).toHaveBeenCalledTimes(3);
    expect(navigate).toHaveBeenLastCalledWith(
      [],
      expect.objectContaining({
        queryParams: expect.objectContaining({ page: 0, search: 'portal' }),
      }),
    );
  });

  it('não reaplica busca pendente após restauração da URL', () => {
    vi.useFakeTimers();
    const { element, navigate } = render();
    set(element, 'search', 'obsoleto', 'input');
    params.next(convertToParamMap({ search: 'anterior', page: '2' }));
    vi.advanceTimersByTime(350);
    expect(navigate).not.toHaveBeenCalled();
  });
  it('limpa todos os filtros, restaurando página, tamanho e ordem padrão', () => {
    params.next(
      convertToParamMap({
        search: 'Portal',
        status: 'RESOLVIDO',
        responsavel: '7',
        page: '2',
        size: '50',
        sort: 'titulo,asc',
      }),
    );
    const { element, navigate } = render();
    element.querySelector<HTMLButtonElement>('.secondary-button')!.click();
    expect(navigate).toHaveBeenLastCalledWith(
      [],
      expect.objectContaining({
        queryParams: {
          page: 0,
          size: 20,
          sort: 'criadoEm,desc',
          categoria: null,
          search: null,
          status: null,
          prioridade: null,
          categoriaId: null,
          responsavel: null,
          dataInicial: null,
          dataFinal: null,
        },
      }),
    );
    expect(element.querySelector<HTMLInputElement>('#ticket-search')!.value).toBe('');
  });
  it('explica período invertido sem enviar consulta e recupera após correção', () => {
    const { fixture, element, navigate } = render();
    set(element, 'dataInicial', '2026-10-03');
    navigate.mockClear();
    set(element, 'dataFinal', '2026-10-01');
    fixture.detectChanges();
    expect(navigate).not.toHaveBeenCalled();
    expect(element.querySelector('[role="alert"]')!.textContent).toContain('data inicial');
    set(element, 'dataFinal', '2026-10-03');
    expect(navigate).toHaveBeenCalledOnce();
  });
  it('permite recarregar opções após erro', () => {
    service.listar.mockReturnValueOnce(throwError(() => new Error('offline')));
    const { fixture, element } = render();
    expect(element.textContent).toContain('Não foi possível carregar as opções');
    element.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    fixture.detectChanges();
    expect(element.textContent).toContain('Agente fictício');
    expect(element.querySelector('[role="alert"]')).toBeNull();
  });
});
