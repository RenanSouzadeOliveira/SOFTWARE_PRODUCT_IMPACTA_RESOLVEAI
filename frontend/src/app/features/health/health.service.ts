import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiService } from '../../core/http/api.service';
import { HealthResponse } from './health.model';

@Injectable({ providedIn: 'root' })
export class HealthService {
  private readonly api = inject(ApiService);

  check(): Observable<HealthResponse> {
    return this.api.get<HealthResponse>('health');
  }
}
