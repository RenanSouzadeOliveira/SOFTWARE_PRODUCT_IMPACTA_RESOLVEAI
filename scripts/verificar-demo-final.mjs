import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const [base, arquivo] = process.argv.slice(2);
assert.equal(new URL(base).hostname, '127.0.0.1');
const { contas } = JSON.parse(readFileSync(arquivo, 'utf8'));
async function consultar(rota, token, dados, metodo = dados ? 'POST' : 'GET', esperado = 200) {
  const resposta = await fetch(base + '/api' + rota, {
    method: metodo,
    redirect: 'error',
    signal: AbortSignal.timeout(10000),
    headers: { ...(dados && { 'Content-Type': 'application/json' }), ...(token && { Authorization: 'Bearer ' + token }) },
    body: dados && JSON.stringify(dados),
  });
  assert.equal(resposta.status, esperado, rota);
  return resposta.json();
}
const tokens = {};
for (const [nome, { email, senha, perfil }] of Object.entries(contas)) {
  const sessao = await consultar('/auth/login', null, { email, senha });
  assert.equal(sessao.usuario.perfil, perfil);
  tokens[nome] = sessao.accessToken;
}
const resumo = await consultar('/dashboard/resumo', tokens.admin);
assert.deepEqual([resumo.total, resumo.abertos, resumo.emAtendimento, resumo.concluidos], [75, 15, 30, 30]);
assert.deepEqual(Object.fromEntries(resumo.porStatus.map(g => [g.status, g.quantidade])), {
  ABERTO: 15, EM_ATENDIMENTO: 30, RESOLVIDO: 15, FECHADO: 15,
});
assert.deepEqual(Object.fromEntries(resumo.porPrioridade.map(g => [g.prioridade, g.quantidade])), {
  BAIXA: 15, MEDIA: 30, ALTA: 30,
});
assert.deepEqual(resumo.porCategoria.map(g => g.quantidade), [25, 25, 25]);
const pagina = await consultar('/chamados/me?search=001&size=20', tokens.solicitante1);
const chamado = pagina.content.find(c => c.titulo.startsWith('[AC3-ACADEMICO-V1] 001'));
assert.ok(chamado);
const historico = await consultar('/chamados/' + chamado.id + '/historico', tokens.solicitante1);
assert.ok(historico.some(e => e.tipoEvento === 'CRIACAO'));
const conversa = await consultar('/chamados/' + chamado.id + '/comentarios', tokens.solicitante1);
assert.ok(conversa.content.length);
for (const rota of ['/admin/usuarios', '/admin/categorias']) await consultar(rota, tokens.admin);
console.log('Carga conferida pela interface HTTP: 6 contas, 75 chamados, 3 categorias, 4 estados, comentários, histórico e administração.');

if (process.argv[4] === '--verificar') {
  const solicitante = tokens.solicitante1;
  const atendente = tokens.atendente1;
  const categoria = await consultar('/admin/categorias', tokens.admin, { nome: 'Projeção da demo' }, 'POST', 201);
  const novo = await consultar('/chamados', solicitante, {
    titulo: 'Projetor da sala de demonstração',
    descricao: 'O projetor do laboratório não apresenta imagem durante a demonstração.',
    categoriaId: categoria.id, prioridade: 'MEDIA',
  }, 'POST', 201);
  const rota = '/chamados/' + novo.id;
  const fila = await consultar('/atendimento/fila?search=' + encodeURIComponent(novo.protocolo), atendente);
  assert.equal(fila.totalElements, 1);
  assert.equal((await consultar(rota + '/assumir', atendente, null, 'PATCH')).status, 'EM_ATENDIMENTO');
  await consultar(rota + '/prioridade', atendente, { prioridade: 'ALTA' }, 'PATCH');
  await consultar(rota + '/comentarios', atendente, { conteudo: 'Equipamento em análise no laboratório.' }, 'POST', 201);
  await consultar(rota + '/status', atendente, { status: 'RESOLVIDO', justificativa: 'Cabo substituído e projeção testada.' }, 'PATCH');
  await consultar(rota + '/status', solicitante, { status: 'FECHADO' }, 'PATCH');
  const eventos = await consultar(rota + '/historico', solicitante);
  assert.ok(eventos.some(e => e.tipoEvento === 'STATUS' && e.valorNovo?.codigo === 'FECHADO'));
  const final = await consultar('/dashboard/resumo', tokens.admin);
  assert.deepEqual([final.total, final.concluidos], [76, 31]);
  await consultar('/admin/categorias/' + categoria.id + '/situacao', tokens.admin, { ativa: false }, 'PATCH');
  console.log('Fluxo conferido: criação, busca na fila, atribuição, prioridade, comentário, resolução, fechamento, histórico, dashboard e categoria.');
}
