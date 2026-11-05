import { HttpParams } from '@angular/common/http';
import { ParamMap } from '@angular/router';
import { paginacaoDaUrl, Paginacao } from '../../shared/paginacao/paginacao.model';
import { Prioridade, StatusChamado } from './chamado.model';

export interface ConsultaChamados extends Paginacao {
  readonly search?: string;
  readonly status?: StatusChamado;
  readonly prioridade?: Prioridade;
  readonly categoriaId?: number;
  readonly responsavel?: number;
  readonly dataInicial?: string;
  readonly dataFinal?: string;
}
export const CAMPOS_FILTROS = [
  'search',
  'status',
  'prioridade',
  'categoriaId',
  'responsavel',
  'dataInicial',
  'dataFinal',
] as const;
export function consultaDaUrl(params: ParamMap): ConsultaChamados {
  const filters: Record<string, string | number> = {};
  for (const key of CAMPOS_FILTROS) {
    const value = params.get(key) ?? (key === 'categoriaId' ? params.get('categoria') : null);
    if (value !== null && value.trim() !== '')
      filters[key] =
        key === 'categoriaId' || key === 'responsavel'
          ? Number(value)
          : key === 'search'
            ? value.trim()
            : value;
  }
  return { ...paginacaoDaUrl(params), ...filters };
}
export function parametrosConsulta(query: ConsultaChamados): HttpParams {
  let params = new HttpParams()
    .set('page', query.page)
    .set('size', query.size)
    .set('sort', query.sort);
  for (const key of CAMPOS_FILTROS) {
    const value = query[key];
    if (value !== undefined && value !== '') params = params.set(key, value);
  }
  return params;
}
