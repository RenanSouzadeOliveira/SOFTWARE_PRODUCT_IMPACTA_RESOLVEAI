import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AtendimentoService } from './atendimento.service';
import { environment } from '../../../environments/environment';

describe('AtendimentoService', () => {
  let http: HttpTestingController;
  let service: AtendimentoService;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(AtendimentoService);
  });
  afterEach(() => http.verify());

  it('consulta categorias inclusive inativas pelo endpoint de atendimento', () => {
    let categories: readonly { id: number; nome: string; ativa?: boolean }[] = [];
    service.listarCategorias().subscribe((value) => (categories = value));
    const req = http.expectOne(`${environment.apiUrl}/atendimento/categorias`);
    req.flush([{ id: 2, nome: 'Legado', ativa: false }]);
    expect(categories[0].ativa).toBe(false);
  });

  it('envia paginação e filtros da fila sem filtro de status', () => {
    service
      .listar(false, { page: 2, size: 10, prioridade: 'ALTA', categoriaId: 3, status: 'FECHADO' })
      .subscribe();
    const req = http.expectOne((r) => r.url === `${environment.apiUrl}/atendimento/fila`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('prioridade')).toBe('ALTA');
    expect(req.request.params.get('categoriaId')).toBe('3');
    expect(req.request.params.has('status')).toBe(false);
    req.flush({ content: [], page: 2, size: 10, totalPages: 0, totalElements: 0 });
  });

  it('consulta a carteira com status e atribui sem enviar usuário', () => {
    service.listar(true, { page: 0, size: 10, status: 'EM_ATENDIMENTO' }).subscribe();
    const req = http.expectOne((r) => r.url.endsWith('/atendimento/me'));
    expect(req.request.params.get('status')).toBe('EM_ATENDIMENTO');
    req.flush({ content: [], page: 0, size: 10, totalPages: 0, totalElements: 0 });
    service.assumir(42).subscribe();
    const assignment = http.expectOne(`${environment.apiUrl}/chamados/42/assumir`);
    expect(assignment.request.method).toBe('PATCH');
    expect(assignment.request.body).toEqual({});
    assignment.flush({ id: 42 });
  });

  it('consulta todos os chamados com paginação e filtros combinados', () => {
    service
      .listarTodos({ page: 1, size: 10, status: 'FECHADO', prioridade: 'ALTA', categoriaId: 2 })
      .subscribe();
    const req = http.expectOne((r) => r.url === `${environment.apiUrl}/atendimento/todos`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('status')).toBe('FECHADO');
    expect(req.request.params.get('prioridade')).toBe('ALTA');
    expect(req.request.params.get('categoriaId')).toBe('2');
    req.flush({ content: [], page: 1, size: 10, totalPages: 0, totalElements: 0 });
  });
});
