import type { Perfil } from '../core/auth/auth.models';
import type { Prioridade, StatusChamado } from '../features/chamados/chamado.model';

// Rótulos exibidos ao usuário; os enums da API permanecem inalterados.
export const STATUS_LABELS: Readonly<Record<StatusChamado, string>> = {
  ABERTO: 'Aberto',
  EM_ATENDIMENTO: 'Em atendimento',
  RESOLVIDO: 'Resolvido',
  FECHADO: 'Fechado',
};

export const PRIORIDADE_LABELS: Readonly<Record<Prioridade, string>> = {
  BAIXA: 'Baixa',
  MEDIA: 'Média',
  ALTA: 'Alta',
};

export const PERFIL_LABELS: Readonly<Record<Perfil, string>> = {
  SOLICITANTE: 'Solicitante',
  ATENDENTE: 'Atendente',
  ADMIN: 'Administrador',
};

const TODOS: Readonly<Record<string, string>> = {
  ...STATUS_LABELS,
  ...PRIORIDADE_LABELS,
  ...PERFIL_LABELS,
};

/** Rótulo pt-BR de status, prioridade ou perfil; valores desconhecidos aparecem como recebidos. */
export function rotulo(valor: string): string {
  return TODOS[valor] ?? valor;
}
