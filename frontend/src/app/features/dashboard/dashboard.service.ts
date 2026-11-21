import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiService } from '../../core/http/api.service';
import { Categoria, Prioridade, StatusChamado } from '../chamados/chamado.model';

export interface DashboardResumo {
  readonly total: number;
  readonly abertos: number;
  readonly emAtendimento: number;
  readonly concluidos: number;
  readonly porStatus: readonly { status: StatusChamado; quantidade: number }[];
  readonly porPrioridade: readonly { prioridade: Prioridade; quantidade: number }[];
  readonly porCategoria: readonly (Pick<Categoria, 'id' | 'nome'> & { quantidade: number })[];
}

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly api = inject(ApiService);

  resumir(): Observable<DashboardResumo> {
    return this.api.get<DashboardResumo>('dashboard/resumo');
  }
}
