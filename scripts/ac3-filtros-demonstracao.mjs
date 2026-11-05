// Apenas dados fictícios. Execute em ambiente acadêmico/de teste.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { pathToFileURL } from 'node:url';

export async function prepararDemonstracao(base = process.env.AC3_API_URL ?? 'http://localhost:8080/api') {
  const suffix = randomUUID();
  const email = process.env.AC3_DEMO_EMAIL ?? `ac3-filtros-${suffix}@example.invalid`;
  const senha = process.env.AC3_DEMO_PASSWORD ?? `Ficticia-${randomUUID()}`;
  let token;
  async function api(path, method = 'GET', body) {
    const response = await fetch(`${base}/${path}`, {method, headers: {
      ...(token ? {Authorization: `Bearer ${token}`} : {}),
      ...(body ? {'Content-Type':'application/json'} : {}),
    }, body: body ? JSON.stringify(body) : undefined});
    assert.equal(response.ok, true, `HTTP inesperado ${response.status} em ${method} ${path}`);
    return response.json();
  }
  await api('auth/register','POST',{nome:'Pessoa fictícia AC3 filtros',email,senha});
  const session = await api('auth/login','POST',{email,senha}); token = session.accessToken;
  const categories = await api('categorias');
  assert.ok(categories.length > 0, 'Disponibilize uma categoria ativa no banco de teste');
  const created=[];
  for(let i=0;i<45;i++) created.push(await api('chamados','POST',{
    titulo: i<25 ? `Portal pesquisa ${i}` : `Rede demonstracao ${i}`,
    descricao: `Descrição fictícia para demonstrar busca e filtros AC3. Registro ${i}.`,
    categoriaId: categories[i % Math.min(categories.length,2)].id,
    prioridade: i<25?'ALTA':'BAIXA',
  }));
  return { api, session, created };
}
export async function verificarDemonstracao(demo) {
  const {api,created}=demo;
  const inicio=created[0].criadoEm.slice(0,10), fim=created.at(-1).criadoEm.slice(0,10);
  const query=new URLSearchParams({status:'ABERTO',prioridade:'ALTA',dataInicial:inicio,dataFinal:fim,search:'  pOrTaL  ',size:'20',sort:'criadoEm,desc'});
  const first=await api(`chamados?${query}`); assert.equal(first.totalElements,25); assert.equal(first.totalPages,2); assert.equal(first.content.length,20);
  query.set('page','1'); const second=await api(`chamados?${query}`); assert.equal(second.content.length,5);
  assert.equal(new Set([...first.content,...second.content].map(c=>c.id)).size,25);
  const restored=await api(`chamados?${query}`); assert.deepEqual(restored,second);
  const empty=await api('chamados?search=termo-inexistente-ficticio'); assert.equal(empty.totalElements,0);
  const cleared=await api('chamados'); assert.equal(cleared.totalElements,45); assert.equal(cleared.page,0); assert.equal(cleared.size,20);
  return {resultado:'PASS',registros:45,filtrados:25,paginas:[20,5],vazio:true,restauracao:true,limpeza:true};
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const demo=await prepararDemonstracao();
  console.log(JSON.stringify(await verificarDemonstracao(demo)));
}
