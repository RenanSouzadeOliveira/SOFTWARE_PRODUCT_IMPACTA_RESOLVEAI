import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { ComentariosChamadoComponent } from './comentarios-chamado.component';
import { ComentarioChamado, ComentariosChamado } from './comentarios-chamado.model';
import { ComentariosChamadoService } from './comentarios-chamado.service';

describe('ComentariosChamadoComponent', () => {
  const comment: ComentarioChamado = {
    id: 1,
    conteudo: 'Conteúdo seguro',
    autor: { id: 2, nome: 'Ana', perfil: 'SOLICITANTE' },
    criadoEm: '2026-10-09T12:00:00Z',
  };
  let reads: Subject<ComentariosChamado>[];
  let posting: Subject<ComentarioChamado>;
  const service = { listar: vi.fn(), criar: vi.fn() };
  beforeEach(async () => {
    vi.clearAllMocks();
    reads = [];
    posting = new Subject();
    service.listar.mockImplementation(() => {
      const response = new Subject<ComentariosChamado>();
      reads.push(response);
      return response;
    });
    service.criar.mockReturnValue(posting);
    await TestBed.configureTestingModule({
      imports: [ComentariosChamadoComponent],
      providers: [{ provide: ComentariosChamadoService, useValue: service }],
    }).compileComponents();
  });
  function render() {
    const fixture = TestBed.createComponent(ComentariosChamadoComponent);
    fixture.componentRef.setInput('chamadoId', 10);
    fixture.componentRef.setInput('status', 'ABERTO');
    fixture.detectChanges();
    return fixture;
  }
  function resolve(
    fixture: ComponentFixture<ComentariosChamadoComponent>,
    content: readonly ComentarioChamado[] = [],
    podeComentar = true,
    index = 0,
  ) {
    reads[index].next({ content, podeComentar });
    fixture.detectChanges();
  }
  function input(fixture: ComponentFixture<ComentariosChamadoComponent>, value: string) {
    const textarea = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = value;
    textarea.dispatchEvent(new Event('input'));
    textarea.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
  }
  function submit(fixture: ComponentFixture<ComentariosChamadoComponent>) {
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    fixture.detectChanges();
  }

  it('mostra carregamento, falha independente, repetição e vazio', () => {
    const fixture = render();
    expect(fixture.nativeElement.textContent).toContain('Carregando comentários');
    reads[0].error(new HttpErrorResponse({ status: 500 }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    fixture.nativeElement.querySelector('button').click();
    resolve(fixture, [], true, 1);
    expect(service.listar).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain('Nenhum comentário registrado');
  });

  it('preserva ordem, autoria, perfil e texto escapado sem edição ou exclusão', () => {
    const fixture = render();
    resolve(
      fixture,
      [
        comment,
        {
          ...comment,
          id: 2,
          conteudo: '<img src=x onerror=alert(1)>',
          autor: { id: 3, nome: 'Bia', perfil: 'ATENDENTE' },
        },
        { ...comment, id: 3, autor: { id: 4, nome: 'Caio', perfil: 'ADMIN' } },
      ],
      false,
    );
    const nodes = fixture.nativeElement.querySelectorAll('[data-testid="comment"]');
    expect(nodes[0].textContent).toContain('Ana · Solicitante');
    expect(nodes[1].textContent).toContain('Bia · Atendente');
    expect(nodes[2].textContent).toContain('Caio · Administrador');
    expect(nodes[1].textContent).toContain('<img src=x onerror=alert(1)>');
    expect(fixture.nativeElement.querySelector('img')).toBeNull();
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
    expect(fixture.nativeElement.querySelector('time')?.getAttribute('datetime')).toBe(
      comment.criadoEm,
    );
  });

  it('mostra leitura do chamado fechado e recarrega permissão após mudança de status', () => {
    const fixture = render();
    resolve(fixture);
    expect(fixture.nativeElement.querySelector('form')).not.toBeNull();
    fixture.componentRef.setInput('status', 'FECHADO');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
    resolve(fixture, [comment], false, 1);
    expect(fixture.nativeElement.textContent).toContain('Chamado fechado');
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });

  it('valida comprimento aparado, aceita limites e impede duplicidade', () => {
    const fixture = render();
    resolve(fixture);
    input(fixture, '   ');
    expect(fixture.nativeElement.querySelector('#publish-comment').disabled).toBe(true);
    input(fixture, 'x'.repeat(2001));
    expect(fixture.nativeElement.querySelector('#publish-comment').disabled).toBe(true);
    input(fixture, 'x'.repeat(2000));
    expect(fixture.nativeElement.querySelector('#publish-comment').disabled).toBe(false);
    input(fixture, '  x  ');
    submit(fixture);
    submit(fixture);
    expect(service.criar).toHaveBeenCalledExactlyOnceWith(10, 'x');
    expect(fixture.nativeElement.querySelector('#publish-comment').disabled).toBe(true);
    posting.next({ ...comment, conteudo: 'x' });
    posting.complete();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('[data-testid="comment"]')).toHaveLength(1);
    expect(fixture.nativeElement.querySelector('textarea').value).toBe('');
    expect(fixture.nativeElement.textContent).toContain('publicado com sucesso');
  });

  it('preserva texto após erro e permite tentar novamente', () => {
    const fixture = render();
    resolve(fixture);
    input(fixture, 'Texto para preservar');
    submit(fixture);
    posting.error(new HttpErrorResponse({ status: 500 }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('textarea').value).toBe('Texto para preservar');
    expect(fixture.nativeElement.querySelector('#publish-comment').disabled).toBe(false);
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('recarrega após publicação se o status mudou durante o envio', () => {
    const fixture = render();
    resolve(fixture);
    input(fixture, 'Comentário em andamento');
    submit(fixture);
    fixture.componentRef.setInput('status', 'RESOLVIDO');
    fixture.detectChanges();
    posting.next(comment);
    posting.complete();
    fixture.detectChanges();
    expect(reads[1].observed).toBe(false);
    expect(service.listar).toHaveBeenCalledTimes(3);
    resolve(fixture, [comment], true, 2);
    expect(fixture.nativeElement.querySelectorAll('[data-testid="comment"]')).toHaveLength(1);
  });

  it('em conflito limpa permissão obsoleta, atualiza comentários e solicita detalhe novo', () => {
    const fixture = render();
    const conflict = vi.fn();
    fixture.componentInstance.conflito.subscribe(conflict);
    resolve(fixture);
    input(fixture, 'Comentário');
    submit(fixture);
    posting.error(new HttpErrorResponse({ status: 409 }));
    fixture.detectChanges();
    expect(conflict).toHaveBeenCalledOnce();
    expect(service.listar).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
    resolve(fixture, [comment], false, 1);
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });

  it('cancela leitura e publicação antigas e não vaza texto entre chamados', () => {
    const fixture = render();
    resolve(fixture, [comment]);
    input(fixture, 'Rascunho antigo');
    submit(fixture);
    fixture.componentRef.setInput('chamadoId', 11);
    fixture.detectChanges();
    expect(reads[0].observed).toBe(false);
    expect(posting.observed).toBe(false);
    reads[0].next({ content: [comment], podeComentar: true });
    posting.next(comment);
    resolve(fixture, [], true, 1);
    expect(service.listar).toHaveBeenLastCalledWith(11);
    expect(fixture.nativeElement.querySelectorAll('[data-testid="comment"]')).toHaveLength(0);
    expect(fixture.nativeElement.querySelector('textarea').value).toBe('');
    expect(fixture.nativeElement.querySelector('#publish-comment').disabled).toBe(true);
  });
});
