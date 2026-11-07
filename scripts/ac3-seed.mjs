#!/usr/bin/env node
// Carga acadêmica local. Tickets e eventos são escritos exclusivamente pelos serviços Java existentes.
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync, writeFileSync, existsSync, statSync, openSync, closeSync, mkdtempSync, rmSync, realpathSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseEnv } from 'node:util';

const root = fileURLToPath(new URL('..', import.meta.url));
process.chdir(root);
const marker = '[AC3-ACADEMICO-V1]';
const keys = ['solicitante1', 'solicitante2', 'solicitante3', 'atendente1', 'atendente2', 'admin'];
const modes = process.argv.slice(2);
if (modes.includes('--help')) {
  console.log('Uso: node scripts/ac3-seed.mjs [--dry-run | --verify]\nPadrão: .env.ac3-gravacao e container resolveai-ac3-gravacao-postgres-1. Não apaga dados nem altera senhas existentes.');
  process.exit(0);
}
assert(modes.length <= 1 && modes.every(v => ['--dry-run', '--verify'].includes(v)), 'Argumento inválido.');
const mode = modes[0] ?? '--apply';
const settings = parseEnv(readFileSync(process.env.AC3_SEED_ENV_FILE ?? '.env.ac3-gravacao', 'utf8'));
const env = { ...process.env, ...settings };
assert.equal(settings.SPRING_PROFILES_ACTIVE, 'dev', 'Exige configuração explicitamente dev.');
assert(!Object.keys(env).some(k => /^SPRING_(DATASOURCE_|FLYWAY_|APPLICATION_JSON)/.test(k)), 'Remova overrides Spring conflitantes da sessão.');
const target = new URL(settings.DB_URL.replace(/^jdbc:/, ''));
assert.equal(target.protocol, 'postgresql:');
assert(['localhost', '127.0.0.1'].includes(target.hostname) && !target.username && !target.password && !target.search, 'Exige JDBC local sem credenciais na URL.');
assert.equal(target.pathname, '/resolveai');
assert.equal(settings.DB_USERNAME, 'resolveai');
assert.equal(target.port, settings.POSTGRES_PORT);
assert(settings.DB_PASSWORD && settings.DB_PASSWORD === settings.POSTGRES_PASSWORD, 'Senhas do mesmo ambiente divergem.');
const apiBase = new URL(process.env.AC3_API_URL ?? 'http://localhost:8080/api');
assert(apiBase.protocol === 'http:' && ['localhost', '127.0.0.1'].includes(apiBase.hostname) && apiBase.pathname === '/api'
  && !apiBase.username && !apiBase.password && !apiBase.search, 'Exige API local /api.');
function command(program, args, options = {}) {
  const result = spawnSync(program, args, { cwd: root, env, encoding: 'utf8', maxBuffer: 8 * 1024 * 1024, ...options });
  if (result.status !== 0) throw new Error(`Falha em ${path.basename(program)}; detalhes mantidos fora da saída pública.`);
  return result.stdout;
}
const container = JSON.parse(command('docker', ['inspect', 'resolveai-ac3-gravacao-postgres-1']))[0];
assert.equal(container.Config.Labels['com.docker.compose.project'], 'resolveai-ac3-gravacao');
assert.equal(container.Config.Labels['com.docker.compose.service'], 'postgres');
assert.equal(realpathSync(container.Config.Labels['com.docker.compose.project.working_dir']), realpathSync(root));
assert.equal(container.State.Status, 'running');
assert(container.NetworkSettings.Ports['5432/tcp'].some(p => p.HostPort === target.port), 'JDBC não aponta para o container acadêmico.');
const pgEnv = { ...env, PGHOST: target.hostname, PGPORT: target.port, PGDATABASE: 'resolveai', PGUSER: settings.DB_USERNAME,
  PGPASSWORD: settings.DB_PASSWORD, PGCONNECT_TIMEOUT: '5' };
function sql(query) { return command('psql', ['-X', '-w', '-qAt', '-v', 'ON_ERROR_STOP=1', '-c', query], { env: pgEnv }).trim(); }
const literal = value => "'" + value.replaceAll("'", "''") + "'";
const scope = `titulo LIKE ${literal(marker + ' %')}`;
const emails = keys.map(k => literal(`ac3-v1-${k}@example.invalid`)).join(',');
assert.equal(sql('SELECT current_database()'), 'resolveai');
assert.equal(sql("SELECT max(version::int) FROM flyway_schema_history WHERE success AND version IS NOT NULL"), '9');
const categories = JSON.parse(sql("SELECT json_agg(t) FROM (SELECT id,nome FROM categorias WHERE ativa ORDER BY nome,id) t"));
assert(categories?.length >= 3, 'Exige três categorias ativas existentes.');
const existing = Number(sql(`SELECT count(*) FROM chamados WHERE ${scope}`));
const targetInfo = { host: target.hostname, porta: target.port, banco: 'resolveai', perfil: 'dev', container: container.Name.slice(1) };
const preHealth = await api('/health'); assert.equal(preHealth.status, 'UP'); assert.equal(preHealth.service, 'resolveai-api');
console.log(JSON.stringify({ ambiente: targetInfo, existentesDoMarcador: existing, planejados: 75, modo: mode }));
if (mode === '--dry-run') process.exit(0);

const credentialsFile = path.resolve(process.env.AC3_SEED_CREDENTIALS_FILE ?? '.env.ac3-seed-v1.json');
assert(credentialsFile.startsWith(root) || process.env.AC3_SEED_CREDENTIALS_FILE, 'Caminho de credenciais inválido.');
if (credentialsFile.startsWith(root)) command('git', ['check-ignore', '-q', credentialsFile]);
env.AC3_SEED_CREDENTIALS_FILE = credentialsFile;
function preservedSnapshot() {
  const digest = (table, where) => `(SELECT md5(coalesce(string_agg(row_to_json(t)::text,'|' ORDER BY t.id),'')) FROM ${table} t WHERE ${where})`;
  return sql(`SELECT json_build_object(
    'chamados', ${digest('chamados', `NOT (${scope})`)},
    'usuarios', ${digest('usuarios', `email NOT IN (${emails})`)},
    'categorias', ${digest('categorias', 'true')},
    'historico', ${digest('historico_chamados', `chamado_id NOT IN (SELECT id FROM chamados WHERE ${scope})`)},
    'status', ${digest('historico_status', `chamado_id NOT IN (SELECT id FROM chamados WHERE ${scope})`)})`);
}
let insertion = { inseridos: 0, existentes: existing };
if (mode === '--apply') {
  const before = preservedSnapshot();
  const work = mkdtempSync(path.join(tmpdir(), 'resolveai-ac3-seed-'));
  const log = path.join(work, 'execucao.log');
  const descriptor = openSync(log, 'wx', 0o600);
  try {
    command(process.env.AC3_MAVEN ?? 'mvn', ['-B', '-ntp', '-f', 'backend/pom.xml', '-DskipTests', 'package'], { stdio: ['ignore', descriptor, descriptor] });
    command('jar', ['xf', path.join(root, 'backend/target/resolveai-api-0.0.1-SNAPSHOT.jar'), 'BOOT-INF/lib', 'BOOT-INF/classes'], { cwd: work });
    command('java', ['--class-path', path.join(work, 'BOOT-INF/classes') + path.delimiter + path.join(work, 'BOOT-INF/lib/*'),
      path.join(root, 'scripts/Ac3DemoSeed.java')], { stdio: ['ignore', descriptor, descriptor] });
    const match = readFileSync(log, 'utf8').match(/AC3_SEED_RESULT=(.+)/);
    assert(match, 'Resumo Java ausente.'); insertion = JSON.parse(match[1]);
    assert.equal(preservedSnapshot(), before, 'Houve alteração concorrente ou inesperada fora do conjunto; verifique o banco.');
  } catch (error) {
    throw new Error(`${error.message} Log privado: ${log}`);
  } finally {
    closeSync(descriptor);
    // Preserva o log pequeno; remove somente bibliotecas temporárias extraídas desta execução.
    rmSync(path.join(work, 'BOOT-INF'), { recursive: true, force: true });
  }
}
assert(existsSync(credentialsFile), 'Não há arquivo privado de credenciais para validar o conjunto.');
assert.equal(statSync(credentialsFile).mode & 0o777, 0o600, 'Credenciais devem ter permissão 600.');
const state = JSON.parse(readFileSync(credentialsFile, 'utf8'));
assert.equal(state.marcador, marker);
async function api(route, session, expected = 200, method = 'GET', body) {
  const response = await fetch(`${apiBase.href}${route}`, { method, redirect: 'error', signal: AbortSignal.timeout(20000),
    headers: { ...(session ? { Authorization: `Bearer ${session.accessToken}` } : {}), ...(body ? { 'Content-Type': 'application/json' } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}) });
  assert.equal(response.status, expected, `${method} ${route}: HTTP inesperado`);
  return response.json();
}
const sessions = {};
for (const key of keys) {
  const account = state.contas[key];
  sessions[key] = await api('/auth/login', null, 200, 'POST', { email: account.email, senha: account.senha });
}
const rows = JSON.parse(sql(`SELECT json_agg(t ORDER BY t.titulo) FROM
  (SELECT id,titulo,protocolo,status,prioridade,categoria_id,solicitante_id,atendente_id,criado_em FROM chamados WHERE ${scope}) t`));
assert.equal(rows?.length, 75, 'Conjunto incompleto; não duplique ou reescreva registros existentes.');
const query = filters => '?' + new URLSearchParams(filters);
async function count(session, filters, expected) {
  const result = await api('/chamados' + query(filters), session);
  assert.equal(result.totalElements, expected, 'Total filtrado divergente'); return result;
}
const admin = sessions.admin, owner = sessions.solicitante1;
await count(admin, { search: marker }, 75);
const sizes = [], ids = [];
for (let page = 0; page < 3; page++) {
  const result = await api(`/chamados/me?page=${page}`, owner);
  assert.equal(result.totalElements, 45); assert.equal(result.totalPages, 3); assert.equal(result.size, 20);
  assert.equal(result.content.length, page === 2 ? 5 : 20);
  sizes.push(result.content.length); ids.push(...result.content.map(t => t.id));
}
assert.equal(new Set(ids).size, 45);
for (const [index, total] of [[2, 15], [3, 15]]) await count(sessions[`solicitante${index}`], {}, total);
const expectedStates = { ABERTO: 15, EM_ATENDIMENTO: 30, RESOLVIDO: 15, FECHADO: 15 };
for (const [status, total] of Object.entries(expectedStates)) await count(admin, { search: marker, status }, total);
for (const [prioridade, total] of [['ALTA', 30], ['MEDIA', 30], ['BAIXA', 15]]) await count(admin, { search: marker, prioridade }, total);
for (const categoriaId of state.categorias) await count(admin, { search: marker, categoriaId }, 25);
for (let i = 1; i <= 2; i++) {
  const portfolio = await api('/atendimento/me', sessions[`atendente${i}`]); assert.equal(portfolio.totalElements, 30);
  const agentId = rows[i === 1 ? 2 : 1].atendente_id;
  await count(admin, { search: marker, responsavel: agentId }, 30);
}
await count(admin, { search: marker, dataInicial: '2026-09-01', dataFinal: '2026-09-30' }, 25);
await count(admin, { search: marker, status: 'EM_ATENDIMENTO', prioridade: 'ALTA', dataInicial: '2026-09-01', dataFinal: '2026-09-30' }, 10);
await count(owner, { search: 'iMpReSsOrA' }, 6);
await count(owner, { search: 'impressora', status: 'EM_ATENDIMENTO', prioridade: 'ALTA', categoriaId: state.categorias[1] }, 1);
await count(owner, { search: 'AC3-V1-TERMO-SEM-RESULTADO-000' }, 0);
await count(owner, { search: rows[0].protocolo }, 1);
for (const order of ['asc', 'desc']) {
  const result = await api('/chamados/me' + query({ size: '100', sort: `criadoEm,${order}` }), owner);
  const expected = rows.slice(0, 45).toSorted((a, b) => {
    const byDate = Date.parse(a.criado_em) - Date.parse(b.criado_em);
    return (order === 'asc' ? byDate : -byDate) || b.id - a.id;
  });
  assert.deepEqual(result.content.map(t => t.id), expected.map(t => t.id));
}
assert.equal(new Set(rows.slice(0, 5).map(t => t.criado_em)).size, 1, 'Empates de timestamp não preparados.');
for (const [i, total] of [[0, 1], [1, 4], [2, 6], [3, 5], [4, 6]]) {
  const events = await api(`/chamados/${rows[i].id}/historico`, owner);
  assert.equal(events.length, total); assert.equal(events.filter(e => e.tipoEvento === 'CRIACAO').length, 1);
  assert(events.every(e => Number.isFinite(Date.parse(e.criadoEm)) && e.autor?.id));
}
await api(`/chamados/${rows[0].id}`, sessions.solicitante2, 404);
await api(`/chamados/${rows[0].id}/historico`, sessions.solicitante2, 404);
await api(`/chamados/${rows[1].id}/historico`, sessions.atendente1, 404);
await api('/atendimento/todos', owner, 403);
await api(`/chamados/${rows[0].id}/historico`, null, 401);
await api(`/chamados/${rows[1].id}/prioridade`, sessions.atendente2, 409, 'PATCH', { prioridade: 'ALTA' });
assert.equal((await api(`/chamados/${rows[1].id}/historico`, owner)).length, 4);
const eventsCount = Number(sql(`SELECT (SELECT count(*) FROM historico_chamados WHERE chamado_id IN (SELECT id FROM chamados WHERE ${scope}))
  + (SELECT count(*) FROM historico_status WHERE chamado_id IN (SELECT id FROM chamados WHERE ${scope}))`));
assert.equal(eventsCount, 330);
const summary = { resultado: 'PASS', ...insertion, totalDoConjunto: rows.length, eventos: eventsCount, paginasSolicitante1: sizes,
  status: expectedStates, prioridades: { ALTA: 30, MEDIA: 30, BAIXA: 15 },
  categorias: state.categorias.map(id => ({ id, nome: categories.find(c => c.id === id)?.nome, total: 25 })),
  solicitantes: [45, 15, 15], atendentes: [30, 30], setembro: 25, emAtendimentoAltaSetembro: 10,
  impressoraSemDistinguirCaixa: 6, impressoraEmAtendimentoAltaCategoriaEquipamentos: 1, vazio: 0,
  credenciaisPrivadas: path.relative(root, credentialsFile), contas: Object.values(state.contas).map(a => ({ email: a.email, perfil: a.perfil })),
  exemplos: rows.slice(0, 5).map(t => ({ id: t.id, protocolo: t.protocolo, titulo: t.titulo })) };
console.log(JSON.stringify(summary, null, 2));
