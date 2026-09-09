import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { ApiService } from './api.service';

describe('ApiService', () => {
  let service: ApiService;
  let httpController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ApiService, provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(ApiService);
    httpController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpController.verify());

  it('faz GET tipado usando a URL base e normaliza a barra do caminho', () => {
    const expectedResponse = { id: 42, label: 'chamado' };
    let actualResponse: typeof expectedResponse | undefined;

    service
      .get<typeof expectedResponse>('/recursos', { params: { page: 1 } })
      .subscribe((response) => {
        actualResponse = response;
      });

    const request = httpController.expectOne(
      (candidate) =>
        candidate.url === `${environment.apiUrl}/recursos` && candidate.params.get('page') === '1',
    );
    expect(request.request.method).toBe('GET');

    request.flush(expectedResponse);
    expect(actualResponse).toEqual(expectedResponse);
  });

  it('faz POST e PATCH tipados', () => {
    service.post<{ id: number }, { nome: string }>('recursos', { nome: 'Novo' }).subscribe();
    const post = httpController.expectOne(`${environment.apiUrl}/recursos`);
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ nome: 'Novo' });
    post.flush({ id: 1 });

    service.patch<{ id: number }, { nome: string }>('recursos/1', { nome: 'Editado' }).subscribe();
    const patch = httpController.expectOne(`${environment.apiUrl}/recursos/1`);
    expect(patch.request.method).toBe('PATCH');
    expect(patch.request.body).toEqual({ nome: 'Editado' });
    patch.flush({ id: 1 });
  });
});
