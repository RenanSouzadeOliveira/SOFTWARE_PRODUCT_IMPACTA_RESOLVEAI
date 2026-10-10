import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal,
  afterNextRender,
  Injector,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
} from '@angular/forms';
import { catchError, EMPTY, map, merge, Subject, switchMap, tap } from 'rxjs';

import { apiErrorMessage } from '../../core/http/api-error';
import {
  AcaoChamado,
  AlterarStatusInput,
  Chamado,
  Prioridade,
  StatusChamado,
} from './chamado.model';
import { ChamadosService } from './chamados.service';
import { ComentariosChamadoComponent } from './comentarios-chamado.component';

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
  imports: [DatePipe, RouterLink, ReactiveFormsModule, ComentariosChamadoComponent],
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
  protected readonly commentsRefresh = signal(0);
  private readonly injector = inject(Injector);
  private trigger: HTMLElement | null = null;
  protected readonly backRoute = this.route.snapshot?.data['backRoute'] ?? '/chamados';
  protected readonly backLabel =
    this.route.snapshot?.data['backLabel'] ?? 'Voltar para meus chamados';
  protected readonly selectedAction = signal<AcaoChamado | null>(null);
  protected readonly submitting = signal(false);
  protected readonly notice = signal<string | null>(null);
  protected readonly operationError = signal<string | null>(null);
  protected readonly prioridades: readonly Prioridade[] = ['BAIXA', 'MEDIA', 'ALTA'];
  protected readonly actionLabels: Readonly<Record<AcaoChamado, string>> = {
    RESOLVER: 'Resolver chamado',
    REABRIR: 'Solicitar reabertura',
    FECHAR: 'Confirmar resolução e fechar',
    ALTERAR_PRIORIDADE: 'Alterar prioridade',
  };
  protected readonly form = inject(NonNullableFormBuilder).group({
    justificativa: ['', trimmedJustification],
    prioridade: ['MEDIA' as Prioridade],
  });

  protected actions(): readonly AcaoChamado[] {
    const granted = this.chamado()?.acoesPermitidas;
    return (['RESOLVER', 'REABRIR', 'FECHAR', 'ALTERAR_PRIORIDADE'] as const).filter(
      (action) => Array.isArray(granted) && granted.includes(action),
    );
  }

  protected confirm(action: AcaoChamado, event: Event): void {
    if (this.submitting() || !this.actions().includes(action)) return;
    this.trigger = event.currentTarget as HTMLElement;
    this.form.reset({ justificativa: '', prioridade: this.chamado()?.prioridade ?? 'MEDIA' });
    this.operationError.set(null);
    this.notice.set(null);
    this.selectedAction.set(action);
    afterNextRender(() => document.getElementById('cancel-action')?.focus(), {
      injector: this.injector,
    });
  }

  protected cancel(): void {
    if (this.submitting()) return;
    this.selectedAction.set(null);
    this.operationError.set(null);
    this.trigger?.focus();
  }

  protected canSubmit(): boolean {
    const action = this.selectedAction();
    if (!action || this.submitting() || !this.actions().includes(action)) return false;
    if (action === 'ALTERAR_PRIORIDADE')
      return (
        this.prioridades.includes(this.form.controls.prioridade.value) &&
        this.form.controls.prioridade.value !== this.chamado()?.prioridade
      );
    return action === 'FECHAR' || this.form.controls.justificativa.valid;
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    const item = this.chamado();
    const action = this.selectedAction();
    if (!item || !action || !this.canSubmit()) return;
    this.submitting.set(true);
    this.operationError.set(null);
    const statuses: Readonly<
      Record<'RESOLVER' | 'REABRIR' | 'FECHAR', AlterarStatusInput['status']>
    > = { RESOLVER: 'RESOLVIDO', REABRIR: 'EM_ATENDIMENTO', FECHAR: 'FECHADO' };
    const request =
      action === 'ALTERAR_PRIORIDADE'
        ? this.chamadosService.alterarPrioridade(item.id, this.form.controls.prioridade.value)
        : this.chamadosService.alterarStatus(item.id, {
            status: statuses[action],
            ...(action === 'FECHAR'
              ? {}
              : { justificativa: this.form.controls.justificativa.value.trim() }),
          });
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (updated) => {
        this.submitting.set(false);
        if (this.chamadoId !== item.id) return;
        this.chamado.set(updated);
        this.commentsRefresh.update((value) => value + 1);
        this.selectedAction.set(null);
        this.notice.set('Chamado atualizado com sucesso.');
        afterNextRender(() => document.getElementById('operation-notice')?.focus(), {
          injector: this.injector,
        });
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        if (this.chamadoId !== item.id) return;
        if (error instanceof HttpErrorResponse && error.status === 409) {
          this.selectedAction.set(null);
          this.notice.set(
            'O chamado foi alterado. Confira os dados atualizados antes de tentar novamente.',
          );
          this.load();
        } else {
          this.operationError.set(
            apiErrorMessage(error, 'Não foi possível atualizar o chamado. Tente novamente.'),
          );
        }
      },
    });
  }

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
          this.selectedAction.set(null);
          this.operationError.set(null);
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

function trimmedJustification(control: AbstractControl<string>): ValidationErrors | null {
  const length = control.value.trim().length;
  return length >= 10 && length <= 500 ? null : { justificationLength: true };
}

function parseId(value: string | null): number | null {
  if (!value || !/^\d+$/.test(value)) {
    return null;
  }

  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}
