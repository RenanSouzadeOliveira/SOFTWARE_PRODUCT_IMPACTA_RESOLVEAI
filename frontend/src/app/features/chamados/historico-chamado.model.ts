export type TipoEventoHistorico = 'CRIACAO' | 'STATUS' | 'PRIORIDADE' | 'ATRIBUICAO';

export interface ValorHistorico {
  readonly codigo: string;
  readonly nome: string | null;
}

export interface EventoHistorico {
  readonly id: string;
  readonly chamadoId: number;
  readonly tipoEvento: TipoEventoHistorico;
  readonly autor: { readonly id: number; readonly nome: string };
  readonly criadoEm: string;
  readonly valorAnterior: ValorHistorico | null;
  readonly valorNovo: ValorHistorico | null;
  readonly observacao: string | null;
}
