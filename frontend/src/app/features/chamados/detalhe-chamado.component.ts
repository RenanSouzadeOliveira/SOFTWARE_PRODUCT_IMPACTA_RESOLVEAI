import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, EMPTY, map, merge, Subject, switchMap, tap } from 'rxjs';

import { apiErrorMessage } from '../../core/http/api-error';
import { Chamado, Prioridade, StatusChamado } from './chamado.model';
import { ChamadosService } from './chamados.service';

type DetalheChamadoState = 'loading' | 'success' | 'error';

const STATUS_LABELS: Readonly<Record<StatusChamado, string>> = {
  ABERTO: 'Aberto',
  EM_ATENDIMENTO: 'Em atendimento',
  RESOLVIDO: 'Resolvido',
  FECHADO: 'Fechado',
};

const PRIORIDADE_LABELS: Readonly<Record<Prioridade, string>> = {
  BAIXA: 'Baixa',
  MEDIA: 'Média',
  ALTA: 'Alta',
};

@Component({
  selector: 'app-detalhe-chamado',
  imports: [DatePipe, RouterLink],
  templateUrl: './detalhe-chamado.component.html',
  styleUrl: './detalhe-chamado.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetalheChamadoComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly chamadosService = inject(ChamadosService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reloadRequests = new Subject<number | null>();
  private chamadoId: number | null = null;

  protected readonly state = signal<DetalheChamadoState>('loading');
  protected readonly chamado = signal<Chamado | null>(null);
  protected readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    merge(
      this.route.paramMap.pipe(
        map((params) => parseId(params.get('id'))),
        tap((id) => (this.chamadoId = id)),
      ),
      this.reloadRequests,
    )
      .pipe(
        tap(() => {
          this.state.set('loading');
          this.chamado.set(null);
          this.errorMessage.set(null);
        }),
        switchMap((id) => {
          if (id === null) {
            this.state.set('error');
            this.errorMessage.set('O identificador do chamado é inválido.');
            return EMPTY;
          }

          return this.chamadosService.buscarPorId(id).pipe(
            tap((chamado) => {
              this.chamado.set(chamado);
              this.state.set('success');
            }),
            catchError((error: unknown) => {
              this.state.set('error');
              this.errorMessage.set(
                apiErrorMessage(error, 'Não foi possível carregar o chamado. Tente novamente.'),
              );
              return EMPTY;
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  protected load(): void {
    this.reloadRequests.next(this.chamadoId);
  }

  protected statusLabel(status: StatusChamado): string {
    return STATUS_LABELS[status];
  }

  protected prioridadeLabel(prioridade: Prioridade): string {
    return PRIORIDADE_LABELS[prioridade];
  }
}

function parseId(value: string | null): number | null {
  if (!value || !/^\d+$/.test(value)) {
    return null;
  }

  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}
