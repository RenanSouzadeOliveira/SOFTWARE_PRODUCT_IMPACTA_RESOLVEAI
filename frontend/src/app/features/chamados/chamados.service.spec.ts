import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { ApiService } from '../../core/http/api.service';
import { Categoria, Chamado, ChamadoResumo, CriarChamadoInput } from './chamado.model';
import { ChamadosService } from './chamados.service';

describe('ChamadosService', () => {
  const api = {
    get: vi.fn(),
    post: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
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
    api.get.mockReturnValue(of(chamados));

    let received: readonly ChamadoResumo[] | undefined;
    TestBed.inject(ChamadosService)
      .listarMeus()
      .subscribe((value) => (received = value));

    expect(api.get).toHaveBeenCalledWith('chamados/me');
    expect(received).toEqual(chamados);
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
