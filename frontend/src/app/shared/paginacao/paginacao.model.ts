import { ParamMap } from '@angular/router';

export interface Paginacao {
  readonly page: number;
  readonly size: number;
  readonly sort: string;
}
export interface Pagina<T> {
  readonly content: readonly T[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}
export const ORDENACOES = [
  { value: 'criadoEm,desc', label: 'Mais recentes' },
  { value: 'criadoEm,asc', label: 'Mais antigos' },
  { value: 'atualizadoEm,desc', label: 'Atualizados recentemente' },
  { value: 'titulo,asc', label: 'Título (A–Z)' },
  { value: 'titulo,desc', label: 'Título (Z–A)' },
  { value: 'status,asc', label: 'Status (A–Z)' },
  { value: 'prioridade,asc', label: 'Prioridade (A–Z)' },
  { value: 'id,desc', label: 'Identificador decrescente' },
] as const;
export function paginacaoDaUrl(params: ParamMap): Paginacao {
  const size = Number(params.get('size') ?? 20);
  const validSize = Number.isSafeInteger(size) && size >= 1 && size <= 100 ? size : 20;
  const page = Number(params.get('page') ?? 0);
  const sort = params.get('sort') ?? 'criadoEm,desc';
  return {
    page: Number.isSafeInteger(page) && page >= 0 && page * validSize <= 2147483647 ? page : 0,
    size: validSize,
    sort: /^(criadoEm|atualizadoEm|titulo|status|prioridade|id),(asc|desc)$/.test(sort)
      ? sort
      : 'criadoEm,desc',
  };
}
