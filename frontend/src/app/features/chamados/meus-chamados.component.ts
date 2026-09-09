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
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { apiErrorMessage } from '../../core/http/api-error';
import { ChamadoResumo, Prioridade, StatusChamado } from './chamado.model';
import { ChamadosService } from './chamados.service';

type MeusChamadosState = 'loading' | 'empty' | 'success' | 'error';

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
  selector: 'app-meus-chamados',
  imports: [DatePipe, RouterLink],
  templateUrl: './meus-chamados.component.html',
  styleUrl: './meus-chamados.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MeusChamadosComponent implements OnInit {
  private readonly chamadosService = inject(ChamadosService);
  private readonly destroyRef = inject(DestroyRef);
  private requestInFlight = false;

  protected readonly state = signal<MeusChamadosState>('loading');
  protected readonly chamados = signal<readonly ChamadoResumo[]>([]);
  protected readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    if (this.requestInFlight) {
      return;
    }

    this.requestInFlight = true;
    this.state.set('loading');
    this.errorMessage.set(null);
    this.chamadosService
      .listarMeus()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => (this.requestInFlight = false)),
      )
      .subscribe({
        next: (chamados) => {
          this.chamados.set(chamados);
          this.state.set(chamados.length > 0 ? 'success' : 'empty');
        },
        error: (error: unknown) => {
          this.state.set('error');
          this.errorMessage.set(
            apiErrorMessage(error, 'Não foi possível carregar seus chamados. Tente novamente.'),
          );
        },
      });
  }

  protected statusLabel(status: StatusChamado): string {
    return STATUS_LABELS[status];
  }

  protected prioridadeLabel(prioridade: Prioridade): string {
    return PRIORIDADE_LABELS[prioridade];
  }
}
