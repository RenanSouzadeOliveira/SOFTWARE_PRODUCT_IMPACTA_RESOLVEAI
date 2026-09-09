import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found',
  imports: [RouterLink],
  template: `
    <section class="not-found" aria-labelledby="not-found-title">
      <p class="code" aria-hidden="true">404</p>
      <h1 id="not-found-title">Página não encontrada</h1>
      <p>O endereço informado não existe ou foi alterado.</p>
      <a routerLink="/">Voltar ao início</a>
    </section>
  `,
  styles: `
    .not-found {
      margin: 4rem auto;
      max-width: 38rem;
      text-align: center;
    }

    .code {
      color: #0967d2;
      font-size: 1rem;
      font-weight: 800;
      letter-spacing: 0.15em;
      margin: 0;
    }

    h1 {
      color: #12263a;
      font-size: clamp(2rem, 8vw, 3.25rem);
      margin: 0.5rem 0 1rem;
    }

    p {
      color: #52606d;
      line-height: 1.6;
    }

    a {
      background: #0967d2;
      border-radius: 0.55rem;
      color: #ffffff;
      display: inline-block;
      font-weight: 700;
      margin-top: 1rem;
      padding: 0.75rem 1rem;
      text-decoration: none;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotFoundComponent {}
