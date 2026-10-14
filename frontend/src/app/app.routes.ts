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
    path: 'chamados',
    title: 'Meus chamados | ResolveAí',
    canActivate: [authGuard, roleGuard],
    data: { role: 'SOLICITANTE' },
    loadComponent: () =>
      import('./features/chamados/meus-chamados.component').then(
        (component) => component.MeusChamadosComponent,
      ),
  },
  {
    path: 'chamados/:id',
    title: 'Detalhes do chamado | ResolveAí',
    canActivate: [authGuard, roleGuard],
    data: { role: 'SOLICITANTE' },
    loadComponent: () =>
      import('./features/chamados/detalhe-chamado.component').then(
        (component) => component.DetalheChamadoComponent,
      ),
  },
  {
    path: 'atendimento/chamados/:id',
    title: 'Detalhes do atendimento | ResolveAí',
    canActivate: [authGuard, roleGuard],
    data: {
      roles: ['ATENDENTE', 'ADMIN'],
      backRoute: '/atendimento/me',
      backLabel: 'Voltar para meus atendimentos',
    },
    loadComponent: () =>
      import('./features/chamados/detalhe-chamado.component').then(
        (component) => component.DetalheChamadoComponent,
      ),
  },
  {
    path: 'atendimento',
    title: 'Atendimento | ResolveAí',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ATENDENTE', 'ADMIN'], carteira: false },
    loadComponent: () =>
      import('./features/atendimento/atendimento.component').then(
        (component) => component.AtendimentoComponent,
      ),
  },
  {
    path: 'atendimento/me',
    title: 'Meus atendimentos | ResolveAí',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ATENDENTE', 'ADMIN'], carteira: true },
    loadComponent: () =>
      import('./features/atendimento/atendimento.component').then(
        (component) => component.AtendimentoComponent,
      ),
  },
  {
    path: 'atendimento/todos',
    title: 'Todos os chamados | ResolveAí',
    canActivate: [authGuard, roleGuard],
    data: { role: 'ADMIN', todos: true },
    loadComponent: () =>
      import('./features/atendimento/atendimento.component').then(
        (component) => component.AtendimentoComponent,
      ),
  },
  {
    path: 'atendimento/todos/:id',
    title: 'Detalhes do chamado | ResolveAí',
    canActivate: [authGuard, roleGuard],
    data: {
      role: 'ADMIN',
      backRoute: '/atendimento/todos',
      backLabel: 'Voltar para todos os chamados',
    },
    loadComponent: () =>
      import('./features/chamados/detalhe-chamado.component').then(
        (component) => component.DetalheChamadoComponent,
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
