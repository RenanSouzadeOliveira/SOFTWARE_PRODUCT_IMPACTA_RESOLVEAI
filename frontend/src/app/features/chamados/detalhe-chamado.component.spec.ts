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
