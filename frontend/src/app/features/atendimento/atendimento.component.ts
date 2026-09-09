import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { AtendimentoService } from './atendimento.service';

type AccessState = 'loading' | 'allowed' | 'error';

@Component({
  selector: 'app-atendimento',
  template: `
    <section class="access-page" aria-labelledby="atendimento-title">
      <p class="eyebrow">Área restrita</p>
      <h1 id="atendimento-title">Atendimento</h1>

      @switch (state()) {
        @case ('loading') {
          <p role="status">Validando acesso com a API…</p>
        }
        @case ('allowed') {
          <div class="access-success" role="status" data-testid="access-allowed">
            <strong>Acesso autorizado</strong>
            <p>Seu perfil de atendente foi validado pelo servidor.</p>
          </div>
        }
        @case ('error') {
          <div class="alert alert--error" role="alert" data-testid="access-error">
            <p>Não foi possível validar o acesso agora.</p>
            <button class="secondary-button" type="button" (click)="checkAccess()">
              Tentar novamente
            </button>
          </div>
        }
      }
    </section>
  `,
  styles: `
    .access-page {
      background: #fff;
      border: 1px solid #dce3ea;
      border-radius: 1rem;
      box-shadow: 0 1rem 2.5rem rgb(36 59 83 / 8%);
      margin: 0 auto;
      max-width: 46rem;
      padding: clamp(1.5rem, 5vw, 3rem);
    }
    h1 { color: #12263a; font-size: clamp(2rem, 7vw, 3rem); margin: 0 0 1.5rem; }
    .eyebrow { color: #0756b3; font-size: .78rem; font-weight: 750; letter-spacing: .09em; margin: 0 0 .5rem; text-transform: uppercase; }
    .access-success { background: #e8f8f0; border-radius: .75rem; padding: 1rem; }
    .access-success p { margin: .25rem 0 0; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AtendimentoComponent implements OnInit {
  private readonly service = inject(AtendimentoService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly state = signal<AccessState>('loading');

  ngOnInit(): void {
    this.checkAccess();
  }

  checkAccess(): void {
    this.state.set('loading');
    this.service
      .checkAccess()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.state.set('allowed'),
        error: () => this.state.set('error'),
      });
  }
}
