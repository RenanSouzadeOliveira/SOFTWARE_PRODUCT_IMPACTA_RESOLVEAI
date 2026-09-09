import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { ApiService } from '../../core/http/api.service';
import { HealthResponse } from './health.model';
import { HealthService } from './health.service';

describe('HealthService', () => {
  it('consulta o endpoint de health com contrato tipado', () => {
    const expected: HealthResponse = {
      status: 'UP',
      service: 'resolveai-api',
      timestamp: '2026-09-08T12:00:00Z',
    };
    const api = {
      get: vi.fn(() => of(expected)),
    };

    TestBed.configureTestingModule({
      providers: [HealthService, { provide: ApiService, useValue: api }],
    });

    let response: HealthResponse | undefined;
    TestBed.inject(HealthService)
      .check()
      .subscribe((health) => {
        response = health;
      });

    expect(api.get).toHaveBeenCalledWith('health');
    expect(response).toEqual(expected);
  });
});
