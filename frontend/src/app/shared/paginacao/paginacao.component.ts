import { ChangeDetectionStrategy, Component, effect, input, output } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ORDENACOES, Pagina, Paginacao } from './paginacao.model';

@Component({
  selector: 'app-paginacao',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="controls">
      <label
        >Itens por página
        <select
          aria-label="Itens por página"
          [formControl]="sizeControl"
          (change)="changeSize($event)"
        >
          @for (size of sizes(); track size) {
            <option [value]="size">{{ size }}</option>
          }
        </select>
      </label>
      <label
        >Ordenar por
        <select aria-label="Ordenar por" [formControl]="sortControl" (change)="changeSort($event)">
          @for (sort of sorts; track sort.value) {
            <option [value]="sort.value">{{ sort.label }}</option>
          }
          @if (!knownSort()) {
            <option [value]="query().sort">{{ customSortLabel() }}</option>
          }
        </select>
      </label>
    </div>
    <nav class="controls" aria-label="Paginação de chamados">
      <button
        type="button"
        [disabled]="disabled() || !result() || result()!.page === 0"
        (click)="pageChange.emit(result()!.page - 1)"
      >
        Anterior
      </button>
      <span role="status"
        >{{ result()?.totalElements ?? 0 }} chamado(s) · Página {{ (result()?.page ?? 0) + 1 }} de
        {{ result()?.totalPages || 1 }}</span
      >
      <button
        type="button"
        [disabled]="disabled() || !result() || result()!.page + 1 >= result()!.totalPages"
        (click)="pageChange.emit(result()!.page + 1)"
      >
        Próxima
      </button>
    </nav>
  `,
  styles: `
    .controls {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.75rem;
      margin-block: 1rem;
    }
    label {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.5rem;
    }
    button,
    select {
      font: inherit;
      padding: 0.65rem;
      min-height: 2.75rem;
      max-width: 100%;
      border: 1px solid #9fb3c8;
      border-radius: 0.5rem;
      background: white;
      color: #243b53;
    }
    button:disabled,
    select:disabled {
      opacity: 0.55;
      cursor: default;
    }
    :focus-visible {
      outline: 3px solid #0967d2;
      outline-offset: 2px;
    }
  `,
})
export class PaginacaoComponent {
  readonly query = input.required<Paginacao>();
  readonly result = input<Pagina<unknown> | null>(null);
  readonly disabled = input(false);
  readonly pageChange = output<number>();
  readonly settingsChange = output<Pick<Paginacao, 'size' | 'sort'>>();
  protected readonly sorts = ORDENACOES;
  protected readonly sizeControl = new FormControl(20, { nonNullable: true });
  protected readonly sortControl = new FormControl('criadoEm,desc', { nonNullable: true });

  constructor() {
    effect(() => {
      this.sizeControl.setValue(this.query().size, { emitEvent: false });
      this.sortControl.setValue(this.query().sort, { emitEvent: false });
      for (const control of [this.sizeControl, this.sortControl]) {
        if (this.disabled()) control.disable({ emitEvent: false });
        else control.enable({ emitEvent: false });
      }
    });
  }
  protected sizes(): number[] {
    return [...new Set([20, 50, 100, this.query().size])].sort((a, b) => a - b);
  }
  protected knownSort(): boolean {
    return this.sorts.some((s) => s.value === this.query().sort);
  }
  protected customSortLabel(): string {
    const [field, direction] = this.query().sort.split(',');
    const labels: Record<string, string> = {
      criadoEm: 'Criação',
      atualizadoEm: 'Atualização',
      titulo: 'Título',
      status: 'Status',
      prioridade: 'Prioridade',
      id: 'Identificador',
    };
    return `${labels[field]} (${direction === 'asc' ? 'crescente' : 'decrescente'})`;
  }
  protected changeSize(event: Event): void {
    const size = Number((event.target as HTMLSelectElement).value);
    if (size !== this.query().size) this.settingsChange.emit({ size, sort: this.query().sort });
  }
  protected changeSort(event: Event): void {
    const sort = (event.target as HTMLSelectElement).value;
    if (sort !== this.query().sort) this.settingsChange.emit({ size: this.query().size, sort });
  }
}
