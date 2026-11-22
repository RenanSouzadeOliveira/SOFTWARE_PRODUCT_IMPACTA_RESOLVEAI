import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  isActive,
  type IsActiveMatchOptions,
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { filter } from 'rxjs';

import { AuthService } from './core/auth/auth.service';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:keydown.escape)': 'fecharMenu(true)' },
})
export class App {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly botaoMenu = viewChild<ElementRef<HTMLButtonElement>>('botaoMenu');

  protected readonly correspondenciaExata: IsActiveMatchOptions = {
    paths: 'exact',
    queryParams: 'ignored',
    matrixParams: 'ignored',
    fragment: 'ignored',
  };
  protected readonly correspondenciaSecao: IsActiveMatchOptions = {
    ...this.correspondenciaExata,
    paths: 'subset',
  };
  protected readonly abrirChamadoAtivo = isActive(
    '/chamados/novo',
    this.router,
    this.correspondenciaExata,
  );
  protected readonly todosChamadosAtivo = isActive(
    '/atendimento/todos',
    this.router,
    this.correspondenciaSecao,
  );
  protected readonly administracaoAtiva = isActive(
    '/admin',
    this.router,
    this.correspondenciaSecao,
  );

  protected readonly currentUser = this.auth.currentUser;
  // Menu recolhível só existe em telas estreitas (CSS); no desktop a navegação fica sempre visível.
  protected readonly menuAberto = signal(false);

  constructor() {
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.menuAberto.set(false));
  }

  protected alternarMenu(): void {
    this.menuAberto.update((aberto) => !aberto);
  }

  protected fecharMenu(devolverFoco = false): void {
    if (!this.menuAberto()) return;
    this.menuAberto.set(false);
    if (devolverFoco) this.botaoMenu()?.nativeElement.focus();
  }

  protected pularParaConteudo(event: Event): void {
    // Evita que o href com <base href="/"> navegue para a página inicial.
    event.preventDefault();
    document.getElementById('conteudo-principal')?.focus();
  }

  protected logout(): void {
    this.menuAberto.set(false);
    this.auth.logout();
    void this.router.navigate(['/login']);
  }
}
