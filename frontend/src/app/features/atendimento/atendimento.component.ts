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
import { ActivatedRoute, ParamMap, Router, RouterLink, RouterLinkActive } from '@angular/router';
import {
  BehaviorSubject,
  catchError,
  combineLatest,
  distinctUntilChanged,
  EMPTY,
  map,
  switchMap,
  tap,
} from 'rxjs';
import { PaginacaoComponent } from '../../shared/paginacao/paginacao.component';
import { Paginacao } from '../../shared/paginacao/paginacao.model';

import { apiErrorMessage } from '../../core/http/api-error';
import { AuthService } from '../../core/auth/auth.service';
import type { Categoria } from '../chamados/chamado.model';
import type { AtendimentoResumo, FiltrosAtendimento, PaginaAtendimento } from './atendimento.model';
import { AtendimentoService } from './atendimento.service';

import { FiltrosChamadosComponent } from '../chamados/filtros-chamados.component';
import { consultaDaUrl } from '../chamados/filtros-chamados.model';

@Component({
  selector: 'app-atendimento',
  imports: [DatePipe, RouterLink, RouterLinkActive, PaginacaoComponent, FiltrosChamadosComponent],
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
  protected readonly todos = this.route.snapshot.data['todos'] === true;
  protected readonly auth = inject(AuthService);
  protected readonly query = signal<Paginacao>({ page: 0, size: 20, sort: 'criadoEm,desc' });
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
        map(([params, reload]) => ({ filters: this.filters(params), reload })),
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
        tap(({ filters }) => {
          this.query.set(filters);
          this.state.set('loading');
          this.error.set(null);
        }),
        switchMap(({ filters }) =>
          (this.todos ? this.service.listarTodos(filters) : this.service.listar(this.carteira, filters)).pipe(
            tap((pagina) => {
              if (pagina.page >= Math.max(pagina.totalPages, 1)) {
                this.navigateToPage(Math.max(pagina.totalPages - 1, 0));
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

  changeSettings(settings: Pick<Paginacao, 'size' | 'sort'>): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { ...settings, page: 0 },
      queryParamsHandling: 'merge',
    });
  }

  goToPage(page: number): void {
    const result = this.pagina();
    if (
      this.state() === 'loading' ||
      this.submitting() ||
      !result ||
      page === result.page ||
      page < 0 ||
      page >= result.totalPages
    )
      return;
    this.navigateToPage(page);
  }

  private navigateToPage(page: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page },
      queryParamsHandling: 'merge',
    });
  }

  refresh(): void {
    if (this.state() === 'loading') return;
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
    return consultaDaUrl(params);
  }
}
