#!/usr/bin/env node
// Execute somente em ambiente local descartável: cria 45 chamados fictícios.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
const api = process.env.AC3_API_URL ?? 'http://localhost:8080/api';
const senha = `AC3-${randomUUID()}-aA1!`;
const email = `ac3-${randomUUID()}@example.invalid`;
let token;
async function request(path, method = 'GET', body, expected = 200) {
  const response = await fetch(`${api}${path}`, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  assert.equal(response.status, expected, `${method} ${path}: HTTP ${response.status}`);
  return response.json();
}
await request('/auth/register', 'POST', { nome: 'Pessoa fictícia AC3', email, senha }, 201);
token = (await request('/auth/login', 'POST', { email, senha })).accessToken;
const categorias = await request('/categorias');
assert(categorias.length, 'Cadastre uma categoria ativa no banco descartável antes da demonstração.');
const created = [];
for (let n = 1; n <= 45; n++) {
  const ticket = await request('/chamados', 'POST', { titulo: `Demonstração AC3 ${String(n).padStart(2, '0')}`, descricao: 'Solicitação fictícia para demonstrar a paginação acadêmica.', categoriaId: categorias[0].id, prioridade: 'MEDIA' }, 201);
  created.push(ticket.id);
}
const pages = [];
for (let page = 0; page < 3; page++) {
  const result = await request(`/chamados/me?page=${page}`);
  assert.equal(result.page, page); assert.equal(result.size, 20);
  assert.equal(result.totalElements, 45); assert.equal(result.totalPages, 3);
  assert.equal(result.content.length, page === 2 ? 5 : 20);
  pages.push(result.content.map(t => t.id));
}
assert.deepEqual(pages.flat(), created.toReversed());
assert.equal(new Set(pages.flat()).size, 45);
assert.deepEqual((await request('/chamados/me?page=1')).content.map(t => t.id), pages[1]);
assert.equal((await request('/chamados/me?page=9')).content.length, 0);
assert.equal((await request('/chamados/me?size=100')).content.length, 45);
assert.equal((await request('/chamados/me?size=20&sort=titulo,asc')).content[0].titulo, 'Demonstração AC3 01');
await request('/chamados/me?sort=senhaHash,asc', 'GET', undefined, 400);
console.log(JSON.stringify({ resultado: 'APROVADO', chamados: 45, paginas: pages.map(p => p.length), semDuplicatas: true, retornoPaginaAnterior: true, ordenacao: true, sortInvalido: 400 }, null, 2));
