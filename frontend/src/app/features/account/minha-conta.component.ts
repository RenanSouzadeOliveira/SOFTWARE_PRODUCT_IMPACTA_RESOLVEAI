import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AuthService } from '../../core/auth/auth.service';
import { Usuario } from '../../core/auth/auth.models';
import { apiErrorMessage } from '../../core/http/api-error';

type AccountState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'ready'; readonly user: Usuario }
  | { readonly kind: 'error' };

@Component({
  selector: 'app-minha-conta',
  imports: [ReactiveFormsModule],
  templateUrl: './minha-conta.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MinhaContaComponent implements OnInit {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly state = signal<AccountState>({ kind: 'loading' });
  protected readonly saving = signal(false);
  protected readonly saveSucceeded = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly form = this.fb.group({
    nome: ['', [Validators.required, Validators.maxLength(120)]],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    if (this.state().kind === 'loading' && this.form.controls.nome.value) {
      return;
    }

    this.state.set({ kind: 'loading' });
    this.errorMessage.set(null);
    this.auth
      .loadCurrentUser()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (user) => {
          this.form.reset({ nome: user.nome });
          this.state.set({ kind: 'ready', user });
        },
        error: () => this.state.set({ kind: 'error' }),
      });
  }

  save(): void {
    if (this.saving() || this.state().kind !== 'ready') {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.saveSucceeded.set(false);
    this.errorMessage.set(null);
    this.auth
      .updateCurrentUser(this.form.getRawValue())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (user) => {
          this.saving.set(false);
          this.saveSucceeded.set(true);
          this.form.reset({ nome: user.nome });
          this.state.set({ kind: 'ready', user });
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.errorMessage.set(
            apiErrorMessage(error, 'Não foi possível salvar seu nome. Tente novamente.'),
          );
        },
      });
  }
}
