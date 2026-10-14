#!/usr/bin/env node
// Browser evidence for a disposable AC2 environment. Never use production fixtures.
import assert from 'node:assert/strict';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const args = process.argv.slice(2);
if (args.includes('--help')) {
  console.log('Usage: node scripts/ac2-browser.mjs --fixtures /tmp/fixtures.json --output-dir /tmp/ac2-browser\nAC2_BROWSER_URL defaults to http://127.0.0.1:4200. AC2_PLAYWRIGHT_MODULE overrides the bundled Playwright module.\nCreates local screenshots, assertion report and a technical silent video; does not publish evidence.');
  process.exit(0);
}
function option(name) {
  const position = args.indexOf(name);
  assert(position >= 0 && args[position + 1], `Required argument: ${name}`);
  return args[position + 1];
}
const fixtures = JSON.parse(await readFile(option('--fixtures'), 'utf8'));
const output = resolve(option('--output-dir'));
const browserUrl = process.env.AC2_BROWSER_URL ?? 'http://127.0.0.1:4200';
const modulePath = process.env.AC2_PLAYWRIGHT_MODULE ?? '/home/renansouzadeoliveira/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs';
const { chromium } = await import(pathToFileURL(modulePath).href);
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true, slowMo: 90 });
const context = await browser.newContext({ viewport: { width: 1280, height: 720 }, recordVideo: { dir: output, size: { width: 1280, height: 720 } } });
const page = await context.newPage();
page.setDefaultTimeout(15000);
const report = { kind: 'AC2 integrated browser evidence', video: 'Technical silent video, local only', assertions: [] };
let fault = null;
let postCount = 0;
let postDelay = false;
const sleep = ms => new Promise(resolveSleep => setTimeout(resolveSleep, ms));
const checkpoint = async name => {
  report.assertions.push(name);
  console.log(`PASS ${name}`);
  await page.screenshot({ path: resolve(output, `${String(report.assertions.length).padStart(2, '0')}.png`), fullPage: true });
};
await page.route('**/api/**', async route => {
  const request = route.request();
  const original = new URL(request.url());
  const suffix = original.pathname.slice(original.pathname.indexOf('/api') + 4);
  if (fault && fault.matches(request, suffix)) {
    const current = fault;
    fault = null;
    if (current.delay) await sleep(current.delay);
    await route.fulfill({ status: current.status ?? 200, contentType: 'application/json', body: JSON.stringify(current.body) });
    return;
  }
  if (request.method() === 'POST' && suffix.endsWith('/comentarios')) {
    postCount++;
    if (postDelay) await sleep(700);
  }
  const apiRoot = fixtures.apiUrl.replace(/\/$/, '').replace(/\/api$/, '');
  const response = await route.fetch({ url: `${apiRoot}/api${suffix}${original.search}` });
  await route.fulfill({ response });
});
async function login(role) {
  const account = fixtures.accounts.find(item => item.role === role);
  assert(account, `Missing ${role} fixture`);
  await page.goto(`${browserUrl}/login`);
  await page.evaluate(() => sessionStorage.clear());
  await page.reload();
  await page.getByLabel('E-mail', { exact: true }).fill(account.email);
  await page.getByLabel('Senha', { exact: true }).fill(fixtures.password);
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await page.waitForURL(url => !url.pathname.endsWith('/login'));
}
async function detail(id, attendant = false) {
  await page.goto(`${browserUrl}/${attendant ? 'atendimento/chamados' : 'chamados'}/${id}`);
  await page.getByTestId('chamado-detail-success').waitFor();
  await page.locator('.timeline').waitFor();
}
async function transition(action, text, status) {
  await page.locator(`[data-action="${action}"]`).click();
  if (text) await page.locator('#justification').fill(text);
  await page.getByRole('button', { name: 'Confirmar alteração', exact: true }).click();
  await page.locator('.status-badge').filter({ hasText: status }).waitFor();
  await page.getByRole('dialog').waitFor({ state: 'hidden' });
}
async function comment(text) {
  await page.getByLabel('Novo comentário', { exact: true }).fill(text);
  await page.getByRole('button', { name: 'Publicar comentário', exact: true }).click();
  await page.getByTestId('comment').filter({ hasText: text }).waitFor();
}
try {
  await login('SOLICITANTE');
  await page.goto(`${browserUrl}/chamados/novo`);
  await page.getByLabel('Título', { exact: true }).fill('AC2 navegador: ciclo completo');
  await page.getByLabel('Descrição', { exact: true }).fill('Registro fictício reproduzível para validar o ciclo completo da AC2.');
  await page.locator('#chamado-categoria option').nth(1).waitFor({ state: 'attached' });
  // Angular ngValue encodes the numeric ID in option.value (e.g. "1: 2").
  const selected = await page.locator('#chamado-categoria option').evaluateAll((options, id) => options.find(option => Number(option.value.split(':').at(-1).trim()) === Number(id))?.value, fixtures.categoryId);
  assert(selected, 'Fixture category is available in the real opening form');
  await page.locator('#chamado-categoria').selectOption(selected);
  await page.getByLabel('Média', { exact: true }).check();
  const createdResponse = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/chamados');
  await page.getByRole('button', { name: 'Abrir chamado', exact: true }).click();
  const created = await (await createdResponse).json();
  assert(Number.isInteger(created.id));
  await page.getByTestId('chamado-success').waitFor();
  await checkpoint('Solicitante abre chamado pelo formulário integrado');
  await detail(created.id);
  await page.getByText('Nenhum comentário registrado.', { exact: true }).waitFor();
  assert.equal(await page.locator('[data-action="RESOLVER"]').count(), 0);
  assert.equal(await page.locator('[data-action="ALTERAR_PRIORIDADE"]').count(), 0);
  await checkpoint('Comentários apresentam estado vazio');
  await login('ATENDENTE');
  await page.goto(`${browserUrl}/atendimento`);
  await page.getByLabel('Prioridade', { exact: true }).selectOption('MEDIA');
  await page.getByLabel('Categoria', { exact: true }).selectOption(String(fixtures.categoryId));
  await page.getByRole('button', { name: 'Filtrar', exact: true }).click();
  await page.waitForURL(url => url.searchParams.get('prioridade') === 'MEDIA');
  const ticket = page.locator('article.ticket').filter({ hasText: created.protocolo });
  await ticket.getByRole('button').click();
  await page.getByRole('dialog').waitFor();
  await page.getByRole('button', { name: 'Confirmar atribuição', exact: true }).click();
  await page.getByText('Ver meus atendimentos', { exact: true }).click();
  await page.locator('article.ticket').filter({ hasText: created.protocolo }).getByRole('link').click();
  await page.locator('.status-badge').filter({ hasText: 'Em atendimento' }).waitFor();
  await checkpoint('Fila filtrada, confirmação de atribuição e carteira integradas');
  await comment('Comentário fictício do atendente para acompanhamento.');
  await page.locator('[data-action="ALTERAR_PRIORIDADE"]').click();
  await page.getByLabel('Nova prioridade').selectOption('ALTA');
  await page.getByRole('button', { name: 'Confirmar alteração', exact: true }).click();
  await page.getByRole('dialog').waitFor({ state: 'hidden' });
  assert.equal(await page.locator('.detail-meta dd').nth(1).textContent(), 'Alta');
  await checkpoint('Atendente publica comentário e altera prioridade');
  await transition('RESOLVER', 'Solução fictícia: configuração corrigida e validada.', 'Resolvido');
  await login('SOLICITANTE');
  await detail(created.id);
  await transition('REABRIR', 'A falha ainda ocorre durante a validação fictícia.', 'Em atendimento');
  await checkpoint('Resolução e reabertura pelo solicitante integradas');
  const safeText = '<img src=x onerror="window.__ac2Injected=true"> comentário literal';
  fault = { matches: (request, suffix) => request.method() === 'POST' && suffix.endsWith('/comentarios'), status: 500, body: { message: 'Falha temporária de demonstração.' } };
  await page.getByLabel('Novo comentário', { exact: true }).fill(safeText);
  await page.getByRole('button', { name: 'Publicar comentário', exact: true }).click();
  await page.getByRole('alert').filter({ hasText: 'Falha temporária' }).waitFor();
  assert.equal(await page.getByLabel('Novo comentário', { exact: true }).inputValue(), safeText);
  await checkpoint('Falha controlada 500 preserva o rascunho');
  const previousPosts = postCount;
  postDelay = true;
  await page.getByRole('button', { name: 'Publicar comentário', exact: true }).click();
  assert(await page.locator('#publish-comment').isDisabled());
  await page.locator('app-comentarios-chamado form').evaluate(form => { form.requestSubmit(); form.requestSubmit(); });
  await page.getByTestId('comment').filter({ hasText: safeText }).waitFor();
  postDelay = false;
  assert.equal(postCount - previousPosts, 1, 'Only one comment request while posting');
  await page.reload();
  await page.getByTestId('comment').filter({ hasText: safeText }).waitFor();
  assert.equal(await page.locator('.timeline img').count(), 0);
  assert.equal(await page.evaluate(() => window.__ac2Injected), undefined);
  await checkpoint('Envio duplicado bloqueado; comentário persiste e HTML é texto');
  fault = { matches: (request, suffix) => request.method() === 'GET' && suffix === `/chamados/${created.id}/comentarios`, status: 500, body: { message: 'Falha controlada dos comentários.' } };
  await page.reload();
  await page.getByRole('button', { name: 'Recarregar comentários', exact: true }).click();
  await page.getByTestId('comment').filter({ hasText: safeText }).waitFor();
  await checkpoint('Comentários recuperam após falha controlada de leitura');
  await login('ADMIN');
  await detail(created.id, true);
  await page.locator('[data-action="RESOLVER"]').waitFor();
  await page.locator('[data-action="ALTERAR_PRIORIDADE"]').waitFor();
  await comment('Administrador acompanha o chamado fictício sem alterar o responsável.');
  await checkpoint('Administrador consulta chamado alheio, ações autorizadas e comentários');
  await login('ATENDENTE');
  await detail(created.id, true);
  await transition('RESOLVER', 'Nova solução fictícia validada após a reabertura.', 'Resolvido');
  await login('SOLICITANTE');
  await detail(created.id);
  await transition('FECHAR', null, 'Fechado');
  await page.getByText('Chamado fechado: os comentários estão disponíveis apenas para leitura.', { exact: true }).waitFor();
  assert.equal(await page.locator('[data-action]').count(), 0);
  assert.equal(await page.locator('#comment-content').count(), 0);
  await checkpoint('Nova resolução e fechamento terminal sem ações ou editor');
  await page.setViewportSize({ width: 360, height: 800 });
  assert(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), 'Detail does not overflow at 360 px');
  await checkpoint('Detalhe fechado utilizável em largura de 360 px');
  await page.setViewportSize({ width: 1280, height: 720 });
  await login('ADMIN');
  await detail(created.id, true);
  assert.equal(await page.locator('[data-action]').count(), 0);
  await checkpoint('Administrador consulta detalhe fechado respeitando estado terminal');
  fault = { matches: (request, suffix) => request.method() === 'GET' && suffix === '/atendimento/fila', status: 500, body: { message: 'Falha controlada da fila.' } };
  await page.goto(`${browserUrl}/atendimento`);
  await page.getByRole('alert').filter({ hasText: 'Falha controlada' }).waitFor();
  await page.getByRole('button', { name: 'Tentar novamente', exact: true }).click();
  await page.locator('.pagination').waitFor();
  await checkpoint('Fila recupera após falha controlada de carregamento');
  fault = { matches: (request, suffix) => request.method() === 'GET' && suffix === '/atendimento/fila', delay: 700, body: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } };
  await page.reload();
  await page.getByText('Carregando chamados…', { exact: true }).waitFor();
  await page.getByText('Nenhum chamado disponível para estes filtros.', { exact: true }).waitFor();
  await checkpoint('Fila apresenta carregamento e vazio com resposta controlada');
  report.result = 'PASS';
} catch (error) {
  report.result = 'FAIL';
  // Playwright errors can contain form values. Keep output free of fixture credentials.
  report.failure = 'Browser assertion or operation failed; inspect the local failure screenshot.';
  console.error(report.failure);
  await page.screenshot({ path: resolve(output, 'failure.png'), fullPage: true }).catch(() => {});
  process.exitCode = 1;
} finally {
  await context.close();
  await browser.close();
  await writeFile(resolve(output, 'report.json'), `${JSON.stringify(report, null, 2)}\n`);
}
