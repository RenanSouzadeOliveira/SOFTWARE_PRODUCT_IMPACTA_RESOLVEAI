import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('cria o layout principal acessível', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('header')).not.toBeNull();
    expect(element.querySelector('main#conteudo-principal')).not.toBeNull();
    expect(element.querySelector('footer')).not.toBeNull();
    expect(element.querySelector('nav')?.getAttribute('aria-label')).toBe('Navegação principal');
  });
});
