#!/usr/bin/env node
// Runs only against the empty, labelled PostgreSQL container created by validar-ac2.sh.
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { writeFileSync } from 'node:fs';
import { isAbsolute, resolve } from 'node:path';

const args = process.argv.slice(2);
if (args.includes('--help')) {
  console.log('AC2_CONTAINER=<disposable labelled container> node scripts/ac2-api.mjs [--fixtures /tmp/accounts.json]\nAC2_API_URL defaults to http://127.0.0.1:18082/api. Requires Node 22, Docker, empty migrated database resolveai_ac2 and user resolveai_test. JSON report contains no credentials. Optional private fixtures contain generated credentials and must stay outside the repository.');
  process.exit(0);
}
assert(args.length === 0 || (args.length === 2 && args[0] === '--fixtures'), 'Unsupported arguments');
const fixturePath = args[1];
if (fixturePath) {
  assert(isAbsolute(fixturePath) && resolve(fixturePath).startsWith('/tmp/'), 'Private fixtures must be an absolute path under /tmp');
}
const container = process.env.AC2_CONTAINER;
assert(container && /^[a-zA-Z0-9][a-zA-Z0-9_.-]+$/.test(container), 'AC2_CONTAINER is required');
const apiUrl = (process.env.AC2_API_URL ?? 'http://127.0.0.1:18082/api').replace(/\/$/, '');
const url = new URL(apiUrl);
assert(['127.0.0.1', 'localhost'].includes(url.hostname) && url.protocol === 'http:' && url.pathname === '/api', 'Only a local disposable API is supported');
const checks = [];
let requests = 0;
function check(name, actual, expected) { assert.deepEqual(actual, expected, name); checks.push(name); }
function docker(arguments_, input) {
  const result = spawnSync('docker', arguments_, { input, encoding: 'utf8', timeout: 20000 });
  assert.equal(result.status, 0, 'Disposable PostgreSQL operation failed');
  return result.stdout.trim();
}
function sql(statement) {
  assert.equal(docker(['inspect', '--format', '{{index .Config.Labels "resolveai.validation"}}', container]), 'ac2', 'Container must have validation label');
  return docker(['exec', '-i', container, 'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-U', 'resolveai_test', '-d', 'resolveai_ac2'], statement);
}
async function request(method, path, account, body, expected = 200) {
  const response = await fetch(`${apiUrl}${path}`, {
    method, headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...(account ? { Authorization: `Bearer ${account.token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(20000),
  });
  requests++;
  assert.equal(response.status, expected, `${method} ${path} expected ${expected}; received ${response.status}`);
  return response.json();
}
const detail = (id, account) => request('GET', `/chamados/${id}`, account);
const assume = (id, account, expected = 200) => request('PATCH', `/chamados/${id}/assumir`, account, undefined, expected);
const status = (id, account, value, expected = 200) => request('PATCH', `/chamados/${id}/status`, account,
  { status: value, ...(value === 'FECHADO' ? {} : { justificativa: 'Validacao ficticia do ciclo AC2 completo' }) }, expected);
const comment = (id, account, content, expected = 201) => request('POST', `/chamados/${id}/comentarios`, account, { conteudo: content }, expected);
function timestamp(value) {
  const match = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?Z$/.exec(value);
  assert(match && Number.isFinite(Date.parse(value)), 'Valid UTC event timestamp');
  return BigInt(Date.parse(`${match[1]}Z`)) * 1000000n + BigInt((match[2] ?? '').padEnd(9, '0'));
}

async function run() {
  check('database initially empty', sql('SELECT (SELECT COUNT(*) FROM usuarios) + (SELECT COUNT(*) FROM chamados);'), '0');
  const password = `Ac2!${randomBytes(18).toString('hex')}`;
  const roles = ['SOLICITANTE', 'SOLICITANTE', 'ATENDENTE', 'ATENDENTE', 'ADMIN'];
  const accounts = [];
  for (const [index, role] of roles.entries()) {
    const email = `ac2-${index}@example.invalid`;
    const name = ['Solicitante AC2', 'Outro solicitante AC2', 'Atendente AC2', 'Outro atendente AC2', 'Administrador AC2'][index];
    const user = await request('POST', '/auth/register', null, { nome: name, email, senha: password }, 201);
    assert(Number.isSafeInteger(user.id) && user.id > 0, 'Valid account identifier');
    check(`registration role ${index}`, user.perfil, 'SOLICITANTE');
    // Bind the HTTP registration to this disposable database before promoting any account.
    check(`database API identity ${index}`, sql(`SELECT email FROM usuarios WHERE id=${user.id};`), email);
    accounts.push({ id: user.id, email, role, name });
  }
  for (const account of accounts.filter(a => a.role !== 'SOLICITANTE')) {
    sql(`UPDATE usuarios SET perfil='${account.role}', atualizado_em=CURRENT_TIMESTAMP WHERE id=${account.id};`);
  }
  for (const account of accounts) {
    const login = await request('POST', '/auth/login', null, { email: account.email, senha: password });
    check(`login role ${account.id}`, login.usuario.perfil, account.role);
    account.token = login.accessToken;
    assert(typeof account.token === 'string' && account.token.length > 20, 'Login token missing');
  }
  const [owner, outsider, attendant, competitor, admin] = accounts;
  const categories = await request('GET', '/categorias', owner);
  assert(categories.length > 0, 'Run API with dev demo categories');
  const categoryId = categories[0].id;
  async function open(title) {
    const result = await request('POST', '/chamados', owner, { titulo: title, descricao: 'Solicitacao ficticia reproduzivel para consolidacao da AC2.', categoriaId: categoryId, prioridade: 'MEDIA' }, 201);
    assert(Number.isSafeInteger(result.id) && result.id > 0);
    check(`opened ${result.id}`, [result.status, result.atendente], ['ABERTO', null]);
    return result.id;
  }
  const tickets = { aberto: await open('AC2 aberto para demonstracao'), emAtendimento: await open('AC2 concorrencia de atribuicao'), resolvido: await open('AC2 resolvido aguardando confirmacao'), fechado: await open('AC2 ciclo completo com conversa') };
  const id = tickets.fechado;
  await request('GET', `/chamados/${id}`, null, undefined, 401);
  await request('GET', '/atendimento/fila', owner, undefined, 403);
  await request('GET', `/chamados/${id}`, outsider, undefined, 404);
  await request('GET', `/chamados/${id}/comentarios`, competitor, undefined, 404);
  await comment(id, outsider, 'Comentario proibido de terceiro', 403);
  await status(id, owner, 'FECHADO', 409);
  check('invalid transition leaves history empty', (await detail(id, owner)).historicoStatus, []);
  await comment(id, owner, 'Preciso de ajuda com o acesso do laboratorio.');
  await assume(id, attendant);
  await request('PATCH', `/chamados/${id}/prioridade`, competitor, { prioridade: 'ALTA' }, 403);
  await request('PATCH', `/chamados/${id}/prioridade`, attendant, { prioridade: 'ALTA' });
  check('priority audit persisted', sql(`SELECT prioridade_anterior || ':' || prioridade_nova || ':' || autor_id FROM historico_chamados WHERE chamado_id=${id} AND tipo_evento='PRIORIDADE';`), `MEDIA:ALTA:${attendant.id}`);
  const beforeComment = await detail(id, owner);
  await comment(id, attendant, 'Identifiquei a configuracao e estou corrigindo.');
  check('comment preserves ticket and history', await detail(id, owner), beforeComment);
  await request('POST', `/chamados/${id}/comentarios`, owner, { conteudo: '   ' }, 400);
  await status(id, competitor, 'RESOLVIDO', 403);
  const resolved = await status(id, attendant, 'RESOLVIDO');
  check('resolution fields', [resolved.status, typeof resolved.solucao, Boolean(resolved.resolvidoEm)], ['RESOLVIDO', 'string', true]);
  const reopened = await status(id, owner, 'EM_ATENDIMENTO');
  check('reopen clears resolution keeps assignment', [reopened.solucao, reopened.resolvidoEm, reopened.fechadoEm, reopened.atendente.id, reopened.atribuidoEm], [null, null, null, attendant.id, resolved.atribuidoEm]);
  await comment(id, owner, 'Reabri porque o acesso ainda precisa ser conferido.');
  await status(id, attendant, 'RESOLVIDO');
  await status(id, owner, 'FECHADO');
  // Compare persisted snapshots: PostgreSQL rounds Instant nanoseconds to microseconds.
  const closed = await detail(id, owner);
  check('full lifecycle history', closed.historicoStatus.map(event => event.statusNovo), ['EM_ATENDIMENTO', 'RESOLVIDO', 'EM_ATENDIMENTO', 'RESOLVIDO', 'FECHADO']);
  check('history actors', closed.historicoStatus.map(event => event.autor.id), [attendant.id, attendant.id, owner.id, attendant.id, owner.id]);
  check('closed terminal actions and timestamp', [closed.acoesPermitidas, Boolean(closed.fechadoEm)], [[], true]);
  const comments = await request('GET', `/chamados/${id}/comentarios`, owner);
  check('closed comments readable', [comments.content.length, comments.podeComentar], [3, false]);
  await comment(id, owner, 'Comentario apos fechamento', 409);
  await status(id, admin, 'EM_ATENDIMENTO', 409);
  check('terminal failures preserve history', await detail(id, owner), closed);
  for (const collection of [closed.historicoStatus, comments.content]) {
    for (let index = 0; index < collection.length; index++) {
      const entry = collection[index];
      const current = timestamp(entry.criadoEm);
      if (index) {
        const previous = timestamp(collection[index - 1].criadoEm);
        assert(current > previous || (current === previous && entry.id > collection[index - 1].id), 'Stable timestamp/id ordering');
      }
    }
  }
  checks.push('history and comments ordered by timestamp/id');
  // Two HTTP requests compete for the same initially open row.
  const race = await Promise.all([attendant, competitor].map(async account => {
    const response = await fetch(`${apiUrl}/chamados/${tickets.emAtendimento}/assumir`, { method: 'PATCH', headers: { Authorization: `Bearer ${account.token}` }, signal: AbortSignal.timeout(20000) });
    requests++;
    return { code: response.status, account };
  }));
  check('assignment concurrency 200/409', race.map(result => result.code).sort(), [200, 409]);
  const winner = race.find(result => result.code === 200).account;
  const raceDetail = await detail(tickets.emAtendimento, admin);
  check('assignment single winner and status history', [raceDetail.atendente.id, raceDetail.historicoStatus.length], [winner.id, 1]);
  check('assignment version and audit atomic', sql(`SELECT versao || ':' || (SELECT COUNT(*) FROM historico_chamados WHERE chamado_id=${tickets.emAtendimento} AND tipo_evento='ATRIBUICAO') FROM chamados WHERE id=${tickets.emAtendimento};`), '1:1');
  // Real PostgreSQL constraint failure proves transaction rollback; remove it even on failure.
  const rollbackId = tickets.resolvido;
  sql(`ALTER TABLE historico_status ADD CONSTRAINT ac2_validation_failure CHECK (chamado_id <> ${rollbackId});`);
  try {
    await assume(rollbackId, attendant, 500);
    check('failed assignment rolls back row', sql(`SELECT status || ':' || versao || ':' || COALESCE(atendente_id::text, 'null') FROM chamados WHERE id=${rollbackId};`), 'ABERTO:0:null');
    check('failed assignment rolls back both histories', sql(`SELECT (SELECT COUNT(*) FROM historico_status WHERE chamado_id=${rollbackId}) + (SELECT COUNT(*) FROM historico_chamados WHERE chamado_id=${rollbackId});`), '0');
  } finally {
    sql('ALTER TABLE historico_status DROP CONSTRAINT ac2_validation_failure;');
  }
  await assume(rollbackId, attendant);
  await comment(rollbackId, attendant, 'Configuracao corrigida e aguardando sua confirmacao.');
  await status(rollbackId, attendant, 'RESOLVIDO');
  const queue = await request('GET', `/atendimento/fila?page=0&size=1&prioridade=MEDIA&categoriaId=${categoryId}`, attendant);
  check('queue server filters and pagination', [queue.content.map(ticket => ticket.id), queue.page, queue.size, queue.totalElements], [[tickets.aberto], 0, 1, 1]);
  const portfolio = await request('GET', `/atendimento/me?page=0&size=1&status=RESOLVIDO&categoriaId=${categoryId}`, attendant);
  check('portfolio server filters and pagination', [portfolio.content.map(ticket => ticket.id), portfolio.size, portfolio.totalElements], [[tickets.resolvido], 1, 1]);
  await request('GET', '/atendimento/fila?size=51', attendant, undefined, 400);
  check('demo statuses', await Promise.all(Object.values(tickets).map(async ticketId => (await detail(ticketId, admin)).status)), ['ABERTO', 'EM_ATENDIMENTO', 'RESOLVIDO', 'FECHADO']);
  if (fixturePath) writeFileSync(fixturePath, JSON.stringify({ apiUrl, password, accounts, tickets, categoryId }, null, 2), { mode: 0o600, flag: 'wx' });
  return { status: 'passed', requests, checks, database: 'PostgreSQL disposable', fixtureStates: ['ABERTO', 'EM_ATENDIMENTO', 'RESOLVIDO', 'FECHADO'] };
}
try { console.log(JSON.stringify(await run(), null, 2)); }
catch (error) {
  // Never print request payloads, response bodies or authentication material.
  console.error(JSON.stringify({ status: 'failed', requests, message: error.message }));
  process.exitCode = 1;
}
