import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiService } from '../../core/http/api.service';

@Injectable({ providedIn: 'root' })
export class AtendimentoService {
  private readonly api = inject(ApiService);

  checkAccess(): Observable<unknown> {
    return this.api.get<unknown>('atendimento/acesso');
  }
}
