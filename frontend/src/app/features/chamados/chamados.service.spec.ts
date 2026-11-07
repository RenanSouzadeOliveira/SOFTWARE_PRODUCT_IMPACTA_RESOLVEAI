import { Pagina } from '../../shared/paginacao/paginacao.model';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom, of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

import { ApiService } from '../../core/http/api.service';
import { Categoria, Chamado, ChamadoResumo, CriarChamadoInput } from './chamado.model';
import { ChamadosService } from './chamados.service';

describe('ChamadosService', () => {
  const api = {
    get: vi.fn(),
    getBlob: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
  });

  it('envia status e justificativa e preserva detalhe retornado pela API', () => {
    const updated: Chamado = {
      id: 10,
      protocolo: 'RA-10',
      titulo: 'Sem acesso',
      descricao: 'Descrição do problema',
      status: 'RESOLVIDO',
      prioridade: 'MEDIA',
      categoria: { id: 1, nome: 'Acesso' },
      solicitante: { id: 1, nome: 'Ana' },
      atendente: null,
      criadoEm: '2026-10-09T12:00:00Z',
      atualizadoEm: '2026-10-09T12:00:00Z',
      acoesPermitidas: ['FECHAR'],
    };
    api.patch.mockReturnValue(of(updated));
    let received: Chamado | undefined;
    TestBed.inject(ChamadosService)
      .alterarStatus(10, { status: 'RESOLVIDO', justificativa: 'Solução registrada' })
      .subscribe((value) => (received = value));
    expect(api.patch).toHaveBeenCalledWith('chamados/10/status', {
      status: 'RESOLVIDO',
      justificativa: 'Solução registrada',
    });
    expect(received).toBe(updated);
  });

  it('consulta histórico pelo endpoint do chamado sem enviar autor', () => {
    api.get.mockReturnValue(of([]));
    TestBed.inject(ChamadosService)
      .listarHistorico(10)
      .subscribe((value) => expect(value).toEqual([]));
    expect(api.get).toHaveBeenCalledWith('chamados/10/historico');
  });

  it('envia prioridade sem campos de usuário ou status', () => {
    api.patch.mockReturnValue(of({}));
    TestBed.inject(ChamadosService).alterarPrioridade(10, 'ALTA').subscribe();
    expect(api.patch).toHaveBeenCalledWith('chamados/10/prioridade', { prioridade: 'ALTA' });
  });

  it('carrega categorias pelo endpoint autenticado', () => {
    const categorias: readonly Categoria[] = [{ id: 1, nome: 'Acesso' }];
    api.get.mockReturnValue(of(categorias));

    let received: readonly Categoria[] | undefined;
    TestBed.inject(ChamadosService)
      .listarCategorias()
      .subscribe((value) => (received = value));

    expect(api.get).toHaveBeenCalledWith('categorias');
    expect(received).toEqual(categorias);
  });

  it('envia somente o contrato público de abertura de chamado', () => {
    const input: CriarChamadoInput = {
      titulo: 'Sem acesso',
      descricao: 'A conta não consegue acessar o portal acadêmico.',
      categoriaId: 1,
      prioridade: 'ALTA',
    };
    const response = {} as Chamado;
    api.post.mockReturnValue(of(response));

    TestBed.inject(ChamadosService).criar(input).subscribe();

    expect(api.post).toHaveBeenCalledWith('chamados', input);
  });

  it('lista os chamados do solicitante pelo endpoint protegido', () => {
    const chamados: readonly ChamadoResumo[] = [
      {
        id: 10,
        protocolo: 'RA-10',
        titulo: 'Sem acesso',
        status: 'ABERTO',
        prioridade: 'MEDIA',
        categoria: { id: 1, nome: 'Acesso' },
        criadoEm: '2026-09-09T12:00:00Z',
        atualizadoEm: '2026-09-09T12:00:00Z',
        resolvidoEm: null,
        fechadoEm: null,
      },
    ];
    const pagina: Pagina<ChamadoResumo> = {
      content: chamados,
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    };
    api.get.mockReturnValue(of(pagina));

    let received: Pagina<ChamadoResumo> | undefined;
    TestBed.inject(ChamadosService)
      .listarMeus()
      .subscribe((value) => (received = value));

    expect(api.get).toHaveBeenCalledWith(
      'chamados/me',
      expect.objectContaining({ params: expect.anything() }),
    );
    const params = api.get.mock.calls[0][1].params;
    expect(params.get('page')).toBe('0');
    expect(params.get('size')).toBe('20');
    expect(params.get('sort')).toBe('criadoEm,desc');
    expect(received).toEqual(pagina);
  });

  it('exporta todos os filtros, paginação e ordenação pelo serializador existente', () => {
    const blob = new Blob(['CSV']);
    api.getBlob.mockReturnValue(of(blob));
    const query = {
      page: 2,
      size: 1,
      sort: 'titulo,asc',
      search: 'ação',
      status: 'ABERTO' as const,
      prioridade: 'ALTA' as const,
      categoriaId: 3,
      responsavel: 7,
      dataInicial: '2026-09-01',
      dataFinal: '2026-09-30',
    };
    TestBed.inject(ChamadosService)
      .exportarCsv(query)
      .subscribe((result) => expect(result).toBe(blob));
    expect(api.getBlob).toHaveBeenCalledWith('chamados/exportacao', expect.anything());
    const params = api.getBlob.mock.calls[0][1].params;
    for (const [key, value] of Object.entries(query)) expect(params.get(key)).toBe(String(value));
  });

  it('decodifica erro JSON em Blob e preserva status para a interface', async () => {
    const body = new Blob([JSON.stringify({ message: 'A exportação permite até 105 chamados.' })], {
      type: 'application/json',
    });
    // jsdom pode não oferecer Blob.text; simula a API nativa do navegador neste teste.
    Object.defineProperty(body, 'text', {
      value: () => Promise.resolve('{"message":"A exportação permite até 105 chamados."}'),
    });
    api.getBlob.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 400, error: body })),
    );
    await expect(
      firstValueFrom(
        TestBed.inject(ChamadosService).exportarCsv({ page: 0, size: 20, sort: 'id,asc' }),
      ),
    ).rejects.toMatchObject({
      status: 400,
      error: { message: 'A exportação permite até 105 chamados.' },
    });
  });

  it('busca o detalhe pelo identificador do chamado', () => {
    const response = {} as Chamado;
    api.get.mockReturnValue(of(response));

    let received: Chamado | undefined;
    TestBed.inject(ChamadosService)
      .buscarPorId(42)
      .subscribe((value) => (received = value));

    expect(api.get).toHaveBeenCalledWith('chamados/42');
    expect(received).toBe(response);
  });
});
