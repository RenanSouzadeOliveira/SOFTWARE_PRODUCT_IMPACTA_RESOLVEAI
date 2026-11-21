import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-acesso-negado',
  imports: [RouterLink],
  template: `
    <section class="acesso-negado" aria-labelledby="acesso-negado-title">
      <p class="code" aria-hidden="true">403</p>
      <h1 id="acesso-negado-title">Acesso negado</h1>
      <p>Seu perfil não tem permissão para acessar esta página.</p>
      <a routerLink="/">Voltar ao início</a>
    </section>
  `,
  styles: `
    .acesso-negado {
      margin: 4rem auto;
      max-width: 38rem;
      padding: 0 1rem;
      text-align: center;
    }

    .code {
      color: #b42318;
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

    a:focus-visible {
      outline: 3px solid #12263a;
      outline-offset: 3px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AcessoNegadoComponent {}
