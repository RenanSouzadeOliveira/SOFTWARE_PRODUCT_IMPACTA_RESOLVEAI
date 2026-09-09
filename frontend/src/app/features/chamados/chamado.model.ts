import { Perfil } from '../../core/auth/auth.models';

export type StatusChamado = 'ABERTO' | 'EM_ATENDIMENTO' | 'RESOLVIDO' | 'FECHADO';

export type Prioridade = 'BAIXA' | 'MEDIA' | 'ALTA';

export interface Categoria {
  readonly id: number;
  readonly nome: string;
  readonly descricao?: string | null;
  readonly ativa?: boolean;
}

export interface UsuarioChamado {
  readonly id: number;
  readonly nome: string;
  readonly email?: string;
  readonly perfil?: Perfil;
}

export interface Chamado {
  readonly id: number;
  readonly protocolo: string;
  readonly titulo: string;
  readonly descricao: string;
  readonly status: StatusChamado;
  readonly prioridade: Prioridade;
  readonly solicitante: UsuarioChamado;
  readonly atendente: UsuarioChamado | null;
  readonly categoria: Categoria;
  readonly solucao?: string | null;
  readonly criadoEm: string;
  readonly atualizadoEm: string;
  readonly resolvidoEm?: string | null;
  readonly fechadoEm?: string | null;
  readonly versao?: number;
}

export interface CriarChamadoInput {
  readonly titulo: string;
  readonly descricao: string;
  readonly categoriaId: number;
  readonly prioridade: Prioridade;
}

export interface FieldViolation {
  readonly field: string;
  readonly message: string;
}
