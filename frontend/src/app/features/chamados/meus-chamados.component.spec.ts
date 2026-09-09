import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';

import { ChamadoResumo } from './chamado.model';
import { ChamadosService } from './chamados.service';
import { MeusChamadosComponent } from './meus-chamados.component';

describe('MeusChamadosComponent', () => {
  const responses: Subject<readonly ChamadoResumo[]>[] = [];
  const chamadosService = {
    listarMeus: vi.fn(() => {
      const response = new Subject<readonly ChamadoResumo[]>();
      responses.push(response);
      return response.asObservable();
    }),
  };

  beforeEach(async () => {
    responses.length = 0;
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [MeusChamadosComponent],
      providers: [provideRouter([]), { provide: ChamadosService, useValue: chamadosService }],
    }).compileComponents();
  });

  it('mostra carregamento e os chamados recebidos com links para os detalhes', () => {
    const fixture = TestBed.createComponent(MeusChamadosComponent);
    fixture.detectChanges();

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[data-testid="chamados-loading"]'),
    ).not.toBeNull();

    responses[0].next([ticket]);
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
    responses[0].next([]);
    responses[0].complete();
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[data-testid="chamados-empty"]')).not.toBeNull();
    expect(element.querySelector('a[href="/chamados/novo"]')).not.toBeNull();
  });

  it('mostra erro e permite tentar novamente', () => {
    const first = new Subject<readonly ChamadoResumo[]>();
    const second = new Subject<readonly ChamadoResumo[]>();
    chamadosService.listarMeus.mockReturnValueOnce(first).mockReturnValueOnce(second);
    const fixture = TestBed.createComponent(MeusChamadosComponent);
    fixture.detectChanges();

    first.error(new Error('indisponível'));
    fixture.detectChanges();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[data-testid="chamados-error"]'),
    ).not.toBeNull();

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('.secondary-button')
      ?.click();
    expect(chamadosService.listarMeus).toHaveBeenCalledTimes(2);
    second.next([ticket]);
    second.complete();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('RA-2026-0001');
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
