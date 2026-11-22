import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  Injector,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { AuthService } from '../../core/auth/auth.service';
import { PRIORIDADE_LABELS, STATUS_LABELS } from '../../shared/rotulos';
import { DashboardResumo, DashboardService } from './dashboard.service';

const STATUS = STATUS_LABELS;
const PRIORIDADE = PRIORIDADE_LABELS;

type EstadoDashboard =
  { tipo: 'carregando' } | { tipo: 'erro' } | { tipo: 'pronto'; resumo: DashboardResumo };

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardComponent {
  private readonly service = inject(DashboardService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly retryButton = viewChild<ElementRef<HTMLButtonElement>>('retryButton');
  private readonly currentUser = inject(AuthService).currentUser;

  protected readonly estado = signal<EstadoDashboard>({ tipo: 'carregando' });
  protected readonly numeros = new Intl.NumberFormat('pt-BR');
  protected readonly escopo = computed(() => {
    switch (this.currentUser()?.perfil) {
      case 'SOLICITANTE':
        return 'Chamados que você abriu';
      case 'ATENDENTE':
        return 'Chamados sob sua responsabilidade';
      case 'ADMIN':
        return 'Todos os chamados';
      default:
        return '';
    }
  });
  protected readonly graficos = computed(() => {
    const estado = this.estado();
    if (estado.tipo !== 'pronto' || estado.resumo.total === 0) return [];

    const { porStatus, porPrioridade, porCategoria } = estado.resumo;
    return [
      {
        id: 'status',
        titulo: 'Distribuição por status',
        grupos: porStatus.map(({ status, quantidade }) => ({
          id: status,
          nome: STATUS[status],
          quantidade,
        })),
      },
      {
        id: 'prioridade',
        titulo: 'Distribuição por prioridade',
        grupos: porPrioridade.map(({ prioridade, quantidade }) => ({
          id: prioridade,
          nome: PRIORIDADE[prioridade],
          quantidade,
        })),
      },
      { id: 'categoria', titulo: 'Distribuição por categoria', grupos: porCategoria },
    ];
  });

  constructor() {
    this.carregar();
  }

  protected tentarNovamente(): void {
    if (this.estado().tipo === 'erro') this.carregar(true);
  }

  private carregar(restaurarFoco = false): void {
    this.estado.set({ tipo: 'carregando' });
    this.service
      .resumir()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (resumo) => this.estado.set({ tipo: 'pronto', resumo }),
        error: () => {
          this.estado.set({ tipo: 'erro' });
          if (restaurarFoco) {
            afterNextRender(
              () => {
                const botao = this.retryButton()?.nativeElement;
                if (botao && botao.ownerDocument.activeElement === botao.ownerDocument.body) {
                  botao.focus();
                }
              },
              { injector: this.injector },
            );
          }
        },
      });
  }
}
