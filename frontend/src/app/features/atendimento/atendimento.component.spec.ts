import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { BehaviorSubject, of, Subject } from 'rxjs';
import { AtendimentoComponent } from './atendimento.component';
import type { AtendimentoResumo, PaginaAtendimento } from './atendimento.model';
import { AtendimentoService } from './atendimento.service';

describe('AtendimentoComponent', () => {
  const item: AtendimentoResumo = {
    id: 7,
    protocolo: 'RA-7',
    titulo: 'Sem acesso',
    categoria: { id: 1, nome: 'Acesso' },
    prioridade: 'ALTA',
    status: 'ABERTO',
    criadoEm: '2026-10-09T10:00:00Z',
    atualizadoEm: '2026-10-09T10:00:00Z',
    atribuidoEm: null,
    atendente: null,
  };
  const page = (content: readonly AtendimentoResumo[] = [item]): PaginaAtendimento => ({
    content,
    page: 0,
    size: 10,
    totalElements: content.length ? 11 : 0,
    totalPages: content.length ? 2 : 0,
  });
  let responses: Subject<PaginaAtendimento>[];
  let assignment: Subject<AtendimentoResumo>;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let carteira: boolean;
  const service = { listar: vi.fn(), assumir: vi.fn(), listarCategorias: vi.fn() };
  beforeEach(async () => {
    vi.clearAllMocks();
    responses = [];
    assignment = new Subject();
    params = new BehaviorSubject(convertToParamMap({}));
    carteira = false;
    service.listar.mockImplementation(() => {
      const response = new Subject<PaginaAtendimento>();
      responses.push(response);
      return response;
    });
    service.assumir.mockReturnValue(assignment);
    service.listarCategorias.mockReturnValue(
      of([
        { id: 1, nome: 'Acesso', ativa: true },
        { id: 2, nome: 'Legado', ativa: false },
      ]),
    );
    await TestBed.configureTestingModule({
      imports: [AtendimentoComponent],
      providers: [
        provideRouter([]),
        { provide: AtendimentoService, useValue: service },
        {
          provide: ActivatedRoute,
          useFactory: () => ({ snapshot: { data: { carteira } }, queryParamMap: params }),
        },
      ],
    }).compileComponents();
  });
  function render() {
    const fixture = TestBed.createComponent(AtendimentoComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('oferece categorias inativas no filtro de atendimento', () => {
    const fixture = render();
    const option = fixture.nativeElement.querySelector('#category option[value="2"]');
    expect(option.textContent).toContain('Legado (inativa)');
    expect(service.listarCategorias).toHaveBeenCalledOnce();
  });

  it('exibe carregamento, vazio e erro recuperável', () => {
    const fixture = render();
    expect(fixture.nativeElement.textContent).toContain('Carregando chamados');
    responses[0].error(new Error('offline'));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Não foi possível carregar');
    fixture.componentInstance.refresh();
    responses[1].next(page([]));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nenhum chamado disponível');
  });

  it('restaura filtros da URL e cancela respostas obsoletas', () => {
    params.next(convertToParamMap({ page: '2', prioridade: 'ALTA', categoriaId: '1' }));
    const fixture = render();
    expect(service.listar).toHaveBeenLastCalledWith(false, {
      page: 2,
      size: 10,
      prioridade: 'ALTA',
      categoriaId: 1,
      status: undefined,
    });
    params.next(convertToParamMap({ prioridade: 'MEDIA' }));
    expect(responses[0].observed).toBe(false);
    responses[1].next(page());
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#priority').value).toBe('MEDIA');
    expect(fixture.nativeElement.textContent).toContain('RA-7');
  });

  it('reinicia página ao filtrar e preserva filtros na paginação', () => {
    const fixture = render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.componentInstance.applyFilters();
    expect(navigate).toHaveBeenLastCalledWith(
      [],
      expect.objectContaining({
        queryParams: { page: 0, prioridade: null, categoriaId: null, status: null },
      }),
    );
    fixture.componentInstance.goToPage(1);
    expect(navigate).toHaveBeenLastCalledWith(
      [],
      expect.objectContaining({ queryParams: { page: 1 }, queryParamsHandling: 'merge' }),
    );
  });

  it('confirma antes de assumir, bloqueia duplicação e atualiza após sucesso', () => {
    const fixture = render();
    responses[0].next(page());
    fixture.detectChanges();
    fixture.nativeElement.querySelector('article button').click();
    fixture.detectChanges();
    expect(service.assumir).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).not.toBeNull();
    fixture.componentInstance.assumir();
    fixture.componentInstance.assumir();
    expect(service.assumir).toHaveBeenCalledExactlyOnceWith(7);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#cancel-assignment').disabled).toBe(true);
    assignment.next(item);
    fixture.detectChanges();
    expect(service.listar).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain('assumido com sucesso');
    expect(fixture.nativeElement.querySelector('.notice a').getAttribute('href')).toBe(
      '/atendimento/me',
    );
  });

  it('explica conflito e atualiza a fila', () => {
    const fixture = render();
    responses[0].next(page());
    fixture.detectChanges();
    fixture.nativeElement.querySelector('article button').click();
    fixture.componentInstance.assumir();
    assignment.error(new HttpErrorResponse({ status: 409 }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('já foi assumido');
    expect(service.listar).toHaveBeenCalledTimes(2);
  });

  it('permite cancelar e tentar novamente após falha de atribuição', () => {
    const fixture = render();
    responses[0].next(page());
    fixture.detectChanges();
    fixture.nativeElement.querySelector('article button').click();
    fixture.componentInstance.cancel();
    fixture.detectChanges();
    expect(service.assumir).not.toHaveBeenCalled();
    fixture.nativeElement.querySelector('article button').click();
    fixture.componentInstance.assumir();
    assignment.error(new Error('offline'));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Não foi possível assumir');
    expect(fixture.nativeElement.querySelector('article button').disabled).toBe(false);
  });

  it('consulta carteira e status sem oferecer atribuição', () => {
    carteira = true;
    params.next(convertToParamMap({ status: 'RESOLVIDO' }));
    const fixture = render();
    responses[0].next(page());
    fixture.detectChanges();
    expect(service.listar).toHaveBeenCalledWith(
      true,
      expect.objectContaining({ status: 'RESOLVIDO' }),
    );
    expect(fixture.nativeElement.querySelector('#status')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('article button')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Não registrada');
  });
});
