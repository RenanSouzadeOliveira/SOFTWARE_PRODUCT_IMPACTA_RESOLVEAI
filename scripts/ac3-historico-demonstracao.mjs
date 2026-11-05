import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';

// Somente infraestrutura acadêmica isolada. Nenhum token é escrito em disco ou no console.
const base = process.env.HISTORY_API_URL ?? 'http://127.0.0.1:8089/api';
const database = process.env.HISTORY_DEMO_DATABASE;
assert.ok(database, 'Informe HISTORY_DEMO_DATABASE: banco PostgreSQL acadêmico isolado.');
assert.ok(['127.0.0.1', 'localhost'].includes(new URL(base).hostname), 'Use uma API local de demonstração.');
const senha = process.env.HISTORY_DEMO_PASSWORD;
assert.ok(senha?.length >= 8, 'Informe HISTORY_DEMO_PASSWORD (senha fictícia, mínimo de 8 caracteres).');
const nonce = Date.now();
const verify = process.argv.includes('--verify');
async function api(path, method, body, token, expected = 200) {
  const response = await fetch(`${base}/${path}`, {
    method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  assert.equal(response.status, expected, `${method} ${path}`);
  return response.json();
}
function sql(command) {
  return execFileSync('psql', ['-h', '127.0.0.1', '-p', process.env.PGPORT ?? '54129',
    '-U', process.env.PGUSER ?? 'history_test', '-d', database, '-v', 'ON_ERROR_STOP=1', '-c', command], { stdio: 'pipe' });
}
async function register(role) {
  const credentials = { nome: `Pessoa acadêmica ${role}`, email: `hist-${nonce}-${role}@example.invalid`, senha };
  const user = await api('auth/register', 'POST', credentials, undefined, 201);
  assert.ok(Number.isSafeInteger(user.id));
  return { ...credentials, id: user.id };
}
const owner = await register('solicitante');
const agent = await register('atendente');
const other = await register('outro');
// Apenas a conta fictícia recém-criada recebe o papel necessário à demonstração.
sql(`UPDATE usuarios SET perfil='ATENDENTE' WHERE id=${agent.id}`);
const login = user => api('auth/login', 'POST', { email: user.email, senha });
const ownerSession = await login(owner), agentSession = await login(agent), otherSession = await login(other);
const categories = await api('categorias', 'GET', undefined, ownerSession.accessToken);
assert.ok(categories.length, 'Inicie a API com o perfil dev e categorias acadêmicas.');
const ticket = await api('chamados', 'POST', {
  titulo: `Auditoria acadêmica AC3 ${nonce}`, descricao: 'Solicitação fictícia para demonstrar o histórico completo de alterações.',
  prioridade: 'MEDIA', categoriaId: categories[0].id,
}, ownerSession.accessToken, 201);
const history = () => api(`chamados/${ticket.id}/historico`, 'GET', undefined, ownerSession.accessToken);
assert.equal((await history()).length, 1);
if (verify) {
  await api(`chamados/${ticket.id}/assumir`, 'PATCH', {}, agentSession.accessToken);
  assert.equal((await history()).length, 3);
  await api(`chamados/${ticket.id}/prioridade`, 'PATCH', { prioridade: 'ALTA' }, agentSession.accessToken);
  await api(`chamados/${ticket.id}/prioridade`, 'PATCH', { prioridade: 'ALTA' }, agentSession.accessToken, 409);
  assert.equal((await history()).length, 4);
  await api(`chamados/${ticket.id}/status`, 'PATCH', { status: 'RESOLVIDO', justificativa: 'Solução acadêmica verificada na demonstração.' }, agentSession.accessToken);
  assert.equal((await history()).length, 5);
  await api(`chamados/${ticket.id}/historico`, 'GET', undefined, otherSession.accessToken, 404);
  sql(`UPDATE usuarios SET ativo=false WHERE id=${agent.id}`);
  await api(`chamados/${ticket.id}/historico`, 'GET', undefined, agentSession.accessToken, 401);
  assert.ok((await history()).some(event => event.autor.id === agent.id && event.autor.nome === agent.nome));
}
console.log(JSON.stringify({ resultado: 'PASS', modo: verify ? 'verificação' : 'preparação para vídeo',
  chamadoId: ticket.id, protocolo: ticket.protocolo, eventos: (await history()).length,
  solicitante: owner.email, atendente: agent.email, outroSolicitante: other.email,
  senha: 'Use o valor fictício de HISTORY_DEMO_PASSWORD; tokens não são exibidos.',
  detalheSolicitante: `/chamados/${ticket.id}`, detalheAtendente: `/atendimento/chamados/${ticket.id}`,
}, null, 2));
