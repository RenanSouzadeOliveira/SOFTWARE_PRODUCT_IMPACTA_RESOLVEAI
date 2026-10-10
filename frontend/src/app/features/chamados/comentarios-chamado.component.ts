import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  EventEmitter,
  inject,
  Input,
  OnChanges,
  Output,
  signal,
  SimpleChanges,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
} from '@angular/forms';
import { catchError, EMPTY, Subject, Subscription, switchMap, tap } from 'rxjs';
import { Perfil } from '../../core/auth/auth.models';
import { apiErrorMessage } from '../../core/http/api-error';
import { StatusChamado } from './chamado.model';
import { ComentariosChamado } from './comentarios-chamado.model';
import { ComentariosChamadoService } from './comentarios-chamado.service';

@Component({
  selector: 'app-comentarios-chamado',
  imports: [DatePipe, ReactiveFormsModule],
  templateUrl: './comentarios-chamado.component.html',
  styleUrl: './comentarios-chamado.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ComentariosChamadoComponent implements OnChanges {
  @Input({ required: true }) chamadoId!: number;
  @Input({ required: true }) status!: StatusChamado;
  @Input() refreshKey = 0;
  @Output() readonly conflito = new EventEmitter<void>();
  private readonly service = inject(ComentariosChamadoService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reload = new Subject<number>();
  private posting: Subscription | null = null;
  private generation = 0;
  protected readonly state = signal<'loading' | 'success' | 'error'>('loading');
  protected readonly pagina = signal<ComentariosChamado | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly postError = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly submitting = signal(false);
  protected readonly form = inject(NonNullableFormBuilder).group({
    conteudo: ['', trimmedContent],
  });
  protected readonly roles: Readonly<Record<Perfil, string>> = {
    SOLICITANTE: 'Solicitante',
    ATENDENTE: 'Atendente',
    ADMIN: 'Administrador',
  };

  constructor() {
    this.reload
      .pipe(
        tap(() => {
          this.state.set('loading');
          this.pagina.set(null);
          this.error.set(null);
        }),
        switchMap((id) =>
          this.service.listar(id).pipe(
            tap((page) => {
              this.pagina.set(page);
              this.state.set('success');
            }),
            catchError((error: unknown) => {
              this.state.set('error');
              this.error.set(
                apiErrorMessage(
                  error,
                  'Não foi possível carregar os comentários. Tente novamente.',
                ),
              );
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['chamadoId']) {
      this.generation++;
      this.posting?.unsubscribe();
      this.submitting.set(false);
      this.form.reset();
      this.postError.set(null);
      this.notice.set(null);
    }
    this.refresh();
  }

  protected refresh(): void {
    this.reload.next(this.chamadoId);
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    if (
      this.submitting() ||
      this.form.invalid ||
      this.pagina()?.podeComentar !== true ||
      this.state() !== 'success'
    )
      return;
    const generation = this.generation;
    this.submitting.set(true);
    this.postError.set(null);
    this.notice.set(null);
    this.posting = this.service
      .criar(this.chamadoId, this.form.controls.conteudo.value.trim())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (comment) => {
          if (generation !== this.generation) return;
          this.submitting.set(false);
          const page = this.pagina();
          if (page)
            this.pagina.set({
              ...page,
              content: [...page.content.filter((item) => item.id !== comment.id), comment],
            });
          else this.refresh();
          this.form.reset();
          this.notice.set('Comentário publicado com sucesso.');
        },
        error: (error: unknown) => {
          if (generation !== this.generation) return;
          this.submitting.set(false);
          this.postError.set(
            apiErrorMessage(
              error,
              'Não foi possível publicar o comentário. Seu texto foi preservado.',
            ),
          );
          if (error instanceof HttpErrorResponse && error.status === 409) {
            this.notice.set('O chamado foi alterado. As permissões estão sendo atualizadas.');
            this.refresh();
            this.conflito.emit();
          }
        },
      });
  }
}

function trimmedContent(control: AbstractControl<string>): ValidationErrors | null {
  const length = control.value.trim().length;
  return length >= 1 && length <= 2000 ? null : { contentLength: true };
}
