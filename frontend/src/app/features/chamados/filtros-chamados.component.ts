import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  input,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { debounceTime } from 'rxjs';
import { CAMPOS_FILTROS, consultaDaUrl } from './filtros-chamados.model';
import { FiltrosChamadosService, OpcaoFiltro, OpcoesFiltros } from './filtros-chamados.service';

@Component({
  selector: 'app-filtros-chamados',
  imports: [ReactiveFormsModule],
  templateUrl: './filtros-chamados.component.html',
  styleUrl: './filtros-chamados.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FiltrosChamadosComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly service = inject(FiltrosChamadosService);
  readonly categorias = input<readonly OpcaoFiltro[]>([]);
  readonly fila = input(false);
  protected readonly opcoes = signal<OpcoesFiltros>({ categorias: [], responsaveis: [] });
  protected readonly optionsError = signal(false);
  protected readonly form = inject(NonNullableFormBuilder).group({
    search: '',
    status: '',
    prioridade: '',
    categoriaId: '',
    responsavel: '',
    dataInicial: '',
    dataFinal: '',
  });
  private current: ReturnType<typeof consultaDaUrl> = { page: 0, size: 20, sort: 'criadoEm,desc' };
  protected readonly dateError = signal(false);
  ngOnInit(): void {
    this.loadOptions();
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const query = consultaDaUrl(params);
      this.current = query;
      const values = Object.fromEntries(
        CAMPOS_FILTROS.map((key) => [key, String(query[key] ?? '')]),
      );
      this.form.patchValue(values, { emitEvent: false });
      this.dateError.set(this.invalidRange());
    });
    // Busca só após pausa na digitação; demais controles aplicam no evento change.
    this.form.controls.search.valueChanges
      .pipe(debounceTime(350), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.apply());
  }
  protected loadOptions(): void {
    this.optionsError.set(false);
    this.service
      .listar()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (options) => this.opcoes.set(options),
        error: () => this.optionsError.set(true),
      });
  }
  protected apply(): void {
    this.dateError.set(this.invalidRange());
    if (this.dateError()) return;
    const values = this.form.getRawValue();
    const queryParams: Record<string, string | number | null> = { page: 0, categoria: null };
    for (const key of CAMPOS_FILTROS) queryParams[key] = values[key].trim() || null;
    // Não reinicia uma página restaurada se uma busca pendente já corresponde à URL.
    const current = this.current;
    if (
      CAMPOS_FILTROS.every((key) => String(current[key] ?? '') === String(queryParams[key] ?? ''))
    )
      return;
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      queryParamsHandling: 'merge',
    });
  }
  protected clear(): void {
    this.form.reset(undefined, { emitEvent: false });
    this.dateError.set(false);
    const queryParams: Record<string, string | number | null> = {
      page: 0,
      size: 20,
      sort: 'criadoEm,desc',
      categoria: null,
    };
    for (const key of CAMPOS_FILTROS) queryParams[key] = null;
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      queryParamsHandling: 'merge',
    });
  }
  private invalidRange(): boolean {
    const { dataInicial, dataFinal } = this.form.getRawValue();
    return !!dataInicial && !!dataFinal && dataInicial > dataFinal;
  }
}
