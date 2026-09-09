import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import type { Perfil } from '../../../core/auth/auth.models';
import { AuthService } from '../../../core/auth/auth.service';
import { safeReturnUrl } from '../../../core/auth/auth.guard';
import { apiErrorMessage } from '../../../core/http/api-error';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly registrationSucceeded =
    this.route.snapshot.queryParamMap.get('cadastrado') === '1';
  protected readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    senha: ['', [Validators.required]],
  });

  submit(): void {
    if (this.submitting()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);
    this.auth
      .login(this.form.getRawValue())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          const requestedUrl = this.route.snapshot.queryParamMap.get('returnUrl');
          const destination = requestedUrl
            ? safeReturnUrl(requestedUrl)
            : defaultRouteFor(response.usuario.perfil);
          void this.router.navigateByUrl(destination);
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          this.errorMessage.set(
            error instanceof HttpErrorResponse && error.status === 401
              ? 'E-mail ou senha inválidos.'
              : apiErrorMessage(error, 'Não foi possível entrar. Tente novamente.'),
          );
        },
      });
  }
}

function defaultRouteFor(perfil: Perfil): string {
  if (perfil === 'SOLICITANTE') {
    return '/chamados';
  }
  if (perfil === 'ATENDENTE') {
    return '/atendimento';
  }
  return '/minha-conta';
}
