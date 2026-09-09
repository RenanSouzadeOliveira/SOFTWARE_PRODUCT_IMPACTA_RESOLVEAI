import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { finalize } from 'rxjs';

import { apiErrorMessage } from '../../core/http/api-error';
import { ChamadosService } from './chamados.service';
import {
  Categoria,
  Chamado,
  CriarChamadoInput,
  FieldViolation,
  Prioridade,
} from './chamado.model';

type CategoriasState = 'loading' | 'ready' | 'empty' | 'error';

interface NovoChamadoFormValue {
  readonly titulo: string;
  readonly descricao: string;
  readonly categoriaId: number;
  readonly prioridade: Prioridade | '';
}

@Component({
  selector: 'app-novo-chamado',
  imports: [ReactiveFormsModule],
  templateUrl: './novo-chamado.component.html',
  styleUrl: './novo-chamado.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NovoChamadoComponent implements OnInit {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly chamados = inject(ChamadosService);
  private readonly destroyRef = inject(DestroyRef);
  private categoriasRequestInFlight = false;

  protected readonly categorias = signal<readonly Categoria[]>([]);
  protected readonly categoriasState = signal<CategoriasState>('loading');
  protected readonly categoriasError = signal<string | null>(null);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly fieldViolations = signal<readonly FieldViolation[]>([]);
  protected readonly createdTicket = signal<Chamado | null>(null);

  protected readonly form = this.fb.group({
    titulo: ['', [Validators.required, trimmedLength(5, 120)]],
    descricao: ['', [Validators.required, trimmedLength(20, 2000)]],
    categoriaId: [0, [Validators.required, categoriaRequired()]],
    prioridade: ['' as Prioridade | '', [Validators.required]],
  });

  ngOnInit(): void {
    this.loadCategorias();
  }

  protected loadCategorias(): void {
    if (this.categoriasRequestInFlight) {
      return;
    }

    this.categoriasRequestInFlight = true;
    this.categoriasState.set('loading');
    this.categoriasError.set(null);
    this.chamados
      .listarCategorias()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .pipe(finalize(() => (this.categoriasRequestInFlight = false)))
      .subscribe({
        next: (categorias) => {
          this.categorias.set(categorias);
          this.categoriasState.set(categorias.length > 0 ? 'ready' : 'empty');
        },
        error: (error: unknown) => {
          this.categoriasState.set('error');
          this.categoriasError.set(
            apiErrorMessage(error, 'Não foi possível carregar as categorias. Tente novamente.'),
          );
        },
      });
  }

  protected submit(): void {
    if (this.submitting()) {
      return;
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const input = this.toCreateInput(this.form.getRawValue());
    if (!input) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);
    this.fieldViolations.set([]);
    this.chamados
      .criar(input)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.submitting.set(false)),
      )
      .subscribe({
        next: (chamado) => this.createdTicket.set(chamado),
        error: (error: unknown) => {
          this.errorMessage.set(
            apiErrorMessage(error, 'Não foi possível abrir o chamado. Tente novamente.'),
          );
          this.fieldViolations.set(readFieldViolations(error));
        },
      });
  }

  protected openAnother(): void {
    this.createdTicket.set(null);
    this.errorMessage.set(null);
    this.fieldViolations.set([]);
    this.form.reset({ titulo: '', descricao: '', categoriaId: 0, prioridade: '' });
    this.form.markAsPristine();
    this.form.markAsUntouched();
  }

  protected fieldViolationMessages(field: string): readonly string[] {
    return this.fieldViolations()
      .filter((violation) => violation.field === field)
      .map((violation) => violation.message);
  }

  private toCreateInput(value: NovoChamadoFormValue): CriarChamadoInput | null {
    if (!isPrioridade(value.prioridade) || !value.categoriaId) {
      return null;
    }

    return {
      titulo: value.titulo.trim(),
      descricao: value.descricao.trim(),
      categoriaId: value.categoriaId,
      prioridade: value.prioridade,
    };
  }
}

function isPrioridade(value: Prioridade | ''): value is Prioridade {
  return value === 'BAIXA' || value === 'MEDIA' || value === 'ALTA';
}

function categoriaRequired(): ValidatorFn {
  return (control: AbstractControl<number>): ValidationErrors | null =>
    control.value > 0 ? null : { required: true };
}

function trimmedLength(minLength: number, maxLength: number): ValidatorFn {
  return (control: AbstractControl<string>): ValidationErrors | null => {
    const length = control.value.trim().length;
    if (length === 0) {
      return null;
    }
    if (length < minLength) {
      return { minlength: { requiredLength: minLength, actualLength: length } };
    }
    if (length > maxLength) {
      return { maxlength: { requiredLength: maxLength, actualLength: length } };
    }
    return null;
  };
}

function readFieldViolations(error: unknown): readonly FieldViolation[] {
  if (!(error instanceof HttpErrorResponse) || !isRecord(error.error)) {
    return [];
  }

  const candidate = error.error['violations'] ?? error.error['fieldViolations'];
  if (!Array.isArray(candidate)) {
    return [];
  }

  return candidate.flatMap((item): FieldViolation[] => {
    if (!isRecord(item)) {
      return [];
    }

    const field = readString(item, 'field') ?? readString(item, 'campo');
    const message = readString(item, 'message') ?? readString(item, 'mensagem');
    return field && message ? [{ field, message }] : [];
  });
}

function readString(record: Record<string, unknown>, key: string): string | null {
  const value = record[key];
  return typeof value === 'string' && value.trim() ? value : null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}
