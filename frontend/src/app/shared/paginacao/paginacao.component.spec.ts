import { TestBed } from '@angular/core/testing';
import { PaginacaoComponent } from './paginacao.component';

describe('PaginacaoComponent', () => {
  function render(page: number, totalPages: number, disabled = false) {
    const fixture = TestBed.createComponent(PaginacaoComponent);
    fixture.componentRef.setInput('query', { page, size: 20, sort: 'criadoEm,desc' });
    fixture.componentRef.setInput('result', {
      content: [],
      page,
      size: 20,
      totalElements: totalPages ? 45 : 0,
      totalPages,
    });
    fixture.componentRef.setInput('disabled', disabled);
    fixture.detectChanges();
    return fixture;
  }
  it('desabilita navegação inválida no vazio, primeira, última e carregando', () => {
    for (const [page, total, loading, previous, next] of [
      [0, 0, false, true, true],
      [0, 3, false, true, false],
      [2, 3, false, false, true],
      [1, 3, true, true, true],
    ] as const) {
      const fixture = render(page, total, loading);
      const buttons = fixture.nativeElement.querySelectorAll('button');
      expect(buttons[0].disabled).toBe(previous);
      expect(buttons[1].disabled).toBe(next);
      fixture.destroy();
    }
  });
  it('emite navegação e mudanças de tamanho e ordenação sem duplicar seleção atual', () => {
    const fixture = render(1, 3);
    const pages: number[] = [];
    const settings: unknown[] = [];
    fixture.componentInstance.pageChange.subscribe((v) => pages.push(v));
    fixture.componentInstance.settingsChange.subscribe((v) => settings.push(v));
    const element: HTMLElement = fixture.nativeElement;
    element.querySelectorAll('button')[1].click();
    expect(pages).toEqual([2]);
    const size = element.querySelector<HTMLSelectElement>('[aria-label="Itens por página"]')!;
    size.dispatchEvent(new Event('change'));
    expect(settings).toEqual([]);
    size.value = '100';
    size.dispatchEvent(new Event('change'));
    const sort = element.querySelector<HTMLSelectElement>('[aria-label="Ordenar por"]')!;
    sort.value = 'titulo,asc';
    sort.dispatchEvent(new Event('change'));
    expect(settings).toEqual([
      { size: 100, sort: 'criadoEm,desc' },
      { size: 20, sort: 'titulo,asc' },
    ]);
  });
  it('mostra tamanho e ordenação restaurados antes da criação das opções', () => {
    const fixture = TestBed.createComponent(PaginacaoComponent);
    fixture.componentRef.setInput('query', { page: 0, size: 50, sort: 'titulo,asc' });
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector<HTMLSelectElement>('[aria-label="Itens por página"]')!.value).toBe(
      '50',
    );
    expect(element.querySelector<HTMLSelectElement>('[aria-label="Ordenar por"]')!.value).toBe(
      'titulo,asc',
    );
  });
});
