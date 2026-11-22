import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  Injector,
  OnInit,
  signal,
  viewChild,
} from '@angular/core';
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

import { apiErrorMessage, readFieldViolations } from '../../core/http/api-error';
import { ChamadosService } from './chamados.service';
import { Categoria, Chamado, CriarChamadoInput, FieldViolation, Prioridade } from './chamado.model';

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
  private readonly injector = inject(Injector);
  private readonly successHeading = viewChild<ElementRef<HTMLHeadingElement>>('successHeading');
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
        next: (chamado) => {
          this.createdTicket.set(chamado);
          afterNextRender(() => this.successHeading()?.nativeElement.focus(), {
            injector: this.injector,
          });
        },
        error: (error: unknown) => {
          this.errorMessage.set(
            apiErrorMessage(error, 'Não foi possível abrir o chamado. Tente novamente.'),
          );
          const violations = readFieldViolations(error);
          this.fieldViolations.set(violations);
          if (
            error instanceof HttpErrorResponse &&
            error.status === 400 &&
            violations.some(
              (item) =>
                item.field === 'categoriaId' &&
                item.message === 'Categoria indisponível. Escolha outra categoria.',
            )
          ) {
            this.form.controls.categoriaId.setValue(0);
            this.form.controls.categoriaId.markAsTouched();
            this.loadCategorias();
          }
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
