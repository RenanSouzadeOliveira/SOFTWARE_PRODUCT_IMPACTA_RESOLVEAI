import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';

import { HealthResponse } from '../health/health.model';
import { HealthService } from '../health/health.service';
import { HomeComponent } from './home.component';

describe('HomeComponent', () => {
  const responses: Subject<HealthResponse>[] = [];
  const healthService = {
    check: vi.fn(() => {
      const response = new Subject<HealthResponse>();
      responses.push(response);
      return response.asObservable();
    }),
  };

  beforeEach(async () => {
    responses.length = 0;
    healthService.check.mockClear();

    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [{ provide: HealthService, useValue: healthService }],
    }).compileComponents();
  });

  it('mostra o estado de carregamento enquanto verifica a API', () => {
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[data-testid="health-loading"]')).not.toBeNull();
    expect(healthService.check).toHaveBeenCalledTimes(1);
  });

  it('mostra o estado de sucesso com o status recebido', () => {
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();

    responses[0].next({
      status: 'UP',
      service: 'resolveai-api',
      timestamp: '2026-09-08T12:00:00Z',
    });
    responses[0].complete();
    fixture.detectChanges();

    const success = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="health-success"]',
    );
    expect(success?.textContent).toContain('API disponível');
    expect(success?.textContent).toContain('UP');
  });

  it('mostra erro e permite tentar novamente sem duplicar requisições em andamento', () => {
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();

    responses[0].error(new Error('API indisponível'));
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[data-testid="health-error"]')).not.toBeNull();

    const retryButton = element.querySelector<HTMLButtonElement>('.retry-button');
    retryButton?.click();
    fixture.componentInstance.checkHealth();
    fixture.detectChanges();

    expect(healthService.check).toHaveBeenCalledTimes(2);
    expect(element.querySelector('[data-testid="health-loading"]')).not.toBeNull();
  });
});
