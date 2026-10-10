import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApiService } from '../../core/http/api.service';
import { ComentariosChamadoService } from './comentarios-chamado.service';
import { ComentarioChamado, ComentariosChamado } from './comentarios-chamado.model';

describe('ComentariosChamadoService', () => {
  const api = { get: vi.fn(), post: vi.fn() };
  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
  });
  it('busca comentários e permissão no endpoint do chamado', () => {
    const page: ComentariosChamado = { content: [], podeComentar: false };
    api.get.mockReturnValue(of(page));
    let received: ComentariosChamado | undefined;
    TestBed.inject(ComentariosChamadoService)
      .listar(10)
      .subscribe((value) => (received = value));
    expect(api.get).toHaveBeenCalledWith('chamados/10/comentarios');
    expect(received).toBe(page);
  });
  it('envia somente conteúdo aparado e preserva autor retornado', () => {
    const comment: ComentarioChamado = {
      id: 1,
      conteudo: 'Teste',
      autor: { id: 2, nome: 'Ana', perfil: 'SOLICITANTE' },
      criadoEm: '2026-10-09T12:00:00Z',
    };
    api.post.mockReturnValue(of(comment));
    let received: ComentarioChamado | undefined;
    TestBed.inject(ComentariosChamadoService)
      .criar(10, '  Teste  ')
      .subscribe((value) => (received = value));
    expect(api.post).toHaveBeenCalledWith('chamados/10/comentarios', { conteudo: 'Teste' });
    expect(received).toBe(comment);
  });
});
