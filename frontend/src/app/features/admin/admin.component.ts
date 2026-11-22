import { DOCUMENT } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  Injector,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize, Observable, take } from 'rxjs';
import { Perfil, Usuario } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { apiErrorMessage } from '../../core/http/api-error';
import { utf8MaxBytes } from '../auth/cadastro/cadastro.component';
import { PERFIL_LABELS, rotulo } from '../../shared/rotulos';
import { AdminService, CategoriaAdmin } from './admin.service';

type Registro = Usuario | CategoriaAdmin;
type Confirmacao =
  | { readonly tipo: 'situacao'; readonly registro: Registro }
  | { readonly tipo: 'perfil'; readonly registro: Usuario; readonly perfil: Perfil };
const textoObrigatorio = (control: AbstractControl<string>) =>
  control.value.trim() ? null : { required: true };

@Component({
  selector: 'app-admin',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminComponent {
  private readonly service = inject(AdminService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly document = inject(DOCUMENT);
  private returnFocus: HTMLElement | null = null;
  protected readonly usuarios =
    inject(ActivatedRoute).snapshot.data['administracao'] === 'usuarios';
  protected readonly registros = signal<readonly Registro[]>([]);
  protected readonly loading = signal(false);
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  protected readonly editor = signal(false);
  protected readonly editando = signal<Registro | null>(null);
  protected readonly confirmacao = signal<Confirmacao | null>(null);
  protected readonly perfis: readonly Perfil[] = ['SOLICITANTE', 'ATENDENTE', 'ADMIN'];
  protected readonly perfilLabels = PERFIL_LABELS;
  protected readonly rotulo = rotulo;
  protected readonly form = inject(NonNullableFormBuilder).group({
    nome: ['', [textoObrigatorio, Validators.maxLength(this.usuarios ? 120 : 100)]],
    email: [''],
    perfil: ['SOLICITANTE' as Perfil],
    senha: [''],
    descricao: ['', Validators.maxLength(255)],
  });

  constructor() {
    this.carregar();
  }

  carregar(): void {
    if (this.loading() || this.submitting()) return;
    this.loading.set(true);
    this.error.set(null);
    const request: Observable<readonly Registro[]> = this.usuarios
      ? this.service.listarUsuarios()
      : this.service.listarCategorias();
    request
      .pipe(
        take(1),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: (registros) => this.registros.set(registros),
        error: (error) =>
          this.falha(error, 'Não foi possível carregar os registros. Tente novamente.'),
      });
  }

  ativo(registro: Registro): boolean {
    return 'ativo' in registro ? registro.ativo : registro.ativa;
  }
  perfil(registro: Registro): Perfil | null {
    return 'perfil' in registro ? registro.perfil : null;
  }
  email(registro: Registro): string {
    return 'email' in registro ? registro.email : '';
  }
  descricao(registro: Registro): string {
    return 'descricao' in registro ? (registro.descricao ?? '') : '';
  }
  proprio(registro: Registro | null): boolean {
    return this.usuarios && registro?.id === this.auth.currentUser()?.id;
  }

  abrir(registro: Registro | null = null): void {
    if (this.submitting() || this.confirmacao()) return;
    this.returnFocus = this.document.activeElement as HTMLElement | null;
    this.editando.set(registro);
    this.form.reset({
      nome: registro?.nome ?? '',
      email: registro ? this.email(registro) : '',
      perfil: registro ? (this.perfil(registro) ?? 'SOLICITANTE') : 'SOLICITANTE',
      senha: '',
      descricao: registro ? this.descricao(registro) : '',
    });
    this.form.controls.email.setValidators(
      this.usuarios ? [Validators.required, Validators.email, Validators.maxLength(254)] : [],
    );
    this.form.controls.senha.setValidators(
      this.usuarios && !registro
        ? [Validators.required, Validators.minLength(8), Validators.maxLength(72), utf8MaxBytes(72)]
        : [],
    );
    this.form.controls.email.updateValueAndValidity();
    this.form.controls.senha.updateValueAndValidity();
    if (this.proprio(registro)) this.form.controls.perfil.disable();
    else this.form.controls.perfil.enable();
    this.error.set(null);
    this.success.set(null);
    this.editor.set(true);
    this.focar('#admin-nome');
  }

  cancelar(): void {
    if (this.submitting()) return;
    const confirmouPerfil = this.confirmacao()?.tipo === 'perfil';
    this.confirmacao.set(null);
    if (confirmouPerfil) this.focar('#admin-perfil');
    else {
      this.editor.set(false);
      this.error.set(null);
      this.restaurarFoco();
    }
  }

  submit(): void {
    if (this.submitting() || this.confirmacao() || !this.editor()) return;
    this.form.controls.nome.setValue(this.form.controls.nome.value.trim());
    this.form.controls.email.setValue(this.form.controls.email.value.trim());
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.focar('[aria-invalid="true"]');
      return;
    }
    const registro = this.editando();
    if (registro && 'perfil' in registro && this.form.controls.perfil.value !== registro.perfil) {
      if (this.proprio(registro)) {
        this.error.set('Você não pode alterar seu próprio perfil.');
        return;
      }
      this.confirmacao.set({ tipo: 'perfil', registro, perfil: this.form.controls.perfil.value });
      this.abrirConfirmacao();
      return;
    }
    this.salvar();
  }

  pedirSituacao(registro: Registro): void {
    if (this.submitting() || this.editor() || this.confirmacao() || this.proprio(registro)) return;
    this.returnFocus = this.document.activeElement as HTMLElement | null;
    this.error.set(null);
    this.success.set(null);
    this.confirmacao.set({ tipo: 'situacao', registro });
    this.abrirConfirmacao();
  }

  confirmar(): void {
    const confirmacao = this.confirmacao();
    if (!confirmacao || this.submitting()) return;
    if (confirmacao.tipo === 'perfil') {
      this.salvar();
      return;
    }
    if (this.proprio(confirmacao.registro)) return;
    const request: Observable<Registro> = this.usuarios
      ? this.service.situacaoUsuario(confirmacao.registro.id, !this.ativo(confirmacao.registro))
      : this.service.situacaoCategoria(confirmacao.registro.id, !this.ativo(confirmacao.registro));
    this.executar(request, 'Situação atualizada com sucesso.');
  }

  private salvar(): void {
    const registro = this.editando();
    const value = this.form.getRawValue();
    let request: Observable<Registro>;
    if (this.usuarios) {
      const input = { nome: value.nome.trim(), email: value.email.trim().toLowerCase() };
      if (registro && 'perfil' in registro) {
        const mudouPerfil = value.perfil !== registro.perfil;
        if (mudouPerfil && typeof registro.versao !== 'number') {
          this.descartarEdicaoDesatualizada();
          return;
        }
        request = this.service.editarUsuario(
          registro.id,
          mudouPerfil
            ? { ...input, perfil: value.perfil, versaoEsperada: registro.versao! }
            : input,
        );
      } else {
        request = this.service.criarUsuario({ ...input, perfil: value.perfil, senha: value.senha });
      }
    } else {
      const input = { nome: value.nome.trim(), descricao: value.descricao.trim() || null };
      request = registro
        ? this.service.editarCategoria(registro.id, input)
        : this.service.criarCategoria(input);
    }
    this.executar(
      request,
      registro ? 'Registro atualizado com sucesso.' : 'Registro criado com sucesso.',
    );
  }

  private executar(request: Observable<Registro>, message: string): void {
    this.submitting.set(true);
    this.error.set(null);
    request
      .pipe(
        take(1),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.submitting.set(false)),
      )
      .subscribe({
        next: (registro) => {
          this.registros.update((registros) =>
            registros.some((item) => item.id === registro.id)
              ? registros.map((item) => (item.id === registro.id ? registro : item))
              : [...registros, registro],
          );
          this.editor.set(false);
          this.confirmacao.set(null);
          this.form.controls.senha.reset();
          this.success.set(message);
          this.restaurarFoco();
        },
        error: (error) => {
          if (
            error instanceof HttpErrorResponse &&
            error.status === 409 &&
            Array.isArray(error.error?.violations) &&
            error.error.violations.some(
              (item: { field?: string }) => item?.field === 'versaoEsperada',
            )
          ) {
            this.descartarEdicaoDesatualizada();
            return;
          }
          this.confirmacao.set(null);
          this.falha(error, 'Não foi possível salvar. Tente novamente.');
          this.focar('#admin-error');
        },
      });
  }

  private descartarEdicaoDesatualizada(): void {
    this.editor.set(false);
    this.confirmacao.set(null);
    this.editando.set(null);
    this.form.reset();
    this.submitting.set(false);
    this.carregar();
    this.error.set(
      'O registro foi alterado por outra pessoa. A lista foi atualizada. Abra a edição novamente.',
    );
    this.restaurarFoco();
  }

  // O <dialog> modal nativo deixa o Tab sair para a interface do navegador; mantém o ciclo nos botões.
  prenderFoco(event: KeyboardEvent): void {
    if (event.key !== 'Tab') return;
    const dialogo = event.currentTarget as HTMLElement;
    const botoes = Array.from(
      dialogo.querySelectorAll<HTMLButtonElement>('button:not([disabled])'),
    );
    const atual = botoes.indexOf(document.activeElement as HTMLButtonElement);
    event.preventDefault();
    if (!botoes.length) return;
    const passo = event.shiftKey ? -1 : 1;
    botoes[(atual + passo + botoes.length) % botoes.length].focus();
  }

  cancelarDialogo(event: Event): void {
    event.preventDefault();
    this.cancelar();
  }

  private abrirConfirmacao(): void {
    afterNextRender(
      () => {
        const dialog = this.element.nativeElement.querySelector<HTMLDialogElement>('dialog');
        dialog?.showModal();
        dialog?.querySelector<HTMLElement>('#admin-cancelar')?.focus();
      },
      { injector: this.injector },
    );
  }

  private falha(error: unknown, fallback: string): void {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    this.error.set(
      status === 401
        ? 'Sua sessão expirou. Entre novamente.'
        : status === 403
          ? 'Você não tem permissão para realizar esta ação.'
          : status === 409
            ? apiErrorMessage(
                error,
                this.usuarios
                  ? 'Este e-mail já está cadastrado ou o registro foi alterado. Revise os dados e tente novamente.'
                  : 'Já existe uma categoria com este nome ou o registro foi alterado. Revise os dados e tente novamente.',
              )
            : apiErrorMessage(error, fallback),
    );
  }
  private focar(selector: string): void {
    afterNextRender(
      () => this.element.nativeElement.querySelector<HTMLElement>(selector)?.focus(),
      { injector: this.injector },
    );
  }
  private restaurarFoco(): void {
    afterNextRender(
      () =>
        this.returnFocus?.isConnected
          ? this.returnFocus.focus()
          : this.element.nativeElement.querySelector<HTMLElement>('#admin-novo')?.focus(),
      { injector: this.injector },
    );
  }
}
