import type { Categoria, Prioridade, StatusChamado } from '../chamados/chamado.model';

export interface AtendimentoResumo {
  readonly id: number;
  readonly protocolo: string;
  readonly titulo: string;
  readonly categoria: Categoria;
  readonly prioridade: Prioridade;
  readonly status: StatusChamado;
  readonly criadoEm: string;
  readonly atualizadoEm: string;
  readonly atribuidoEm: string | null;
  readonly atendente: { readonly id: number; readonly nome: string } | null;
}

export interface PaginaAtendimento {
  readonly content: readonly AtendimentoResumo[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}

export interface FiltrosAtendimento {
  readonly page: number;
  readonly size: number;
  readonly prioridade?: Prioridade;
  readonly categoriaId?: number;
  readonly status?: StatusChamado;
}
