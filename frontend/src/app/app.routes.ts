import { Routes } from '@angular/router';

import { authGuard } from './core/auth/auth.guard';
import { roleGuard } from './core/auth/role.guard';

export const routes: Routes = [
  {
    path: '',
    title: 'Início | ResolveAí',
    loadComponent: () =>
      import('./features/home/home.component').then((component) => component.HomeComponent),
  },
  {
    path: 'cadastro',
    title: 'Cadastro | ResolveAí',
    loadComponent: () =>
      import('./features/auth/cadastro/cadastro.component').then(
        (component) => component.CadastroComponent,
      ),
  },
  {
    path: 'login',
    title: 'Entrar | ResolveAí',
    loadComponent: () =>
      import('./features/auth/login/login.component').then((component) => component.LoginComponent),
  },
  {
    path: 'minha-conta',
    title: 'Minha conta | ResolveAí',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/account/minha-conta.component').then(
        (component) => component.MinhaContaComponent,
      ),
  },
  {
    path: 'chamados/novo',
    title: 'Abrir chamado | ResolveAí',
    canActivate: [authGuard, roleGuard],
    data: { role: 'SOLICITANTE' },
    loadComponent: () =>
      import('./features/chamados/novo-chamado.component').then(
        (component) => component.NovoChamadoComponent,
      ),
  },
  {
    path: 'atendimento',
    title: 'Atendimento | ResolveAí',
    canActivate: [authGuard, roleGuard],
    data: { role: 'ATENDENTE' },
    loadComponent: () =>
      import('./features/atendimento/atendimento.component').then(
        (component) => component.AtendimentoComponent,
      ),
  },
  {
    path: '**',
    title: 'Página não encontrada | ResolveAí',
    loadComponent: () =>
      import('./features/not-found/not-found.component').then(
        (component) => component.NotFoundComponent,
      ),
  },
];
