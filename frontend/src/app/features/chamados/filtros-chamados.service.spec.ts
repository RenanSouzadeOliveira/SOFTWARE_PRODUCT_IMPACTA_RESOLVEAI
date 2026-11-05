import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { convertToParamMap } from '@angular/router';
import { ChamadosService } from './chamados.service';
import { FiltrosChamadosService } from './filtros-chamados.service';
import { consultaDaUrl } from './filtros-chamados.model';

describe('Contrato HTTP dos filtros', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('preserva todos os filtros da URL e codifica texto literal com segurança', () => {
    const query = consultaDaUrl(
      convertToParamMap({
        page: '2',
        size: '10',
        sort: 'titulo,asc',
        search: ' 100%_! & portal ',
        status: 'RESOLVIDO',
        prioridade: 'ALTA',
        categoria: '3',
        responsavel: '7',
        dataInicial: '2026-10-01',
        dataFinal: '2026-10-02',
      }),
    );
    TestBed.inject(ChamadosService).listar(query).subscribe();
    const request = http.expectOne((r) => r.url.endsWith('/chamados'));
    expect(request.request.params.get('search')).toBe('100%_! & portal');
    for (const [key, value] of Object.entries(query))
      expect(request.request.params.get(key)).toBe(String(value));
    expect(request.request.params.has('categoria')).toBe(false);
    request.flush({ content: [], page: 2, size: 10, totalElements: 0, totalPages: 0 });
  });
  it('consulta somente opções seguras do usuário autenticado', () => {
    TestBed.inject(FiltrosChamadosService).listar().subscribe();
    const request = http.expectOne((r) => r.url.endsWith('/chamados/filtros'));
    expect(request.request.method).toBe('GET');
    request.flush({ categorias: [], responsaveis: [] });
  });
});
