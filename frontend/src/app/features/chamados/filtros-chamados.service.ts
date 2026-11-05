import { inject, Injectable } from '@angular/core';
import { ApiService } from '../../core/http/api.service';
export interface OpcaoFiltro {
  readonly id: number;
  readonly nome: string;
  readonly ativa?: boolean;
}
export interface OpcoesFiltros {
  readonly categorias: readonly OpcaoFiltro[];
  readonly responsaveis: readonly OpcaoFiltro[];
}
@Injectable({ providedIn: 'root' })
export class FiltrosChamadosService {
  private readonly api = inject(ApiService);
  listar() {
    return this.api.get<OpcoesFiltros>('chamados/filtros');
  }
}
