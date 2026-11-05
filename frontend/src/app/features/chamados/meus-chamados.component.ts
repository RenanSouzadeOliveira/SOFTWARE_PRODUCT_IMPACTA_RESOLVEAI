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
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
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
import { Pagina, Paginacao } from '../../shared/paginacao/paginacao.model';

import { apiErrorMessage } from '../../core/http/api-error';
import { ChamadoResumo, Prioridade, StatusChamado } from './chamado.model';
import { ChamadosService } from './chamados.service';

import { FiltrosChamadosComponent } from './filtros-chamados.component';
import { consultaDaUrl } from './filtros-chamados.model';

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
  imports: [DatePipe, RouterLink, PaginacaoComponent, FiltrosChamadosComponent],
  templateUrl: './meus-chamados.component.html',
  styleUrl: './meus-chamados.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MeusChamadosComponent implements OnInit {
  private readonly chamadosService = inject(ChamadosService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly global = this.route.snapshot?.data?.['global'] === true;
  private readonly reload = new BehaviorSubject(0);
  protected readonly query = signal<Paginacao>({ page: 0, size: 20, sort: 'criadoEm,desc' });
  protected readonly pagina = signal<Pagina<ChamadoResumo> | null>(null);

  protected readonly state = signal<MeusChamadosState>('loading');
  protected readonly chamados = signal<readonly ChamadoResumo[]>([]);
  protected readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    combineLatest([this.route.queryParamMap, this.reload])
      .pipe(
        map(([params, reload]) => ({ query: consultaDaUrl(params), reload })),
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
        tap(({ query }) => {
          this.query.set(query);
          this.state.set('loading');
          this.errorMessage.set(null);
        }),
        switchMap(({ query }) =>
          (this.global
            ? this.chamadosService.listar(query)
            : this.chamadosService.listarMeus(query)
          ).pipe(
            tap((pagina) => {
              if (pagina.page >= Math.max(pagina.totalPages, 1)) {
                this.navigate({ page: Math.max(pagina.totalPages - 1, 0) });
                return;
              }
              this.pagina.set(pagina);
              this.chamados.set(pagina.content);
              this.state.set(pagina.content.length ? 'success' : 'empty');
            }),
            catchError((error: unknown) => {
              this.state.set('error');
              this.errorMessage.set(
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

  protected load(): void {
    if (this.state() !== 'loading') this.reload.next(this.reload.value + 1);
  }

  protected goToPage(page: number): void {
    const result = this.pagina();
    if (
      this.state() === 'loading' ||
      !result ||
      page === result.page ||
      page < 0 ||
      page >= result.totalPages
    )
      return;
    this.navigate({ page });
  }

  protected changeSettings(settings: Pick<Paginacao, 'size' | 'sort'>): void {
    this.navigate({ ...settings, page: 0 });
  }

  private navigate(queryParams: Partial<Paginacao>): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      queryParamsHandling: 'merge',
    });
  }

  protected statusLabel(status: StatusChamado): string {
    return STATUS_LABELS[status];
  }

  protected prioridadeLabel(prioridade: Prioridade): string {
    return PRIORIDADE_LABELS[prioridade];
  }
}
