import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of, Subject } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { Usuario } from '../../core/auth/auth.models';
import { AdminComponent } from './admin.component';
import { AdminService, CategoriaAdmin } from './admin.service';

const usuario: Usuario = {
  id: 2,
  nome: 'Pessoa de teste',
  email: 'pessoa@example.com',
  perfil: 'SOLICITANTE',
  ativo: true,
  versao: 0,
};
const categoria: CategoriaAdmin = {
  id: 8,
  nome: 'Acesso',
  descricao: 'Contas e permissões',
  ativa: true,
};
describe('AdminComponent', () => {
  let tipo: 'usuarios' | 'categorias';
  let list: Subject<readonly Usuario[] | readonly CategoriaAdmin[]>;
  let mutation: Subject<Usuario | CategoriaAdmin>;
  const service = {
    listarUsuarios: vi.fn(),
    listarCategorias: vi.fn(),
    criarUsuario: vi.fn(),
    editarUsuario: vi.fn(),
    situacaoUsuario: vi.fn(),
    criarCategoria: vi.fn(),
    editarCategoria: vi.fn(),
    situacaoCategoria: vi.fn(),
  };
  beforeEach(async () => {
    vi.clearAllMocks();
    // jsdom não implementa a top layer nativa; teclado/foco real é verificado no Chrome.
    Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
      configurable: true,
      value: function (this: HTMLDialogElement) {
        this.open = true;
      },
    });
    tipo = 'usuarios';
    list = new Subject();
    mutation = new Subject();
    service.listarUsuarios.mockReturnValue(list);
    service.listarCategorias.mockReturnValue(list);
    for (const method of [
      service.criarUsuario,
      service.editarUsuario,
      service.situacaoUsuario,
      service.criarCategoria,
      service.editarCategoria,
      service.situacaoCategoria,
    ])
      method.mockReturnValue(mutation);
    await TestBed.configureTestingModule({
      imports: [AdminComponent],
      providers: [
        provideRouter([]),
        { provide: AdminService, useValue: service },
        {
          provide: AuthService,
          useValue: { currentUser: () => ({ ...usuario, id: 1, perfil: 'ADMIN' }) },
        },
        {
          provide: ActivatedRoute,
          useFactory: () => ({ snapshot: { data: { administracao: tipo } } }),
        },
      ],
    }).compileComponents();
  });
  function render() {
    const fixture = TestBed.createComponent(AdminComponent);
    fixture.detectChanges();
    return fixture;
  }
  function query<T extends HTMLElement = HTMLElement>(
    fixture: ComponentFixture<AdminComponent>,
    selector: string,
  ): T {
    return fixture.nativeElement.querySelector(selector);
  }
  function click(fixture: ComponentFixture<AdminComponent>, selector: string) {
    query(fixture, selector).click();
    fixture.detectChanges();
  }
  function field(fixture: ComponentFixture<AdminComponent>, id: string, value: string) {
    const input = query<HTMLInputElement>(fixture, id);
    input.value = value;
    input.dispatchEvent(new Event(input.tagName === 'SELECT' ? 'change' : 'input'));
  }
  function submit(fixture: ComponentFixture<AdminComponent>) {
    query(fixture, 'form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();
  }
  function userForm(fixture: ComponentFixture<AdminComponent>) {
    field(fixture, '#admin-nome', '  Novo nome  ');
    field(fixture, '#admin-email', 'NOVO@example.com');
    field(fixture, '#admin-senha', 'senha1234');
  }

  it('mostra carregamento, lista com perfil/situação e identifica autoações proibidas', () => {
    const fixture = render();
    expect(fixture.nativeElement.textContent).toContain('Carregando usuários');
    list.next([usuario, { ...usuario, id: 1, nome: 'Admin', perfil: 'ADMIN' }]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('SOLICITANTE');
    expect(fixture.nativeElement.textContent).toContain('Ativo');
    expect(
      query<HTMLButtonElement>(
        fixture,
        '[data-testid="admin-registro-1"] [data-testid="admin-situacao"]',
      ).disabled,
    ).toBe(true);
    click(fixture, '[data-testid="admin-registro-1"] [data-testid="admin-editar"]');
    expect(query<HTMLSelectElement>(fixture, '#admin-perfil').disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Você não pode alterar seu próprio perfil');
  });
  it('mostra lista vazia e permite recuperar falha de carregamento', () => {
    const fixture = render();
    list.error(new Error('offline'));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Não foi possível carregar');
    service.listarUsuarios.mockReturnValue(of([]));
    click(fixture, '[data-testid="admin-recarregar"]');
    expect(query(fixture, '[data-testid="admin-vazio"]')).not.toBeNull();
    expect(query(fixture, '[data-testid="admin-error"]')).toBeNull();
  });
  it('valida nome em branco, e-mail, senha e foco no primeiro campo inválido', async () => {
    const fixture = render();
    list.next([]);
    fixture.detectChanges();
    click(fixture, '#admin-novo');
    await fixture.whenStable();
    expect(document.activeElement?.id).toBe('admin-nome');
    field(fixture, '#admin-nome', '   ');
    field(fixture, '#admin-email', 'invalido');
    field(fixture, '#admin-senha', 'curta');
    submit(fixture);
    await fixture.whenStable();
    expect(document.activeElement?.id).toBe('admin-nome');
    expect(query(fixture, '#admin-email').getAttribute('aria-invalid')).toBe('true');
    expect(fixture.nativeElement.textContent).toContain('Informe uma senha válida');
    expect(service.criarUsuario).not.toHaveBeenCalled();
  });
  it('rejeita senha acima do limite UTF-8 e aceita dados normalizados, bloqueando envio duplicado', () => {
    const fixture = render();
    list.next([]);
    fixture.detectChanges();
    click(fixture, '#admin-novo');
    userForm(fixture);
    field(fixture, '#admin-senha', 'á'.repeat(37));
    submit(fixture);
    expect(service.criarUsuario).not.toHaveBeenCalled();
    field(fixture, '#admin-senha', 'senha1234');
    submit(fixture);
    fixture.componentInstance.submit();
    expect(service.criarUsuario).toHaveBeenCalledExactlyOnceWith({
      nome: 'Novo nome',
      email: 'novo@example.com',
      perfil: 'SOLICITANTE',
      senha: 'senha1234',
    });
    expect(query<HTMLButtonElement>(fixture, '[data-testid="admin-salvar"]').disabled).toBe(true);
    mutation.next({ ...usuario, nome: 'Novo nome' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Registro criado com sucesso');
    expect(query(fixture, 'form')).toBeNull();
  });
  it('preserva dados para corrigir conflito 409 e permite tentar novamente', () => {
    const fixture = render();
    list.next([]);
    fixture.detectChanges();
    click(fixture, '#admin-novo');
    userForm(fixture);
    submit(fixture);
    mutation.error(new HttpErrorResponse({ status: 409 }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Este e-mail já está cadastrado');
    expect(query<HTMLInputElement>(fixture, '#admin-email').value).toBe('NOVO@example.com');
    expect(query<HTMLButtonElement>(fixture, '[data-testid="admin-salvar"]').disabled).toBe(false);
    service.criarUsuario.mockReturnValue(of(usuario));
    submit(fixture);
    expect(service.criarUsuario).toHaveBeenCalledTimes(2);
  });
  it('edita usuário sem enviar senha e confirma mudança de perfil antes da API', () => {
    const fixture = render();
    list.next([usuario]);
    fixture.detectChanges();
    click(fixture, '[data-testid="admin-editar"]');
    expect(query(fixture, '#admin-senha')).toBeNull();
    field(fixture, '#admin-perfil', 'ATENDENTE');
    submit(fixture);
    expect(service.editarUsuario).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('de SOLICITANTE para ATENDENTE');
    click(fixture, '#admin-cancelar');
    expect(query(fixture, '[data-testid="admin-confirmacao"]')).toBeNull();
    submit(fixture);
    click(fixture, '#admin-confirmar');
    fixture.componentInstance.confirmar();
    expect(service.editarUsuario).toHaveBeenCalledExactlyOnceWith(2, {
      nome: usuario.nome,
      email: usuario.email,
      perfil: 'ATENDENTE',
      versaoEsperada: 0,
    });
    mutation.next({ ...usuario, perfil: 'ATENDENTE' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Registro atualizado com sucesso');
  });
  it('permite editar dados da própria conta preservando perfil mesmo se manipulado', () => {
    const fixture = render();
    list.next([{ ...usuario, id: 1, perfil: 'ADMIN' }]);
    fixture.detectChanges();
    click(fixture, '[data-testid="admin-editar"]');
    field(fixture, '#admin-nome', 'Nome atualizado');
    submit(fixture);
    expect(service.editarUsuario).toHaveBeenCalledWith(1, {
      nome: 'Nome atualizado',
      email: usuario.email,
    });
  });
  it.each([true, false])(
    'confirma ativação/desativação de usuário (ativo=%s), permite cancelar e restaura foco',
    async (ativo) => {
      const fixture = render();
      list.next([{ ...usuario, ativo }]);
      fixture.detectChanges();
      const button = query<HTMLButtonElement>(fixture, '[data-testid="admin-situacao"]');
      button.focus();
      click(fixture, '[data-testid="admin-situacao"]');
      expect(service.situacaoUsuario).not.toHaveBeenCalled();
      await fixture.whenStable();
      expect(document.activeElement?.id).toBe('admin-cancelar');
      click(fixture, '#admin-cancelar');
      await fixture.whenStable();
      expect(document.activeElement).toBe(button);
      click(fixture, '[data-testid="admin-situacao"]');
      click(fixture, '#admin-confirmar');
      fixture.componentInstance.confirmar();
      expect(service.situacaoUsuario).toHaveBeenCalledExactlyOnceWith(2, !ativo);
      mutation.next({ ...usuario, ativo: !ativo });
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Situação atualizada com sucesso');
    },
  );
  it.each([401, 403])('explica erro HTTP %s de carregamento e de gravação', (status) => {
    const fixture = render();
    list.error(new HttpErrorResponse({ status }));
    fixture.detectChanges();
    const message = status === 401 ? 'Sua sessão expirou' : 'Você não tem permissão';
    expect(fixture.nativeElement.textContent).toContain(message);
    click(fixture, '#admin-novo');
    userForm(fixture);
    submit(fixture);
    mutation.error(new HttpErrorResponse({ status }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain(message);
  });
  it('lista categorias inativas, valida nome e descrição e cria sem descrição', () => {
    tipo = 'categorias';
    const fixture = render();
    list.next([{ ...categoria, ativa: false }]);
    fixture.detectChanges();
    expect(service.listarCategorias).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.textContent).toContain('Inativa');
    expect(fixture.nativeElement.textContent).toContain('Contas e permissões');
    click(fixture, '#admin-novo');
    field(fixture, '#admin-nome', ' ');
    field(fixture, '#admin-descricao', 'a'.repeat(256));
    submit(fixture);
    expect(service.criarCategoria).not.toHaveBeenCalled();
    expect(query(fixture, '#admin-descricao').getAttribute('aria-invalid')).toBe('true');
    field(fixture, '#admin-nome', '  Equipamentos  ');
    field(fixture, '#admin-descricao', '  ');
    submit(fixture);
    expect(service.criarCategoria).toHaveBeenCalledExactlyOnceWith({
      nome: 'Equipamentos',
      descricao: null,
    });
    mutation.next({ ...categoria, nome: 'Equipamentos', descricao: null });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Registro criado com sucesso');
  });
  it('edita categoria, trata nome duplicado e confirma situação preservando registro', () => {
    tipo = 'categorias';
    const fixture = render();
    list.next([categoria]);
    fixture.detectChanges();
    click(fixture, '[data-testid="admin-editar"]');
    field(fixture, '#admin-nome', 'Novo acesso');
    submit(fixture);
    expect(service.editarCategoria).toHaveBeenCalledWith(8, {
      nome: 'Novo acesso',
      descricao: categoria.descricao,
    });
    mutation.error(new HttpErrorResponse({ status: 409 }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Já existe uma categoria');
    fixture.componentInstance.cancelar();
    fixture.detectChanges();
    click(fixture, '[data-testid="admin-situacao"]');
    expect(fixture.nativeElement.textContent).toContain('O histórico será preservado');
    service.situacaoCategoria.mockReturnValue(of({ ...categoria, ativa: false }));
    click(fixture, '#admin-confirmar');
    expect(service.situacaoCategoria).toHaveBeenCalledWith(8, false);
    expect(fixture.nativeElement.textContent).toContain('Inativa');
    service.situacaoCategoria.mockReturnValue(of(categoria));
    click(fixture, '[data-testid="admin-situacao"]');
    click(fixture, '#admin-confirmar');
    expect(service.situacaoCategoria).toHaveBeenLastCalledWith(8, true);
  });
  it('edita nome e e-mail sem reenviar perfil', () => {
    const fixture = render();
    list.next([usuario]);
    fixture.detectChanges();
    click(fixture, '[data-testid="admin-editar"]');
    field(fixture, '#admin-nome', 'Novo nome');
    submit(fixture);
    expect(service.editarUsuario).toHaveBeenCalledExactlyOnceWith(2, {
      nome: 'Novo nome',
      email: usuario.email,
    });
  });
  it('fecha edição obsoleta, recarrega lista e não repete mutação', () => {
    const fixture = render();
    list.next([usuario]);
    fixture.detectChanges();
    click(fixture, '[data-testid="admin-editar"]');
    field(fixture, '#admin-perfil', 'ATENDENTE');
    submit(fixture);
    click(fixture, '#admin-confirmar');
    service.listarUsuarios.mockReturnValue(of([{ ...usuario, versao: 1 }]));
    mutation.error(
      new HttpErrorResponse({
        status: 409,
        error: {
          violations: [{ field: 'versaoEsperada', message: 'Registro alterado.' }],
        },
      }),
    );
    fixture.detectChanges();
    expect(query(fixture, 'form')).toBeNull();
    expect(query(fixture, 'dialog')).toBeNull();
    expect(service.listarUsuarios).toHaveBeenCalledTimes(2);
    expect(service.editarUsuario).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.textContent).toContain('Abra a edição novamente');
  });
  it('desinscreve GET pendente ao destruir', () => {
    const fixture = render();
    expect(list.observed).toBe(true);
    fixture.destroy();
    expect(list.observed).toBe(false);
  });
  it('desinscreve mutação pendente e ignora retorno após destruir', () => {
    const fixture = render();
    list.next([usuario]);
    fixture.detectChanges();
    click(fixture, '[data-testid="admin-situacao"]');
    click(fixture, '#admin-confirmar');
    expect(mutation.observed).toBe(true);
    fixture.destroy();
    expect(mutation.observed).toBe(false);
    mutation.next({ ...usuario, ativo: false });
  });
  it('usa modal nativo e bloqueia Escape durante mutação', async () => {
    const fixture = render();
    list.next([usuario]);
    fixture.detectChanges();
    click(fixture, '[data-testid="admin-situacao"]');
    await fixture.whenStable();
    const dialog = query<HTMLDialogElement>(fixture, 'dialog');
    expect(dialog.open).toBe(true);
    click(fixture, '#admin-confirmar');
    const event = new Event('cancel', { cancelable: true });
    dialog.dispatchEvent(event);
    fixture.detectChanges();
    expect(event.defaultPrevented).toBe(true);
    expect(query(fixture, 'dialog')).not.toBeNull();
  });
  it('mantém Tab e Shift+Tab dentro da confirmação e descreve alvo e consequência', async () => {
    const fixture = render();
    list.next([usuario]);
    fixture.detectChanges();
    click(fixture, '[data-testid="admin-situacao"]');
    await fixture.whenStable();
    const dialog = query<HTMLDialogElement>(fixture, 'dialog');
    const descricao = query<HTMLElement>(fixture, `#${dialog.getAttribute('aria-describedby')}`);
    expect(descricao.textContent).toContain(usuario.nome);
    expect(descricao.textContent).toContain('perderá o acesso');
    const cancelar = query<HTMLButtonElement>(fixture, '#admin-cancelar');
    const confirmar = query<HTMLButtonElement>(fixture, '#admin-confirmar');
    const tab = (shiftKey: boolean) => {
      const event = new KeyboardEvent('keydown', { key: 'Tab', shiftKey, cancelable: true });
      dialog.dispatchEvent(event);
      return event;
    };
    cancelar.focus();
    expect(tab(false).defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(confirmar);
    tab(true);
    expect(document.activeElement).toBe(cancelar);
    tab(true);
    expect(document.activeElement).toBe(confirmar);
  });
  it('recupera erro de situação e cancela requisição ao destruir componente', () => {
    const fixture = render();
    list.next([usuario]);
    fixture.detectChanges();
    click(fixture, '[data-testid="admin-situacao"]');
    click(fixture, '#admin-confirmar');
    mutation.error(new Error('offline'));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Não foi possível salvar');
    expect(query<HTMLButtonElement>(fixture, '[data-testid="admin-situacao"]').disabled).toBe(
      false,
    );
    fixture.destroy();
    expect(list.observed).toBe(false);
  });
});
