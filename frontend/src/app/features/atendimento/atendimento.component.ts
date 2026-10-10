import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  Injector,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, ParamMap, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { BehaviorSubject, catchError, combineLatest, EMPTY, switchMap, tap } from 'rxjs';

import { apiErrorMessage } from '../../core/http/api-error';
import { AuthService } from '../../core/auth/auth.service';
import type { Categoria, Prioridade, StatusChamado } from '../chamados/chamado.model';
import type { AtendimentoResumo, FiltrosAtendimento, PaginaAtendimento } from './atendimento.model';
import { AtendimentoService } from './atendimento.service';

@Component({
  selector: 'app-atendimento',
  imports: [DatePipe, ReactiveFormsModule, RouterLink, RouterLinkActive],
  templateUrl: './atendimento.component.html',
  styleUrl: './atendimento.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AtendimentoComponent implements OnInit {
  private readonly service = inject(AtendimentoService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly reload = new BehaviorSubject(0);
  private trigger: HTMLElement | null = null;
  protected readonly carteira = this.route.snapshot.data['carteira'] === true;
  protected readonly auth = inject(AuthService);
  protected readonly prioridades: readonly Prioridade[] = ['BAIXA', 'MEDIA', 'ALTA'];
  protected readonly statuses: readonly StatusChamado[] = [
    'ABERTO',
    'EM_ATENDIMENTO',
    'RESOLVIDO',
    'FECHADO',
  ];
  protected readonly form = inject(NonNullableFormBuilder).group({
    prioridade: '',
    categoriaId: '',
    status: '',
  });
  protected readonly state = signal<'loading' | 'success' | 'error'>('loading');
  protected readonly pagina = signal<PaginaAtendimento | null>(null);
  protected readonly categorias = signal<readonly Categoria[]>([]);
  protected readonly categoryError = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly succeeded = signal(false);
  protected readonly selected = signal<AtendimentoResumo | null>(null);
  protected readonly submitting = signal(false);

  ngOnInit(): void {
    this.loadCategories();
    combineLatest([this.route.queryParamMap, this.reload])
      .pipe(
        tap(([params]) => {
          const filters = this.filters(params);
          this.form.setValue({
            prioridade: filters.prioridade ?? '',
            categoriaId: filters.categoriaId?.toString() ?? '',
            status: filters.status ?? '',
          });
          this.state.set('loading');
          this.error.set(null);
        }),
        switchMap(([params]) =>
          this.service.listar(this.carteira, this.filters(params)).pipe(
            tap((pagina) => {
              if (pagina.page >= Math.max(pagina.totalPages, 1)) {
                this.goToPage(Math.max(pagina.totalPages - 1, 0));
              } else {
                this.pagina.set(pagina);
                this.state.set('success');
              }
            }),
            catchError((error: unknown) => {
              this.state.set('error');
              this.error.set(
                apiErrorMessage(error, 'Não foi possível carregar os chamados. Tente novamente.'),
              );
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  loadCategories(): void {
    this.categoryError.set(false);
    this.service
      .listarCategorias()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (categorias) => this.categorias.set(categorias),
        error: () => this.categoryError.set(true),
      });
  }

  applyFilters(): void {
    const values = this.form.getRawValue();
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        page: 0,
        prioridade: values.prioridade || null,
        categoriaId: values.categoriaId || null,
        status: this.carteira ? values.status || null : null,
      },
    });
  }

  goToPage(page: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page },
      queryParamsHandling: 'merge',
    });
  }

  refresh(): void {
    this.reload.next(this.reload.value + 1);
  }

  confirm(item: AtendimentoResumo, event: Event): void {
    if (this.submitting()) return;
    this.trigger = event.currentTarget as HTMLElement;
    this.selected.set(item);
    afterNextRender(() => document.getElementById('cancel-assignment')?.focus(), {
      injector: this.injector,
    });
  }

  cancel(): void {
    if (this.submitting()) return;
    this.selected.set(null);
    this.trigger?.focus();
  }

  assumir(): void {
    const item = this.selected();
    if (!item || this.submitting()) return;
    this.submitting.set(true);
    this.notice.set(null);
    this.succeeded.set(false);
    this.service
      .assumir(item.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.finishAssignment();
          this.succeeded.set(true);
          this.notice.set(`Chamado ${item.protocolo} assumido com sucesso.`);
          this.refresh();
        },
        error: (error: unknown) => {
          this.finishAssignment();
          const conflict = error instanceof HttpErrorResponse && error.status === 409;
          this.notice.set(
            conflict
              ? 'Este chamado já foi assumido ou não está mais disponível. A fila foi atualizada.'
              : apiErrorMessage(error, 'Não foi possível assumir o chamado. Tente novamente.'),
          );
          if (conflict) this.refresh();
        },
      });
  }

  label(value: string): string {
    return (
      (
        {
          BAIXA: 'Baixa',
          MEDIA: 'Média',
          ALTA: 'Alta',
          ABERTO: 'Aberto',
          EM_ATENDIMENTO: 'Em atendimento',
          RESOLVIDO: 'Resolvido',
          FECHADO: 'Fechado',
        } as Record<string, string>
      )[value] ?? value
    );
  }

  private finishAssignment(): void {
    this.submitting.set(false);
    this.selected.set(null);
    this.trigger?.focus();
  }

  private filters(params: ParamMap): FiltrosAtendimento {
    const page = Number(params.get('page') ?? 0);
    const categoria = Number(params.get('categoriaId'));
    const prioridade = params.get('prioridade') as Prioridade;
    const status = params.get('status') as StatusChamado;
    return {
      page: Number.isSafeInteger(page) && page >= 0 ? page : 0,
      size: 10,
      prioridade: this.prioridades.includes(prioridade) ? prioridade : undefined,
      categoriaId: Number.isSafeInteger(categoria) && categoria > 0 ? categoria : undefined,
      status: this.carteira && this.statuses.includes(status) ? status : undefined,
    };
  }
}
