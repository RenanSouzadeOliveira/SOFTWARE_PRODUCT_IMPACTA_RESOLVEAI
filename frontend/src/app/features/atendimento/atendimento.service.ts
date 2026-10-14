import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { HttpParams } from '@angular/common/http';

import { ApiService } from '../../core/http/api.service';
import type { AtendimentoResumo, FiltrosAtendimento, PaginaAtendimento } from './atendimento.model';
import type { Categoria } from '../chamados/chamado.model';

@Injectable({ providedIn: 'root' })
export class AtendimentoService {
  private readonly api = inject(ApiService);

  listarCategorias(): Observable<readonly Categoria[]> {
    return this.api.get<readonly Categoria[]>('atendimento/categorias');
  }

  listar(carteira: boolean, filtros: FiltrosAtendimento): Observable<PaginaAtendimento> {
    return this.consultar(carteira ? 'me' : 'fila', filtros);
  }

  listarTodos(filtros: FiltrosAtendimento): Observable<PaginaAtendimento> {
    return this.consultar('todos', filtros);
  }

  private consultar(
    area: 'fila' | 'me' | 'todos',
    filtros: FiltrosAtendimento,
  ): Observable<PaginaAtendimento> {
    let params = new HttpParams().set('page', filtros.page).set('size', filtros.size);
    if (filtros.prioridade) params = params.set('prioridade', filtros.prioridade);
    if (filtros.categoriaId) params = params.set('categoriaId', filtros.categoriaId);
    if (area !== 'fila' && filtros.status) params = params.set('status', filtros.status);
    return this.api.get<PaginaAtendimento>(`atendimento/${area}`, { params });
  }

  assumir(id: number): Observable<AtendimentoResumo> {
    return this.api.patch<AtendimentoResumo, Record<string, never>>(`chamados/${id}/assumir`, {});
  }
}
