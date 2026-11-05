import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  Input,
  OnChanges,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, EMPTY, Subject, switchMap, tap } from 'rxjs';
import { apiErrorMessage } from '../../core/http/api-error';
import { ChamadosService } from './chamados.service';
import { EventoHistorico, TipoEventoHistorico, ValorHistorico } from './historico-chamado.model';

@Component({
  selector: 'app-historico-chamado',
  imports: [DatePipe],
  templateUrl: './historico-chamado.component.html',
  styleUrl: './historico-chamado.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HistoricoChamadoComponent implements OnChanges {
  @Input({ required: true }) chamadoId!: number;
  @Input() refreshKey = 0;
  private readonly service = inject(ChamadosService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reload = new Subject<number>();
  protected readonly state = signal<'loading' | 'success' | 'error'>('loading');
  protected readonly eventos = signal<readonly EventoHistorico[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly labels: Readonly<Record<TipoEventoHistorico, string>> = {
    CRIACAO: 'Chamado criado',
    STATUS: 'Status alterado',
    PRIORIDADE: 'Prioridade alterada',
    ATRIBUICAO: 'Responsável alterado',
  };
  private readonly values: Readonly<Record<string, string>> = {
    ABERTO: 'Aberto',
    EM_ATENDIMENTO: 'Em atendimento',
    RESOLVIDO: 'Resolvido',
    FECHADO: 'Fechado',
    BAIXA: 'Baixa',
    MEDIA: 'Média',
    ALTA: 'Alta',
  };

  constructor() {
    this.reload
      .pipe(
        tap(() => {
          this.state.set('loading');
          this.eventos.set([]);
          this.error.set(null);
        }),
        switchMap((id) =>
          this.service.listarHistorico(id).pipe(
            // A API entrega a ordem cronológica e o desempate estável entre as fontes AC2.
            tap((eventos) => {
              this.eventos.set(eventos);
              this.state.set('success');
            }),
            catchError((error: unknown) => {
              this.state.set('error');
              this.error.set(
                apiErrorMessage(error, 'Não foi possível carregar o histórico. Tente novamente.'),
              );
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  ngOnChanges(): void {
    this.refresh();
  }
  protected refresh(): void {
    this.reload.next(this.chamadoId);
  }
  protected valor(value: ValorHistorico | null, tipo: TipoEventoHistorico): string {
    if (tipo === 'ATRIBUICAO')
      return value?.nome ?? (value ? `Usuário #${value.codigo}` : 'Sem responsável');
    return value ? (this.values[value.codigo] ?? value.codigo) : 'Não registrado';
  }
}
