import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AdminService } from './admin.service';

describe('AdminService', () => {
  let service: AdminService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdminService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('lista usuários e categorias incluindo inativos sem transformação de ordem', () => {
    for (const [path, request] of [
      ['usuarios', service.listarUsuarios()],
      ['categorias', service.listarCategorias()],
    ] as const) {
      const response = [{ id: 2 }, { id: 1 }];
      (request as Observable<unknown>).subscribe((value) => expect(value).toEqual(response));
      const pending = http.expectOne(`${environment.apiUrl}/admin/${path}`);
      expect(pending.request.method).toBe('GET');
      pending.flush(response);
    }
  });
  it('envia contratos específicos de criação, edição e situação', () => {
    const usuario = { nome: 'Pessoa', email: 'pessoa@example.com', perfil: 'ATENDENTE' as const };
    const categoria = { nome: 'Acesso', descricao: null };
    const cases = [
      [
        'usuarios',
        'POST',
        { ...usuario, senha: 'senha1234' },
        () => service.criarUsuario({ ...usuario, senha: 'senha1234' }),
      ],
      ['usuarios/2', 'PATCH', usuario, () => service.editarUsuario(2, usuario)],
      ['usuarios/2/situacao', 'PATCH', { ativo: false }, () => service.situacaoUsuario(2, false)],
      ['categorias', 'POST', categoria, () => service.criarCategoria(categoria)],
      ['categorias/3', 'PATCH', categoria, () => service.editarCategoria(3, categoria)],
      ['categorias/3/situacao', 'PATCH', { ativa: true }, () => service.situacaoCategoria(3, true)],
    ] as const;
    for (const [path, method, body, call] of cases) {
      (call() as Observable<unknown>).subscribe();
      const pending = http.expectOne(`${environment.apiUrl}/admin/${path}`);
      expect(pending.request.method).toBe(method);
      expect(pending.request.body).toEqual(body);
      pending.flush({});
    }
  });
});
