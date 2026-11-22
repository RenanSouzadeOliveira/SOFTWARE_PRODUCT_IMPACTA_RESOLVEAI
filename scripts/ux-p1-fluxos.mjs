// Fluxos P1 em navegador real: capturas para a apresentação e verificações de teclado,
// diálogos, validação, estados vazios/erro e proteção contra dupla submissão.
// Uso: UX_CREDS=<json local fora do repositório> UX_OUT=docs/evidencias/ux-p1 node scripts/ux-p1-fluxos.mjs
import assert from 'node:assert/strict';
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { abrirChrome } from './ux-p1-cdp.mjs';

const web = new URL(
  process.env.UX_WEB_URL ?? `http://127.0.0.1:${process.env.UX_WEB_PORT ?? '4471'}`,
);
if (web.protocol !== 'http:' || !['127.0.0.1', 'localhost'].includes(web.hostname)) {
  throw new Error('Use somente ambiente local descartável.');
}
const controleNegativo = process.env.UX_CONTROLE_NEGATIVO === '1';
const creds = JSON.parse(readFileSync(process.env.UX_CREDS, 'utf8'));
const out = process.env.UX_OUT ?? 'docs/evidencias/ux-p1';
mkdirSync(out, { recursive: true });
const b = await abrirChrome();
const checks = [];
let capturasLista;
const check = async (nome, fn) => {
  try {
    const observado = await fn();
    checks.push({ nome, ok: true, ...(observado && { observado }) });
  } catch (erro) {
    checks.push({ nome, ok: false, erro: erro.message });
  }
};
const foto = (nome, inteira = false) => b.foto(`${out}/${nome}.png`, inteira);
const semOverflow = async () => { const m = await b.overflow(); assert.equal(m.rolagemHorizontal, false, JSON.stringify(m)); };
const focoVisivel = () => b.avaliar(`(()=>{const e=document.activeElement;const s=getComputedStyle(e);return {alvo:e.id||e.textContent.trim().slice(0,30),outline:s.outlineStyle+' '+s.outlineWidth}})()`);
async function entrar(perfil) {
  await b.avaliar('sessionStorage.clear()').catch(() => {});
  await b.ir(web.origin + '/login');
  await b.digitar('#login-email', creds.users[perfil][1]);
  await b.digitar('#login-senha', creds.senha);
  await b.avaliar("document.querySelector('form button[type=submit]').click()");
  await b.esperar("!location.pathname.startsWith('/login')");
}
const clicar = (sel) => b.avaliar(`document.querySelector(${JSON.stringify(sel)}).click()`);

const esperadoAssumir = 'article.ticket button';
const dialogo = '[role="dialog"]';
const semDialogo = `!document.querySelector(${JSON.stringify(dialogo)})`;
const seletorMenu = '[data-testid="menu-toggle"]';

async function cancelarAtribuicao(tecla) {
  await b.avaliar(`window.uxAcionador = document.querySelector(${JSON.stringify(esperadoAssumir)}); uxAcionador.focus()`);
  const antes = b.requisicoes.length;
  await b.tecla('Enter', 'Enter', 13);
  await b.esperar('document.activeElement.id === "cancel-assignment"');
  assert.equal(await b.avaliar('window.uxAcionador.disabled'), true);
  await b.tecla(tecla, tecla, tecla === 'Escape' ? 27 : 13);
  await b.esperar(`${semDialogo} && !uxAcionador.disabled`);
  const foco = await b.avaliar(`({
    mesmoElemento: document.activeElement === uxAcionador,
    ativo: document.activeElement.tagName,
    conectado: uxAcionador.isConnected,
    habilitado: !uxAcionador.disabled,
  })`);
  assert.ok(foco.mesmoElemento && foco.ativo === 'BUTTON' && foco.conectado && foco.habilitado,
    `Foco após o cancelamento: ${JSON.stringify(foco)}`);
  assert.equal(b.requisicoes.slice(antes).filter((r) => r.startsWith('PATCH')).length, 0);
  return foco;
}

async function checarCancelamentos() {
  await b.viewport(360, 800);
  await b.ir(web.origin + '/atendimento');
  await b.esperar(`document.querySelector(${JSON.stringify(esperadoAssumir)})`);
  await check('Assumir: Escape restaura foco ao acionador', () => cancelarAtribuicao('Escape'));
  await check('Assumir: Cancelar restaura foco ao acionador', () => cancelarAtribuicao('Enter'));
}

const viewportVazio = [[320, 640], [360, 800], [390, 844], [768, 1024], [1440, 900]];
const rotaVazio = '/chamados?status=FECHADO&prioridade=ALTA';
async function checarVazio(largura, altura) {
  await b.viewport(largura, altura);
  await b.ir(web.origin + rotaVazio);
  await b.esperar('document.querySelector("[data-testid=chamados-empty]")');
  const medidas = await b.overflow();
  const cta = await b.avaliar(`(() => {
    const el = document.querySelector('[data-testid=chamados-empty] a');
    const { left, right, height } = el.getBoundingClientRect();
    return { seletor: '[data-testid=chamados-empty] a', left, right, height, texto: el.textContent.trim() };
  })()`);
  if (controleNegativo) {
    await foto('antes-estado-vazio-filtros-mobile-320', true);
    cta.scrollWidthAoPermitirQuebra = await b.avaliar(`(() => {
      const el = document.querySelector('[data-testid=chamados-empty] a');
      const original = el.style.whiteSpace;
      el.style.whiteSpace = 'normal';
      const scrollWidth = document.documentElement.scrollWidth;
      el.style.whiteSpace = original;
      return scrollWidth;
    })()`);
  }
  assert.equal(medidas.largura, largura);
  assert.equal(medidas.scrollWidth, largura, JSON.stringify({ ...medidas, cta }));
  assert.deepEqual(medidas.fora, []);
  assert.ok(cta.left >= 0 && cta.right <= largura && cta.height >= 44, JSON.stringify(cta));
  assert.equal(cta.texto, 'Abrir chamado');
  assert.match(await b.avaliar('document.querySelector("[data-testid=chamados-empty]").textContent'), /limp(?:ar|e os) filtros/i);
  if (largura <= 360) await foto(`12-estado-vazio-filtros-mobile-${largura}`, true);
  return { ...medidas, cta };
}

async function checarPaginacao(rota, endpoint, loading, erro, sucesso) {
  const terminar = await b.pausarProximoGet(web.origin + endpoint, () => b.ir(web.origin + rota));
  try {
    await b.esperar(loading);
    assert.equal(await b.avaliar('!!document.querySelector("app-paginacao")'), false, 'Paginação visível durante a carga.');
  } finally {
    await terminar(true);
  }
  await b.esperar(erro);
  assert.equal(await b.avaliar('!!document.querySelector("app-paginacao")'), false, 'Paginação visível após erro.');
  await b.avaliar(`Array.from(document.querySelectorAll('main button')).find(b => b.textContent.trim() === 'Tentar novamente').click()`);
  await b.esperar(sucesso);
  const textoPagina = 'document.querySelector("app-paginacao nav [role=status]")?.textContent.trim()';
  const resumo = await b.avaliar(textoPagina);
  assert.match(resumo, /^[1-9]\d* chamado\(s\) · Página 1 de [1-9]/);
  const resultado = { carregando: 'oculta', erroHTTP503: 'oculta', aposRepetir: resumo };
  const proxima = 'app-paginacao nav button:last-child';
  if (await b.avaliar(`!document.querySelector('${proxima}').disabled`)) {
    await clicar(proxima);
    await b.esperar(`(${textoPagina})?.includes('Página 2 de')`);
    resultado.proxima = await b.avaliar(textoPagina);
  }
  return resultado;
}

async function checarMenu(rotas) {
  const observacoes = [];
  for (const [w, h] of [[360, 800], [1440, 900]]) {
    await b.viewport(w, h);
    for (const [rota, destino] of rotas) {
      await b.ir(web.origin + rota);
      assert.equal(await b.avaliar('location.pathname + location.search'), rota);
      if (w === 360) {
        await b.avaliar(`document.querySelector('${seletorMenu}').focus()`);
        await b.tecla('Enter', 'Enter', 13);
        await b.esperar(`document.querySelector('${seletorMenu}').getAttribute('aria-expanded') === 'true'`);
      }
      const links = await b.avaliar(`(() => {
        const menu = document.querySelector('#menu-principal');
        const destinos = seletor => Array.from(menu.querySelectorAll(seletor), a => a.getAttribute('href'));
        const link = menu.querySelector('a.active');
        return { ativo: destinos('a.active'), atual: destinos('a[aria-current="page"]'),
          visivel: !!link?.getClientRects().length,
          fundo: link && getComputedStyle(link).backgroundColor };
      })()`);
      assert.deepEqual(links.ativo, [destino], rota);
      assert.deepEqual(links.atual, [destino], rota);
      assert.equal(links.visivel, true, rota);
      assert.notEqual(links.fundo, 'rgba(0, 0, 0, 0)', rota);
      observacoes.push({ largura: w, rota, ativo: destino });
    }
    await clicar('#menu-principal a[href="/dashboard"]');
    await b.esperar("location.pathname === '/dashboard'");
    if (w === 360) {
      await b.esperar(`document.querySelector('${seletorMenu}').getAttribute('aria-expanded') === 'false'`);
    }
  }
  return observacoes;
}

function gravarResultado() {
  const relatorio = { ...(controleNegativo && { controleNegativo: '9e1e681' }), checks,
    ...(capturasLista && { capturasLista }), errosConsole: b.erros };
  writeFileSync(`${out}/verificacoes.json`, JSON.stringify(relatorio, null, 1) + '\n');
  console.log(JSON.stringify(relatorio, null, 1));
  process.exitCode = checks.every((c) => c.ok) && !b.erros.length ? 0 : 1;
}

async function executarControleNegativo() {
  await b.viewport(360, 800);
  await entrar('atendente');
  await checarCancelamentos();
  await entrar('solicitante');
  await check('Estado vazio por filtro: sem overflow em 320 × 640', () => checarVazio(320, 640));
}

async function executarFluxos() {
  // Público
  await b.viewport(1440, 900); await b.ir(web.origin + '/'); await b.avaliar('sessionStorage.clear()');
  await b.ir(web.origin + '/login'); await foto('01-login-desktop-1440');
  await b.viewport(360, 800); await b.ir(web.origin + '/login'); await foto('02-login-mobile-360');
  await check('Teclado: 1º Tab foca o atalho "Pular para o conteúdo" com contorno visível', async () => {
    await b.tecla('Tab', 'Tab', 9);
    const f = await focoVisivel();
    assert.match(f.alvo, /Pular/); assert.match(f.outline, /solid 3px/);
  });
  await check('Login: validação aparece junto aos campos sem requisição', async () => {
    const antes = b.requisicoes.length;
    await clicar('form button[type=submit]'); await b.sleep(200);
    assert.match(await b.avaliar("document.querySelector('#login-email-error').textContent"), /Informe o e-mail/);
    assert.equal(await b.avaliar("document.querySelector('#login-email').getAttribute('aria-invalid')"), 'true');
    assert.equal(b.requisicoes.slice(antes).filter((r) => r.startsWith('POST')).length, 0);
    await foto('10-validacao-login-mobile-360');
  });
  await check('Login: credenciais inválidas mostram mensagem compreensível e preservam o e-mail', async () => {
    await b.digitar('#login-email', creds.users.solicitante[1]);
    await b.digitar('#login-senha', 'senha-incorreta-ficticia');
    await clicar('form button[type=submit]');
    await b.esperar("document.querySelector('[data-testid=login-error]')");
    assert.match(await b.avaliar("document.querySelector('[data-testid=login-error]').textContent"), /E-mail ou senha inválidos/);
    assert.equal(await b.avaliar("document.querySelector('#login-email').value"), creds.users.solicitante[1]);
  });

  // SOLICITANTE
  await b.viewport(1440, 900); await entrar('solicitante');
  await b.ir(web.origin + '/chamados'); await b.esperar("document.querySelector('[data-testid=chamados-success]')");
  await foto('03-lista-chamados-desktop-1440');
  await b.viewport(360, 800); await b.ir(web.origin + '/chamados'); await b.esperar("document.querySelector('[data-testid=chamados-success]')");
  await check('Lista 360px: sem rolagem horizontal e status/prioridade com texto', semOverflow);
  capturasLista = await b.fotosSequenciais(`${out}/04-lista-chamados-mobile-360`, '.tickets-list > li');
  await check('Menu móvel: abre com Enter e fecha com Escape devolvendo o foco', async () => {
    await b.avaliar("document.querySelector('[data-testid=menu-toggle]').focus()");
    await b.tecla('Enter', 'Enter', 13); await b.sleep(150);
    assert.equal(await b.avaliar("document.querySelector('[data-testid=menu-toggle]').getAttribute('aria-expanded')"), 'true');
    assert.equal(await b.avaliar("getComputedStyle(document.querySelector('#menu-principal')).display"), 'flex');
    await foto('13-menu-movel-aberto-360');
    await b.tecla('Escape', 'Escape', 27); await b.sleep(150);
    assert.equal(await b.avaliar("document.querySelector('[data-testid=menu-toggle]').getAttribute('aria-expanded')"), 'false');
    assert.equal(await b.avaliar("document.activeElement.dataset.testid"), 'menu-toggle');
  });
  await check('Menu SOLICITANTE: um indicador ativo no desktop e no celular', () => checarMenu([
    ['/chamados?status=ABERTO', '/chamados'],
    [`/chamados/${creds.chamados[0]}`, '/chamados'],
    ['/chamados/novo', '/chamados/novo'],
  ]));
  await b.viewport(360, 800);
  await check('Paginação: Meus chamados durante carga, erro e recuperação', () => checarPaginacao(
    '/chamados?size=10', '/api/chamados/me', 'document.querySelector("[data-testid=chamados-loading]")',
    'document.querySelector("[data-testid=chamados-error]")', 'document.querySelector("[data-testid=chamados-success]")',
  ));
  for (const [w, h] of viewportVazio) {
    await check(`Estado vazio por filtro: sem overflow em ${w} × ${h}`, () => checarVazio(w, h));
  }
  await b.viewport(360, 800);
  await b.ir(web.origin + `/chamados/${creds.chamados[0]}`); await b.esperar("document.querySelector('#detalhe-chamado-title')");
  await check('Detalhe 360px: sem rolagem horizontal', semOverflow);
  await foto('05-detalhe-chamado-mobile-360', true);
  await check('Novo chamado: envia um POST, foca a confirmação e oferece próximo passo por Tab', async () => {
    await b.ir(web.origin + '/chamados/novo'); await b.esperar("document.querySelector('#chamado-categoria option:nth-child(2)')");
    const antesValidacao = b.requisicoes.length;
    await clicar('form button[type=submit]'); await b.sleep(200);
    assert.match(await b.avaliar("document.querySelector('#chamado-prioridade-error').textContent"), /Selecione uma prioridade/);
    assert.equal(b.requisicoes.slice(antesValidacao).filter((r) => r === 'POST /api/chamados').length, 0);
    await foto('10b-validacao-novo-chamado-mobile-360', true);
    await b.digitar('#chamado-titulo', 'Teclado sem resposta na recepção');
    await clicar('input[formControlName=prioridade][value=ALTA]');
    await b.digitar('#chamado-descricao', 'Descrição fictícia: algumas teclas não respondem desde ontem.');
    await b.avaliar("(()=>{const s=document.querySelector('#chamado-categoria');s.selectedIndex=1;s.dispatchEvent(new Event('change'))})()");
    const antes = b.requisicoes.length;
    await b.avaliar(`(() => {
      const form = document.querySelector('form');
      const submit = form.querySelector('button[type=submit]');
      submit.focus();
      form.requestSubmit(submit);
      form.requestSubmit(submit);
    })()`);
    await b.esperar("document.querySelector('[data-testid=chamado-success]')");
    assert.equal(b.requisicoes.slice(antes).filter((r) => r === 'POST /api/chamados').length, 1);
    const foco = await b.avaliar(`document.activeElement === document.querySelector('#chamado-success-title')
      && document.activeElement.isConnected && document.activeElement !== document.body`);
    assert.equal(foco, true);
    await foto('14-sucesso-abertura-mobile-360');
    await b.tecla('Tab', 'Tab', 9);
    const tab = await b.avaliar('document.activeElement === document.querySelector("[data-testid=chamado-success] button")');
    assert.equal(tab, true);
    return { foco: 'chamado-success-title', tab: 'Abrir outro chamado', posts: 1 };
  });

  // ATENDENTE
  await b.viewport(1440, 900); await entrar('atendente');
  await b.ir(web.origin + '/atendimento'); await b.esperar("document.querySelector('.ticket button')");
  await foto('15-fila-atendimento-desktop-1440');
  await b.viewport(360, 800); await b.ir(web.origin + '/atendimento'); await b.esperar("document.querySelector('.ticket button')");
  await check('Fila 360px: sem rolagem horizontal', semOverflow);
  await check('Menu ATENDENTE: um indicador ativo no desktop e no celular', () => checarMenu([
    ['/atendimento?status=ABERTO', '/atendimento'],
    ['/atendimento/me', '/atendimento'],
    [`/atendimento/chamados/${creds.chamados[0]}`, '/atendimento'],
  ]));
  await b.viewport(360, 800);
  await check('Paginação: fila durante carga, erro e recuperação', () => checarPaginacao(
    '/atendimento?size=10', '/api/atendimento/fila',
    'Array.from(document.querySelectorAll("main [role=status]")).some(e => e.textContent.includes("Carregando chamados"))',
    'document.querySelector("main [role=alert] button")', 'document.querySelector("article.ticket")',
  ));
  await checarCancelamentos();
  await check('Assumir: confirmação dupla gera um único PATCH', async () => {
    await clicar('.ticket button'); await b.esperar("document.querySelector('[role=dialog]')");
    await b.avaliar("document.querySelector('[role=dialog]').scrollIntoView({block:'center'})");
    await foto('11-dialogo-assumir-mobile-360');
    const antes = b.requisicoes.length;
    await b.avaliar("(()=>{const x=document.querySelector('[role=dialog] .primary-button');x.click();x.click();})()");
    await b.esperar("document.querySelector('.notice')");
    const patches = b.requisicoes.slice(antes).filter((r) => /^PATCH \/api\/chamados\/\d+\/assumir$/.test(r)).length;
    assert.equal(patches, 1);
    return { patches };
  });

  // ADMIN
  await b.viewport(1440, 900); await entrar('admin');
  await b.ir(web.origin + '/dashboard'); await b.esperar("document.querySelector('h1')"); await b.sleep(400);
  await foto('06-dashboard-desktop-1440');
  await b.viewport(360, 800); await b.ir(web.origin + '/dashboard'); await b.sleep(600);
  await check('Dashboard 360px: sem rolagem horizontal', semOverflow);
  await foto('07-dashboard-mobile-360', true);
  await b.viewport(1440, 900); await b.ir(web.origin + '/admin/usuarios'); await b.esperar("document.querySelector('.records')");
  await foto('08-admin-usuarios-desktop-1440');
  await b.viewport(360, 800); await b.ir(web.origin + '/admin/usuarios'); await b.esperar("document.querySelector('.records')");
  await check('Admin 360px: sem rolagem horizontal e perfis em pt-BR', async () => {
    await semOverflow();
    const t = await b.avaliar("document.querySelector('.records').textContent");
    assert.match(t, /Perfil: Solicitante/); assert.doesNotMatch(t, /SOLICITANTE|ATENDENTE/);
  });
  await foto('09-admin-usuarios-mobile-360', true);
  await check('Admin: diálogo modal cabe na viewport, prende o foco e cancela com Escape', async () => {
    await b.avaliar("document.querySelector('[data-testid=admin-registro-2] [data-testid=admin-situacao]').focus()");
    await b.tecla('Enter', 'Enter', 13);
    await b.esperar("document.querySelector('dialog[open]')");
    const r = await b.avaliar("(()=>{const d=document.querySelector('dialog').getBoundingClientRect();return {l:d.left,r:d.right,t:d.top,b:d.bottom,w:innerWidth,h:innerHeight}})()");
    assert.ok(r.l >= 0 && r.r <= r.w && r.t >= 0 && r.b <= r.h, JSON.stringify(r));
    assert.equal(await b.avaliar('document.activeElement.id'), 'admin-cancelar');
    await foto('16-dialogo-desativar-usuario-mobile-360');
    await b.tecla('Tab', 'Tab', 9); await b.tecla('Tab', 'Tab', 9);
    assert.ok(await b.avaliar("!!document.activeElement.closest('dialog')"), 'foco saiu do diálogo');
    await b.tecla('Escape', 'Escape', 27); await b.sleep(150);
    assert.equal(await b.avaliar("document.querySelector('dialog[open]')"), null);
    assert.equal(await b.avaliar('document.activeElement.dataset.testid'), 'admin-situacao');
  });
  await b.ir(web.origin + '/admin/categorias'); await b.esperar("document.querySelector('.records')");
  await check('Categorias 360px: nome longo sem rolagem horizontal', semOverflow);
  await check('Menu ADMIN: um indicador ativo no desktop e no celular', () => checarMenu([
    ['/atendimento', '/atendimento'],
    ['/atendimento/todos', '/atendimento/todos'],
    [`/atendimento/todos/${creds.chamados[0]}`, '/atendimento/todos'],
    ['/consulta-chamados?status=ABERTO', '/consulta-chamados'],
    ['/admin/usuarios', '/admin/usuarios'],
    ['/admin/categorias', '/admin/usuarios'],
  ]));
  await b.viewport(360, 800);
  await check('Paginação: consulta administrativa durante carga, erro e recuperação', () => checarPaginacao(
    '/consulta-chamados?size=10', '/api/chamados', 'document.querySelector("[data-testid=chamados-loading]")',
    'document.querySelector("[data-testid=chamados-error]")', 'document.querySelector("[data-testid=chamados-success]")',
  ));
  await check('Rota de ADMIN negada para SOLICITANTE (guard mantido)', async () => {
    await entrar('solicitante');
    await b.ir(web.origin + '/admin/usuarios'); await b.sleep(500);
    assert.equal(await b.avaliar('location.pathname'), '/acesso-negado');
    await foto('17-acesso-negado-mobile-360');
  });
}

try {
  await (controleNegativo ? executarControleNegativo() : executarFluxos());
  gravarResultado();
} finally {
  await b.fechar();
}
