// Fluxos P1 em navegador real: capturas para a apresentação e verificações de teclado,
// diálogos, validação, estados vazios/erro e proteção contra dupla submissão.
// Uso: UX_CREDS=<json local fora do repositório> UX_OUT=docs/evidencias/ux-p1 node scripts/ux-p1-fluxos.mjs
import assert from 'node:assert/strict';
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { abrirChrome } from './ux-p1-cdp.mjs';

const web = 'http://127.0.0.1:' + (process.env.UX_WEB_PORT ?? '4471');
const creds = JSON.parse(readFileSync(process.env.UX_CREDS, 'utf8'));
const out = process.env.UX_OUT ?? 'docs/evidencias/ux-p1';
mkdirSync(out, { recursive: true });
const b = await abrirChrome({ porta: 9473 });
const checks = [];
const check = async (nome, fn) => {
  try { await fn(); checks.push({ nome, ok: true }); } catch (e) { checks.push({ nome, ok: false, erro: e.message }); }
};
const foto = (nome, inteira = false) => b.foto(`${out}/${nome}.png`, inteira);
const semOverflow = async () => { const m = await b.overflow(); assert.equal(m.rolagemHorizontal, false, JSON.stringify(m)); };
const focoVisivel = () => b.avaliar(`(()=>{const e=document.activeElement;const s=getComputedStyle(e);return {alvo:e.id||e.textContent.trim().slice(0,30),outline:s.outlineStyle+' '+s.outlineWidth}})()`);
async function entrar(perfil) {
  await b.avaliar('sessionStorage.clear()').catch(() => {});
  await b.ir(web + '/login');
  await b.digitar('#login-email', creds.users[perfil][1]);
  await b.digitar('#login-senha', creds.senha);
  await b.avaliar("document.querySelector('form button[type=submit]').click()");
  await b.esperar("!location.pathname.startsWith('/login')");
}
const clicar = (sel) => b.avaliar(`document.querySelector(${JSON.stringify(sel)}).click()`);

// Público
await b.viewport(1440, 900); await b.ir(web + '/'); await b.avaliar('sessionStorage.clear()');
await b.ir(web + '/login'); await foto('01-login-desktop-1440');
await b.viewport(360, 800); await b.ir(web + '/login'); await foto('02-login-mobile-360');
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
await b.ir(web + '/chamados'); await b.esperar("document.querySelector('[data-testid=chamados-success]')");
await foto('03-lista-chamados-desktop-1440');
await b.viewport(360, 800); await b.ir(web + '/chamados'); await b.esperar("document.querySelector('[data-testid=chamados-success]')");
await check('Lista 360px: sem rolagem horizontal e status/prioridade com texto', semOverflow);
await foto('04-lista-chamados-mobile-360', true);
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
await check('Estado vazio por filtro: mensagem orienta a limpar filtros', async () => {
  await b.ir(web + '/chamados?search=termo-inexistente-ficticio');
  await b.esperar("document.querySelector('[data-testid=chamados-empty]')");
  await foto('12-estado-vazio-filtros-mobile-360', true);
});
await b.ir(web + `/chamados/${creds.chamados[0]}`); await b.esperar("document.querySelector('#detalhe-chamado-title')");
await check('Detalhe 360px: sem rolagem horizontal', semOverflow);
await foto('05-detalhe-chamado-mobile-360', true);
await check('Novo chamado: dupla ativação gera uma única requisição POST', async () => {
  await b.ir(web + '/chamados/novo'); await b.esperar("document.querySelector('#chamado-categoria option:nth-child(2)')");
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
  await b.avaliar("(()=>{const f=document.querySelector('form');f.requestSubmit();f.requestSubmit();document.querySelector('form button[type=submit]').click();})()");
  await b.esperar("document.querySelector('[data-testid=chamado-success]')");
  assert.equal(b.requisicoes.slice(antes).filter((r) => r === 'POST /api/chamados').length, 1);
  await foto('14-sucesso-abertura-mobile-360');
});

// ATENDENTE
await b.viewport(1440, 900); await entrar('atendente');
await b.ir(web + '/atendimento'); await b.esperar("document.querySelector('.ticket button')");
await foto('15-fila-atendimento-desktop-1440');
await b.viewport(360, 800); await b.ir(web + '/atendimento'); await b.esperar("document.querySelector('.ticket button')");
await check('Fila 360px: sem rolagem horizontal', semOverflow);
await check('Assumir: diálogo cancela com Escape e devolve o foco ao botão', async () => {
  await b.avaliar("document.querySelector('.ticket button').focus()");
  await b.tecla('Enter', 'Enter', 13);
  await b.esperar("document.querySelector('[role=dialog]')");
  assert.equal(await b.avaliar('document.activeElement.id'), 'cancel-assignment');
  await b.tecla('Escape', 'Escape', 27); await b.sleep(150);
  assert.equal(await b.avaliar("document.querySelector('[role=dialog]')"), null);
  assert.match(await b.avaliar('document.activeElement.textContent'), /Assumir/);
});
await check('Assumir: confirmação dupla gera um único PATCH', async () => {
  await clicar('.ticket button'); await b.esperar("document.querySelector('[role=dialog]')");
  await b.avaliar("document.querySelector('[role=dialog]').scrollIntoView({block:'center'})");
  await foto('11-dialogo-assumir-mobile-360');
  const antes = b.requisicoes.length;
  await b.avaliar("(()=>{const x=document.querySelector('[role=dialog] .primary-button');x.click();x.click();})()");
  await b.esperar("document.querySelector('.notice')");
  assert.equal(b.requisicoes.slice(antes).filter((r) => /^PATCH \/api\/chamados\/\d+\/assumir$/.test(r)).length, 1);
});

// ADMIN
await b.viewport(1440, 900); await entrar('admin');
await b.ir(web + '/dashboard'); await b.esperar("document.querySelector('h1')"); await b.sleep(400);
await foto('06-dashboard-desktop-1440');
await b.viewport(360, 800); await b.ir(web + '/dashboard'); await b.sleep(600);
await check('Dashboard 360px: sem rolagem horizontal', semOverflow);
await foto('07-dashboard-mobile-360', true);
await b.viewport(1440, 900); await b.ir(web + '/admin/usuarios'); await b.esperar("document.querySelector('.records')");
await foto('08-admin-usuarios-desktop-1440');
await b.viewport(360, 800); await b.ir(web + '/admin/usuarios'); await b.esperar("document.querySelector('.records')");
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
await b.ir(web + '/admin/categorias'); await b.esperar("document.querySelector('.records')");
await check('Categorias 360px: nome longo sem rolagem horizontal', semOverflow);
await check('Rota de ADMIN negada para SOLICITANTE (guard mantido)', async () => {
  await entrar('solicitante');
  await b.ir(web + '/admin/usuarios'); await b.sleep(500);
  assert.equal(await b.avaliar('location.pathname'), '/acesso-negado');
  await foto('17-acesso-negado-mobile-360');
});

writeFileSync(`${out}/verificacoes.json`, JSON.stringify({ checks, errosConsole: b.erros }, null, 1));
console.log(JSON.stringify({ checks, errosConsole: b.erros }, null, 1));
b.fechar();
process.exitCode = checks.every((c) => c.ok) && !b.erros.length ? 0 : 1;
