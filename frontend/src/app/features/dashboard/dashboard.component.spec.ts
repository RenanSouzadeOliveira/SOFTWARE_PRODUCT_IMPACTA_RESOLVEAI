import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';
import { DashboardComponent } from './dashboard.component';
import { DashboardResumo } from './dashboard.service';

describe('DashboardComponent', () => {
  const url = `${environment.apiUrl}/dashboard/resumo`;
  const resumo: DashboardResumo = {
    total: 5,
    abertos: 1,
    emAtendimento: 2,
    concluidos: 2,
    porStatus: [
      { status: 'ABERTO', quantidade: 1 },
      { status: 'EM_ATENDIMENTO', quantidade: 2 },
      { status: 'RESOLVIDO', quantidade: 1 },
      { status: 'FECHADO', quantidade: 1 },
    ],
    porPrioridade: [
      { prioridade: 'BAIXA', quantidade: 3 },
      { prioridade: 'MEDIA', quantidade: 2 },
      { prioridade: 'ALTA', quantidade: 0 },
    ],
    porCategoria: [
      { id: 7, nome: 'Acesso e autenticação', quantidade: 5 },
      { id: 9, nome: 'Equipamentos', quantidade: 0 },
    ],
  };

  let fixture: ComponentFixture<DashboardComponent>;
  let element: HTMLElement;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { currentUser: signal({ perfil: 'ATENDENTE' }) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DashboardComponent);
    element = fixture.nativeElement;
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('carrega uma vez e apresenta indicadores e distribuições do GET', () => {
    expect(element.querySelector('[role="status"]')?.textContent).toContain('Carregando');
    expect(element.querySelector('.kpis')).toBeNull();

    http.expectOne({ url, method: 'GET' }).flush(resumo);
    fixture.detectChanges();

    expect(element.querySelector('[role="status"]')).toBeNull();
    expect(element.querySelector('header p')?.textContent).toContain('sob sua responsabilidade');
    expect(Array.from(element.querySelectorAll('.kpi dt'), (term) => term.textContent)).toEqual([
      'Total de chamados visíveis',
      'Chamados abertos',
      'Chamados em atendimento',
      'Chamados concluídos',
    ]);
    expect(
      Array.from(element.querySelectorAll('.kpi dd:first-of-type'), (value) => value.textContent),
    ).toEqual(['5', '1', '2', '2']);
    expect(
      Array.from(element.querySelectorAll('.grafico h2'), (title) => title.textContent),
    ).toEqual([
      'Distribuição por status',
      'Distribuição por prioridade',
      'Distribuição por categoria',
    ]);
    const charts = Array.from(element.querySelectorAll('.grafico'));
    const groups = charts.map((chart) =>
      Array.from(chart.querySelectorAll('.grupo'), (row) => [
        row.querySelector('span')?.textContent,
        row.querySelector('strong')?.textContent,
      ]),
    );
    expect(groups).toEqual([
      [
        ['Aberto', '1'],
        ['Em atendimento', '2'],
        ['Resolvido', '1'],
        ['Fechado', '1'],
      ],
      [
        ['Baixa', '3'],
        ['Média', '2'],
        ['Alta', '0'],
      ],
      [
        ['Acesso e autenticação', '5'],
        ['Equipamentos', '0'],
      ],
    ]);
    expect(charts[1].querySelector<HTMLElement>('li:last-child .barra')?.style.width).toBe('0%');
    expect(charts[2].querySelector<HTMLElement>('.barra')?.style.width).toBe('100%');
  });

  it('devolve o foco a cada falha de retry e carrega uma única vez por tentativa', async () => {
    const indisponivel = { status: 503, statusText: 'Service Unavailable' };
    const botao = () => element.querySelector<HTMLButtonElement>('button')!;
    http.expectOne({ url, method: 'GET' }).flush({}, indisponivel);
    await fixture.whenStable();
    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Não foi possível carregar',
    );
    expect(document.activeElement).toBe(document.body);

    botao().focus();
    for (let tentativa = 0; tentativa < 2; tentativa += 1) {
      const retry = botao();
      expect(document.activeElement).toBe(retry);
      retry.click();
      retry.click();
      fixture.detectChanges();
      expect(element.querySelector('[role="status"]')?.textContent).toContain('Carregando');
      expect(element.querySelector('button')).toBeNull();

      http.expectOne({ url, method: 'GET' }).flush({}, indisponivel);
      await fixture.whenStable();
      expect(element.querySelector('[role="alert"]')).not.toBeNull();
      expect(document.activeElement).toBe(botao());
    }

    botao().click();
    http.expectOne({ url, method: 'GET' }).flush(resumo);
    await fixture.whenStable();
    expect(element.querySelector('[role="alert"]')).toBeNull();
    expect(element.querySelectorAll('.grafico')).toHaveLength(3);
  });

  it('exibe quatro zeros e mensagem de escopo vazio', () => {
    http.expectOne(url).flush({
      total: 0,
      abertos: 0,
      emAtendimento: 0,
      concluidos: 0,
      porStatus: [],
      porPrioridade: [],
      porCategoria: [{ id: 9, nome: 'Equipamentos', quantidade: 0 }],
    } satisfies DashboardResumo);
    fixture.detectChanges();

    expect(
      Array.from(element.querySelectorAll('.kpi dd:first-of-type'), (value) => value.textContent),
    ).toEqual(['0', '0', '0', '0']);
    expect(element.querySelector('[role="status"]')?.textContent).toContain(
      'Nenhum chamado visível',
    );
    expect(element.querySelector('.grafico')).toBeNull();
  });
});
