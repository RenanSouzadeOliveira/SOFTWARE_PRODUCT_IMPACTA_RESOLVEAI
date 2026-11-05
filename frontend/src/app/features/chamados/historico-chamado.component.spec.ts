import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { ChamadosService } from './chamados.service';
import { HistoricoChamadoComponent } from './historico-chamado.component';
import { EventoHistorico } from './historico-chamado.model';

describe('HistoricoChamadoComponent', () => {
  const responses: Subject<readonly EventoHistorico[]>[] = [];
  const service = {
    listarHistorico: vi.fn((id: number) => {
      const response = new Subject<readonly EventoHistorico[]>();
      responses.push(response);
      return response.asObservable();
    }),
  };
  beforeEach(() => {
    responses.length = 0;
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      imports: [HistoricoChamadoComponent],
      providers: [{ provide: ChamadosService, useValue: service }],
    });
  });
  function render() {
    const fixture = TestBed.createComponent(HistoricoChamadoComponent);
    fixture.componentRef.setInput('chamadoId', 10);
    fixture.detectChanges();
    return fixture;
  }
  it('mostra carregamento e eventos mistos na ordem da API, com autor, data e valores seguros', () => {
    const fixture = render();
    expect(fixture.nativeElement.textContent).toContain('Carregando histórico');
    responses[0].next([
      {
        ...base,
        id: 'CHAMADO-3',
        tipoEvento: 'PRIORIDADE',
        criadoEm: '2026-11-05T13:00:00Z',
        valorAnterior: { codigo: 'MEDIA', nome: null },
        valorNovo: { codigo: 'ALTA', nome: null },
      },
      {
        ...base,
        id: 'STATUS-1',
        tipoEvento: 'STATUS',
        valorAnterior: { codigo: 'ABERTO', nome: null },
        valorNovo: { codigo: 'EM_ATENDIMENTO', nome: null },
      },
      {
        ...base,
        id: 'CHAMADO-2',
        tipoEvento: 'ATRIBUICAO',
        valorNovo: { codigo: '2', nome: 'Bia acadêmica' },
      },
      {
        ...base,
        id: 'CHAMADO-1',
        tipoEvento: 'CRIACAO',
        valorNovo: { codigo: 'ABERTO', nome: null },
        observacao: '<script>texto seguro</script>',
      },
    ]);
    fixture.detectChanges();
    const items = fixture.nativeElement.querySelectorAll('li') as NodeListOf<HTMLElement>;
    expect(items).toHaveLength(4);
    expect(items[0].textContent).toContain('Prioridade alterada');
    expect(items[0].textContent).toContain('Anterior: Média');
    expect(items[0].textContent).toContain('Novo: Alta');
    expect(items[1].textContent).toContain('Em atendimento');
    expect(items[2].textContent).toContain('Sem responsável');
    expect(items[2].textContent).toContain('Bia acadêmica');
    expect(items[3].textContent).toContain('Chamado criado');
    expect(items[3].textContent).not.toContain('Anterior:');
    expect(items[0].textContent).toContain('Ana acadêmica');
    expect(items[0].querySelector('time')?.getAttribute('datetime')).toBe('2026-11-05T13:00:00Z');
    expect(items[0].querySelector('time')?.textContent).toContain('05/11/2026');
    expect(fixture.nativeElement.querySelector('script')).toBeNull();
    expect(items[3].textContent).toContain('<script>texto seguro</script>');
  });
  it('mostra vazio legado e recarrega depois de alteração', () => {
    const fixture = render();
    responses[0].next([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nenhum evento registrado');
    fixture.componentRef.setInput('refreshKey', 1);
    fixture.detectChanges();
    expect(service.listarHistorico).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain('Carregando histórico');
    responses[1].next([base]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('li')).toHaveLength(1);
  });
  it('trata erro e permite nova tentativa com botão acessível', () => {
    const fixture = render();
    responses[0].error(new HttpErrorResponse({ status: 503 }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      'Não foi possível carregar o histórico',
    );
    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(button.type).toBe('button');
    button.click();
    fixture.detectChanges();
    expect(service.listarHistorico).toHaveBeenCalledTimes(2);
    responses[1].next([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nenhum evento registrado');
  });
  it('cancela busca anterior ao navegar e ao destruir', () => {
    const fixture = render();
    fixture.componentRef.setInput('chamadoId', 11);
    fixture.detectChanges();
    expect(responses[0].observed).toBe(false);
    responses[0].next([base]);
    responses[1].next([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Ana acadêmica');
    expect(service.listarHistorico).toHaveBeenLastCalledWith(11);
    fixture.destroy();
    expect(responses[1].observed).toBe(false);
  });
});
const base: EventoHistorico = {
  id: 'CHAMADO-1',
  chamadoId: 10,
  tipoEvento: 'CRIACAO',
  autor: { id: 1, nome: 'Ana acadêmica' },
  criadoEm: '2026-11-05T12:00:00Z',
  valorAnterior: null,
  valorNovo: { codigo: 'ABERTO', nome: null },
  observacao: null,
};
