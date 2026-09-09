import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiService } from '../../core/http/api.service';
import { Categoria, Chamado, CriarChamadoInput } from './chamado.model';

@Injectable({ providedIn: 'root' })
export class ChamadosService {
  private readonly api = inject(ApiService);

  listarCategorias(): Observable<readonly Categoria[]> {
    return this.api.get<readonly Categoria[]>('categorias');
  }

  criar(input: CriarChamadoInput): Observable<Chamado> {
    return this.api.post<Chamado, CriarChamadoInput>('chamados', input);
  }
}
