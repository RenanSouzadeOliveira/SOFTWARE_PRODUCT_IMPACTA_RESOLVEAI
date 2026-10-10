import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/http/api.service';
import { ComentarioChamado, ComentariosChamado } from './comentarios-chamado.model';

@Injectable({ providedIn: 'root' })
export class ComentariosChamadoService {
  private readonly api = inject(ApiService);

  listar(id: number): Observable<ComentariosChamado> {
    return this.api.get<ComentariosChamado>(`chamados/${id}/comentarios`);
  }

  criar(id: number, conteudo: string): Observable<ComentarioChamado> {
    return this.api.post<ComentarioChamado, { conteudo: string }>(`chamados/${id}/comentarios`, {
      conteudo: conteudo.trim(),
    });
  }
}
