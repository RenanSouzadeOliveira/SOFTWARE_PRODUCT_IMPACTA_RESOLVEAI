import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    title: 'Início | ResolveAí',
    loadComponent: () =>
      import('./features/home/home.component').then((component) => component.HomeComponent),
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
