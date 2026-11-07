import { EventoHistorico } from './historico-chamado.model';
import { inject, Injectable } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, from, mergeMap, throwError } from 'rxjs';
import { ConsultaChamados, parametrosConsulta } from './filtros-chamados.model';
import { Pagina } from '../../shared/paginacao/paginacao.model';

import { ApiService } from '../../core/http/api.service';
import {
  AlterarStatusInput,
  Categoria,
  Chamado,
  ChamadoResumo,
  CriarChamadoInput,
  Prioridade,
} from './chamado.model';

@Injectable({ providedIn: 'root' })
export class ChamadosService {
  private readonly api = inject(ApiService);

  listarCategorias(): Observable<readonly Categoria[]> {
    return this.api.get<readonly Categoria[]>('categorias');
  }

  criar(input: CriarChamadoInput): Observable<Chamado> {
    return this.api.post<Chamado, CriarChamadoInput>('chamados', input);
  }

  listarMeus(
    query: ConsultaChamados = { page: 0, size: 20, sort: 'criadoEm,desc' },
  ): Observable<Pagina<ChamadoResumo>> {
    const params = parametrosConsulta(query);
    return this.api.get<Pagina<ChamadoResumo>>('chamados/me', { params });
  }

  listar(query: ConsultaChamados): Observable<Pagina<ChamadoResumo>> {
    return this.api.get<Pagina<ChamadoResumo>>('chamados', { params: parametrosConsulta(query) });
  }

  exportarCsv(query: ConsultaChamados): Observable<Blob> {
    return this.api.getBlob('chamados/exportacao', { params: parametrosConsulta(query) }).pipe(
      catchError((error: unknown) => {
        // HttpClient também entrega erros JSON como Blob em uma requisição binária.
        if (error instanceof HttpErrorResponse && error.error instanceof Blob) {
          return from(error.error.text()).pipe(
            mergeMap((text) => {
              let body: unknown = null;
              try {
                body = JSON.parse(text);
              } catch {
                /* Usa mensagem genérica sanitizada. */
              }
              return throwError(
                () =>
                  new HttpErrorResponse({
                    error: body,
                    status: error.status,
                    statusText: error.statusText,
                    headers: error.headers,
                    url: error.url ?? undefined,
                  }),
              );
            }),
          );
        }
        return throwError(() => error);
      }),
    );
  }

  buscarPorId(id: number): Observable<Chamado> {
    return this.api.get<Chamado>(`chamados/${encodeURIComponent(String(id))}`);
  }

  listarHistorico(id: number): Observable<readonly EventoHistorico[]> {
    return this.api.get<readonly EventoHistorico[]>(
      `chamados/${encodeURIComponent(String(id))}/historico`,
    );
  }

  alterarStatus(id: number, input: AlterarStatusInput): Observable<Chamado> {
    return this.api.patch<Chamado, AlterarStatusInput>(`chamados/${id}/status`, input);
  }

  alterarPrioridade(id: number, prioridade: Prioridade): Observable<Chamado> {
    return this.api.patch<Chamado, { prioridade: Prioridade }>(`chamados/${id}/prioridade`, {
      prioridade,
    });
  }
}
