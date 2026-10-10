import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Subject } from 'rxjs';

import { Chamado } from './chamado.model';
import { ChamadosService } from './chamados.service';
import { DetalheChamadoComponent } from './detalhe-chamado.component';

describe('DetalheChamadoComponent', () => {
  const responses: Subject<Chamado>[] = [];
  const chamadosService = {
    alterarStatus: vi.fn(),
    alterarPrioridade: vi.fn(),
    buscarPorId: vi.fn(() => {
      const response = new Subject<Chamado>();
      responses.push(response);
      return response.asObservable();
    }),
  };

  beforeEach(async () => {
    responses.length = 0;
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [DetalheChamadoComponent],
      providers: [
        provideRouter([]),
        { provide: ChamadosService, useValue: chamadosService },
        {
          provide: ActivatedRoute,
          useValue: { paramMap: new Subject() },
        },
      ],
    }).compileComponents();
  });

  function render(item: Chamado = detail) {
    const params = TestBed.inject(ActivatedRoute).paramMap as Subject<
      ReturnType<typeof convertToParamMap>
    >;
    const fixture = TestBed.createComponent(DetalheChamadoComponent);
    fixture.detectChanges();
    params.next(convertToParamMap({ id: '10' }));
    responses[0].next(item);
    responses[0].complete();
    fixture.detectChanges();
    return fixture;
  }

  function open(fixture: ReturnType<typeof render>, action: string) {
    fixture.nativeElement.querySelector(`[data-action="${action}"]`).click();
    fixture.detectChanges();
  }

  function justify(fixture: ReturnType<typeof render>, text: string) {
    const textarea = fixture.nativeElement.querySelector('#justification') as HTMLTextAreaElement;
    textarea.value = text;
    textarea.dispatchEvent(new Event('input'));
    textarea.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
  }

  it('não oferece ações ausentes ou desconhecidas e usa histórico como texto seguro', () => {
    const fixture = render({
      ...detail,
      acoesPermitidas: ['UNKNOWN'],
      historicoStatus: [
        {
          id: 1,
          statusAnterior: null,
          statusNovo: 'ABERTO',
          autor: { id: 1, nome: 'Ana' },
          criadoEm: detail.criadoEm,
          observacao: '<script>malicioso</script>',
        },
      ],
    });
    expect(fixture.nativeElement.querySelector('[data-action]')).toBeNull();
    expect(fixture.nativeElement.querySelector('script')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('<script>malicioso</script>');
    expect(fixture.nativeElement.textContent).toContain('Nenhuma solução registrada');
  });

  it('valida justificativa aparada, cancela confirmação e impede envio duplicado', () => {
    const update = new Subject<Chamado>();
    chamadosService.alterarStatus.mockReturnValue(update);
    const fixture = render({ ...detail, acoesPermitidas: ['RESOLVER'] });
    open(fixture, 'RESOLVER');
    justify(fixture, '          abc         ');
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
    justify(fixture, 'x'.repeat(501));
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
    fixture.nativeElement.querySelector('#cancel-action').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
    open(fixture, 'RESOLVER');
    justify(fixture, '   Solução registrada   ');
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    fixture.detectChanges();
    expect(chamadosService.alterarStatus).toHaveBeenCalledExactlyOnceWith(10, {
      status: 'RESOLVIDO',
      justificativa: 'Solução registrada',
    });
    expect(fixture.nativeElement.querySelector('#cancel-action').disabled).toBe(true);
    update.next({ ...detail, status: 'RESOLVIDO', acoesPermitidas: ['FECHAR'] });
    update.complete();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-action="FECHAR"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-action="RESOLVER"]')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('atualizado com sucesso');
  });

  it('fecha sem justificativa e recupera erro sem perder a confirmação', () => {
    const update = new Subject<Chamado>();
    chamadosService.alterarStatus.mockReturnValue(update);
    const fixture = render({ ...detail, status: 'RESOLVIDO', acoesPermitidas: ['FECHAR'] });
    open(fixture, 'FECHAR');
    expect(fixture.nativeElement.textContent).toContain('fechamento é definitivo');
    expect(fixture.nativeElement.querySelector('textarea')).toBeNull();
    fixture.nativeElement.querySelector('button[type="submit"]').click();
    update.error(new HttpErrorResponse({ status: 500 }));
    fixture.detectChanges();
    expect(chamadosService.alterarStatus).toHaveBeenCalledWith(10, { status: 'FECHADO' });
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(false);
  });

  it('reabre com justificativa e limpa confirmação e ações obsoletas em conflito', () => {
    const update = new Subject<Chamado>();
    chamadosService.alterarStatus.mockReturnValue(update);
    const fixture = render({ ...detail, status: 'RESOLVIDO', acoesPermitidas: ['REABRIR'] });
    open(fixture, 'REABRIR');
    justify(fixture, 'Problema ainda ocorre');
    fixture.nativeElement.querySelector('button[type="submit"]').click();
    expect(chamadosService.alterarStatus).toHaveBeenCalledWith(10, {
      status: 'EM_ATENDIMENTO',
      justificativa: 'Problema ainda ocorre',
    });
    update.error(new HttpErrorResponse({ status: 409 }));
    fixture.detectChanges();
    expect(chamadosService.buscarPorId).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-action]')).toBeNull();
    responses[1].next({ ...detail, status: 'FECHADO', acoesPermitidas: [] });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Fechado');
    expect(fixture.nativeElement.textContent).toContain('Confira os dados atualizados');
  });

  it('exige prioridade diferente, com controle rotulado e atualiza o detalhe', () => {
    const update = new Subject<Chamado>();
    chamadosService.alterarPrioridade.mockReturnValue(update);
    const fixture = render({ ...detail, acoesPermitidas: ['ALTERAR_PRIORIDADE'] });
    open(fixture, 'ALTERAR_PRIORIDADE');
    expect(fixture.nativeElement.querySelector('label[for="new-priority"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
    const select = fixture.nativeElement.querySelector('#new-priority') as HTMLSelectElement;
    select.value = 'ALTA';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    fixture.nativeElement.querySelector('button[type="submit"]').click();
    expect(chamadosService.alterarPrioridade).toHaveBeenCalledWith(10, 'ALTA');
    update.next({ ...detail, prioridade: 'ALTA', acoesPermitidas: [] });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Alta');
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
  });

  it('carrega o identificador da rota e mostra os detalhes', () => {
    const params = TestBed.inject(ActivatedRoute).paramMap as Subject<
      ReturnType<typeof convertToParamMap>
    >;
    const fixture = TestBed.createComponent(DetalheChamadoComponent);
    fixture.detectChanges();
    params.next(convertToParamMap({ id: '10' }));
    fixture.detectChanges();

    expect(chamadosService.buscarPorId).toHaveBeenCalledWith(10);
    expect(
      (fixture.nativeElement as HTMLElement).querySelector(
        '[data-testid="chamado-detail-loading"]',
      ),
    ).not.toBeNull();

    responses[0].next(detail);
    responses[0].complete();
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[data-testid="chamado-detail-success"]')).not.toBeNull();
    expect(element.textContent).toContain('RA-2026-0001');
    expect(element.textContent).toContain('Descrição completa do problema de acesso.');
    expect(element.querySelector('a[href="/chamados"]')).not.toBeNull();
  });

  it('mostra erro e permite repetir a busca', () => {
    const params = TestBed.inject(ActivatedRoute).paramMap as Subject<
      ReturnType<typeof convertToParamMap>
    >;
    const first = new Subject<Chamado>();
    const second = new Subject<Chamado>();
    chamadosService.buscarPorId.mockReturnValueOnce(first).mockReturnValueOnce(second);
    const fixture = TestBed.createComponent(DetalheChamadoComponent);
    fixture.detectChanges();
    params.next(convertToParamMap({ id: '10' }));
    first.error(new HttpErrorResponse({ status: 503, error: { message: 'Serviço indisponível' } }));
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[data-testid="chamado-detail-error"]')?.textContent).toContain(
      'Serviço indisponível',
    );
    element.querySelector<HTMLButtonElement>('.secondary-button')?.click();
    expect(chamadosService.buscarPorId).toHaveBeenCalledTimes(2);

    second.next(detail);
    second.complete();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('RA-2026-0001');
  });

  it('cancela a resposta anterior quando o identificador da rota muda', () => {
    const params = TestBed.inject(ActivatedRoute).paramMap as Subject<
      ReturnType<typeof convertToParamMap>
    >;
    const fixture = TestBed.createComponent(DetalheChamadoComponent);
    fixture.detectChanges();

    params.next(convertToParamMap({ id: '10' }));
    params.next(convertToParamMap({ id: '11' }));

    expect(chamadosService.buscarPorId).toHaveBeenNthCalledWith(1, 10);
    expect(chamadosService.buscarPorId).toHaveBeenNthCalledWith(2, 11);

    responses[0].next(detail);
    responses[0].complete();
    responses[1].next({ ...detail, id: 11, protocolo: 'RA-2026-0002', titulo: 'Chamado atual' });
    responses[1].complete();
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('RA-2026-0002');
    expect(element.textContent).not.toContain('RA-2026-0001');
  });
});

const detail: Chamado = {
  id: 10,
  protocolo: 'RA-2026-0001',
  titulo: 'Falha de acesso',
  descricao: 'Descrição completa do problema de acesso.',
  status: 'ABERTO',
  prioridade: 'MEDIA',
  solicitante: { id: 1, nome: 'Ana', email: 'ana@example.com', perfil: 'SOLICITANTE' },
  atendente: null,
  categoria: { id: 1, nome: 'Acesso' },
  solucao: null,
  criadoEm: '2026-09-09T12:00:00Z',
  atualizadoEm: '2026-09-09T12:00:00Z',
  resolvidoEm: null,
  fechadoEm: null,
};
