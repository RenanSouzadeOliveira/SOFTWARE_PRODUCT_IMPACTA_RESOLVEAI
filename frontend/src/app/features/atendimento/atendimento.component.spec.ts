import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';

import { AtendimentoComponent } from './atendimento.component';
import { AtendimentoService } from './atendimento.service';

describe('AtendimentoComponent', () => {
  const responses: Subject<unknown>[] = [];
  const service = {
    checkAccess: vi.fn(() => {
      const response = new Subject<unknown>();
      responses.push(response);
      return response;
    }),
  };

  beforeEach(async () => {
    responses.length = 0;
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [AtendimentoComponent],
      providers: [{ provide: AtendimentoService, useValue: service }],
    }).compileComponents();
  });

  it('confirma quando o servidor autoriza o perfil de atendente', () => {
    const fixture = TestBed.createComponent(AtendimentoComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Validando acesso');
    responses[0].next({ message: 'Acesso de atendente autorizado' });
    fixture.detectChanges();

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[data-testid="access-allowed"]'),
    ).not.toBeNull();
  });

  it('mostra falha e permite repetir a validação', () => {
    const fixture = TestBed.createComponent(AtendimentoComponent);
    fixture.detectChanges();
    responses[0].error(new Error('indisponível'));
    fixture.detectChanges();

    const retry = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
      '.secondary-button',
    );
    expect(retry).not.toBeNull();
    retry?.click();
    expect(service.checkAccess).toHaveBeenCalledTimes(2);
  });
});
