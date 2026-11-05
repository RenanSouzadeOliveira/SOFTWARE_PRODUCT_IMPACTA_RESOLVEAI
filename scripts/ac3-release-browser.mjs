// Validação AC3: reutiliza os fluxos dos roteiros de filtros e histórico.
// Somente API e PostgreSQL locais descartáveis; promove/desativa contas fictícias recém-criadas.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { pathToFileURL, fileURLToPath } from 'node:url';
import { mkdirSync } from 'node:fs';
const { chromium } = await import(process.env.AC3_PLAYWRIGHT_MODULE ?? 'playwright');
const apiBase=process.env.AC3_API_URL ?? 'http://127.0.0.1:18239/api';
const webBase=process.env.AC3_WEB_URL ?? 'http://127.0.0.1:4239';
const pgPort=process.env.PGPORT ?? '54239', pgUser=process.env.PGUSER ?? 'history_test';
const database=process.env.HISTORY_DEMO_DATABASE;
assert.ok(database, 'Defina HISTORY_DEMO_DATABASE com o banco acadêmico descartável.');
for(const url of [apiBase,webBase]) assert.ok(['localhost','127.0.0.1'].includes(new URL(url).hostname));
const output=process.env.AC3_EVIDENCE_DIR ?? '/tmp/resolveai-release-evidence';
mkdirSync(output,{recursive:true});

async function validarLista(){
const root=fileURLToPath(new URL('..', import.meta.url));
const {prepararDemonstracao,verificarDemonstracao}=await import(pathToFileURL(root+'/scripts/ac3-filtros-demonstracao.mjs'));
const base=apiBase;
process.env.AC3_DEMO_EMAIL ??= `release-${randomUUID()}@example.invalid`;
process.env.AC3_DEMO_PASSWORD ??= randomUUID()+'aA1!';
const demo=await prepararDemonstracao(base);
console.log('API:',JSON.stringify(await verificarDemonstracao(demo)));
async function api(path,method,body,token){const r=await fetch(base+'/'+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:body?JSON.stringify(body):undefined});assert.ok(r.ok,`${method} ${path}: ${r.status}`);return r.json()}
const credentials={nome:'Agente fictício AC3',email:`agente-${Date.now()}@example.invalid`,senha:randomUUID()+'aA1!'};
const agent=await api('auth/register','POST',credentials);
assert.ok(Number.isSafeInteger(agent.id));
execFileSync('psql',['-h','127.0.0.1','-p',pgPort,'-U',pgUser,'-d',database,'-v','ON_ERROR_STOP=1','-c',`UPDATE usuarios SET perfil='ATENDENTE' WHERE id=${agent.id}`],{stdio:'pipe'});
const agentSession=await api('auth/login','POST',{email:credentials.email,senha:credentials.senha});
for(const c of demo.created.slice(0,25)) await api(`chamados/${c.id}/assumir`,'PATCH',{},agentSession.accessToken);
const browser=await chromium.launch({executablePath:process.env.AC3_CHROMIUM_PATH,headless:true,args:['--no-sandbox']});
const context=await browser.newContext({viewport:{width:1280,height:900},recordVideo:{dir:output+'/video-lista',size:{width:1280,height:900}}});

const page=await context.newPage();
const errors=[];page.on('pageerror',error=>errors.push(error.message));
const listing=()=>page.locator('[data-testid="chamados-success"]');
const total=page.getByRole('navigation',{name:'Paginação de chamados'});
async function expectText(locator,text){await locator.filter({hasText:text}).waitFor({timeout:15000});}
async function change(action,expected){const response=page.waitForResponse(r=>{const url=new URL(r.url());return url.pathname==='/api/chamados/me'&&r.status()===200&&Object.entries(expected).every(([k,v])=>url.searchParams.get(k)===v)},{timeout:15000});await action();await response;await listing().waitFor();}
try {
 await page.goto(`${webBase}/login`);
 await page.getByLabel('E-mail',{exact:true}).fill(process.env.AC3_DEMO_EMAIL);
 await page.getByLabel('Senha',{exact:true}).fill(process.env.AC3_DEMO_PASSWORD);
 await page.getByRole('button',{name:'Entrar',exact:true}).click();
 await page.waitForURL(url=>url.pathname!=='/login');
 await page.goto(`${webBase}/chamados`);await expectText(total,'45 chamado(s)');assert.equal(await page.locator('.ticket-card').count(),20);
 for(const [pageIndex,length] of [['1',20],['2',5]]) {
  await change(()=>page.getByRole('button',{name:'Próxima',exact:true}).click(),{page:pageIndex});
  assert.equal(await page.locator('.ticket-card').count(),length);
 }
 await change(()=>page.getByRole('button',{name:'Anterior',exact:true}).click(),{page:'1'});
 await change(()=>page.getByRole('button',{name:'Anterior',exact:true}).click(),{page:'0'});
 await change(()=>page.getByLabel('Buscar por protocolo ou texto').fill(demo.created[0].protocolo),{search:demo.created[0].protocolo});
 await expectText(total,'1 chamado(s)');
 await change(()=>page.getByRole('button',{name:'Limpar filtros',exact:true}).click(),{page:'0'});

 await change(()=>page.getByLabel('Status',{exact:true}).selectOption('EM_ATENDIMENTO'),{status:'EM_ATENDIMENTO'});
 await change(()=>page.getByLabel('Prioridade',{exact:true}).selectOption('ALTA'),{prioridade:'ALTA'});
 const day=demo.created[0].criadoEm.slice(0,10);
 await change(()=>page.getByLabel('Data inicial (UTC)').fill(day),{dataInicial:day});
 await change(()=>page.getByLabel('Data final (UTC)').fill(day),{dataFinal:day});
 await change(()=>page.getByLabel('Buscar por protocolo ou texto').fill('pOrTaL'),{search:'pOrTaL'});
 await expectText(total,'25 chamado(s)');await expectText(total,'Página 1 de 2');
 await change(()=>page.getByRole('button',{name:'Próxima',exact:true}).click(),{page:'1',search:'pOrTaL',status:'EM_ATENDIMENTO'});
 await expectText(total,'Página 2 de 2');assert.equal(await page.locator('.ticket-card').count(),5);
 const filteredUrl=page.url();await page.reload();await expectText(total,'25 chamado(s)');await expectText(total,'Página 2 de 2');
 assert.equal(page.url(),filteredUrl);assert.equal(await page.getByLabel('Data final (UTC)').inputValue(),day);assert.equal(await page.getByLabel('Buscar por protocolo ou texto').inputValue(),'pOrTaL');
 await change(()=>page.getByRole('button',{name:'Anterior',exact:true}).click(),{page:'0',search:'pOrTaL'});
 await page.goBack();await expectText(total,'Página 2 de 2');await page.goForward();await expectText(total,'Página 1 de 2');
 await change(()=>page.getByLabel('Responsável',{exact:true}).selectOption(String(agent.id)),{responsavel:String(agent.id)});await expectText(total,'25 chamado(s)');
 const category=demo.created[0].categoria.id;
 await change(()=>page.getByLabel('Categoria',{exact:true}).selectOption(String(category)),{categoriaId:String(category)});
 await expectText(total,'13 chamado(s)');
 await change(()=>page.getByRole('combobox',{name:'Ordenar por',exact:true}).selectOption('titulo,asc'),{sort:'titulo,asc',search:'pOrTaL',responsavel:String(agent.id),categoriaId:String(category)});
 await page.screenshot({path:output+'/lista-desktop.png',fullPage:true});
 await page.setViewportSize({width:360,height:800});await page.screenshot({path:output+'/lista-mobile.png',fullPage:true});
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,'overflow a 360px');
 await page.getByLabel('Buscar por protocolo ou texto').focus();await page.keyboard.press('Tab');assert.equal(await page.evaluate(()=>document.activeElement.id),'ticket-status');
 await change(()=>page.getByRole('button',{name:'Limpar filtros',exact:true}).click(),{size:'20',sort:'criadoEm,desc',page:'0'});
 await expectText(total,'45 chamado(s)');for(const key of ['search','status','prioridade','categoriaId','responsavel','dataInicial','dataFinal']) assert.equal(new URL(page.url()).searchParams.has(key),false);
 await change(()=>page.getByLabel('Buscar por protocolo ou texto').fill('pOrTaL'),{search:'pOrTaL'});await expectText(total,'25 chamado(s)');
 await change(()=>page.getByRole('button',{name:'Limpar filtros',exact:true}).click(),{size:'20',sort:'criadoEm,desc',page:'0'});await expectText(total,'45 chamado(s)');
 // Busca inexistente precisa manter controles de navegação desabilitados.
 const emptyResponse=page.waitForResponse(r=>r.url().includes('search=termo-inexistente')&&r.status()===200);
 await page.getByLabel('Buscar por protocolo ou texto').fill('termo-inexistente');await emptyResponse;
 await page.locator('[data-testid="chamados-empty"]').waitFor();assert.equal(await page.getByRole('button',{name:'Próxima',exact:true}).isDisabled(),true);assert.equal(await page.getByRole('button',{name:'Anterior',exact:true}).isDisabled(),true);
 assert.deepEqual(errors,[]);
 console.log(JSON.stringify({browser:'PASS',combinedFilters:true,pages:[20,20,5],filteredPages:[20,5],protocol:true,refresh:true,backForward:true,categoryAndAssignee:true,sortPreservesFilters:true,clear:true,emptyDisabled:true,width360:true,keyboard:true,pageErrors:0}));
const adminCredentials={nome:'Admin fictício AC3',email:`admin-${Date.now()}@example.invalid`,senha:randomUUID()+'aA1!'};
const adminUser=await api('auth/register','POST',adminCredentials);assert.ok(Number.isSafeInteger(adminUser.id));execFileSync('psql',['-h','127.0.0.1','-p',pgPort,'-U',pgUser,'-d',database,'-v','ON_ERROR_STOP=1','-c',`UPDATE usuarios SET perfil='ADMIN' WHERE id=${adminUser.id}`],{stdio:'pipe'});
const admin=await api('auth/login','POST',{email:adminCredentials.email,senha:adminCredentials.senha});
const global=await api('chamados','GET',undefined,admin.accessToken);

 for(const [role,session,target] of [['ATENDENTE',agentSession,'/atendimento/me'],['ADMIN',admin,'/consulta-chamados'],['ADMIN',admin,'/atendimento/todos']]) {
  const context=await browser.newContext({viewport:{width:360,height:800},locale:'pt-BR',recordVideo:{dir:output+'/video-perfis',size:{width:360,height:800}}});
  await context.addInitScript(session=>sessionStorage.setItem('resolveai.auth.session',JSON.stringify({...session,expiresAt:Date.now()+session.expiresIn*1000})),session);
  const page=await context.newPage(); const errors=[]; page.on('pageerror',e=>errors.push(e.message));
  await page.goto(webBase+target);
  const nav=page.getByRole('navigation',{name:'Paginação de chamados'});
  const expectTotal=n=>nav.filter({hasText:`${n} chamado(s)`}).waitFor({timeout:10000});
  await expectTotal(role==='ADMIN'?global.totalElements:25);
  await page.getByLabel('Buscar por protocolo ou texto').fill('portal');
  if(role==='ADMIN') await page.getByLabel('Responsável',{exact:true}).selectOption(String(agent.id));
  await page.waitForURL(url=>url.searchParams.get('search')==='portal');
  await expectTotal(25);
  await page.getByRole('combobox',{name:'Ordenar por',exact:true}).selectOption('titulo,asc');
  await page.waitForURL(url=>url.searchParams.get('sort')==='titulo,asc');
  await page.getByRole('button',{name:'Próxima',exact:true}).click();await nav.filter({hasText:'Página 2 de 2'}).waitFor();
  await page.reload();await nav.filter({hasText:'Página 2 de 2'}).waitFor();assert.equal(await page.getByLabel('Buscar por protocolo ou texto').inputValue(),'portal');
  await page.getByRole('button',{name:'Limpar filtros',exact:true}).click();await expectTotal(role==='ADMIN'?global.totalElements:25);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  if(role==='ATENDENTE') {
   await page.getByRole('link',{name:'Fila',exact:true}).click();await page.locator('.tickets').waitFor();assert.equal(await page.locator('#ticket-assignee').count(),0);
   await page.getByLabel('Buscar por protocolo ou texto').fill('inexistente-ficticio');await expectTotal(0);
  }
  assert.deepEqual(errors,[]);await context.close();
  console.log(role+' '+target+': PASS filtros/paginação/sort/refresh/limpeza/360px');
 }

} finally {await context.close();await browser.close()}

}
async function validarHistorico(){
const base=apiBase;
const nonce=Date.now();const senha=randomUUID()+'aA1!';
async function api(path, method='GET',body,token,expected=200){
 const r=await fetch(`${base}/${path}`,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},...(body?{body:JSON.stringify(body)}:{})});
 assert.equal(r.status,expected,`${method} ${path}`);return r.status===204?null:await r.json();
}
const credentials=role=>({nome:`Pessoa acadêmica ${role}`,email:`history-${nonce}-${role}@example.invalid`,senha});
const ownerCredentials=credentials('solicitante'),agentCredentials=credentials('atendente'),otherCredentials=credentials('outro');
await api('auth/register','POST',ownerCredentials,undefined,201);
const agent=await api('auth/register','POST',agentCredentials,undefined,201);
const otherUser=await api('auth/register','POST',otherCredentials,undefined,201);
assert.ok(Number.isSafeInteger(otherUser.id));
function sql(command){return execFileSync('psql',['-h','127.0.0.1','-p',pgPort,'-U',pgUser,'-d',database,'-v','ON_ERROR_STOP=1','-c',command],{stdio:'pipe'}).toString();}
sql(`UPDATE usuarios SET perfil='ATENDENTE' WHERE id=${agent.id}`);
const owner=await api('auth/login','POST',ownerCredentials);
const operator=await api('auth/login','POST',agentCredentials);
const other=await api('auth/login','POST',otherCredentials);
// Importação explícita de um chamado fictício antigo com seu evento na mesma instrução.
// Não altera nem remove eventos existentes; exercita filtro de data com dias distintos.
sql(`WITH antigo AS (
 INSERT INTO chamados(protocolo,titulo,descricao,prioridade,solicitante_id,categoria_id,criado_em)
 SELECT 'AC3-LEGADO-${nonce}','Solicitação acadêmica antiga','Registro fictício de dois dias antes para validar filtro de data.',
 'BAIXA',${otherUser.id},id,CURRENT_TIMESTAMP-INTERVAL '2 days' FROM categorias WHERE ativa=true ORDER BY id LIMIT 1
 RETURNING id,solicitante_id,criado_em
) INSERT INTO historico_chamados(chamado_id,autor_id,tipo_evento,status_novo,criado_em)
 SELECT id,solicitante_id,'CRIACAO','ABERTO',criado_em FROM antigo`);
assert.equal((await api('chamados','GET',undefined,other.accessToken)).totalElements,1);
assert.equal((await api('chamados?dataInicial='+new Date().toISOString().slice(0,10),'GET',undefined,other.accessToken)).totalElements,0);

const categories=await api('categorias','GET',undefined,owner.accessToken);
const ticket=await api('chamados','POST',{titulo:'Histórico acadêmico AC3',descricao:'Solicitação fictícia para demonstrar auditoria completa.',prioridade:'MEDIA',categoriaId:categories[0].id},owner.accessToken,201);
await api(`chamados/${ticket.id}/assumir`,'PATCH',{},operator.accessToken);
const browser=await chromium.launch({executablePath:process.env.AC3_CHROMIUM_PATH,headless:true,args:['--no-sandbox']});
const context=await browser.newContext({viewport:{width:1280,height:900},recordVideo:{dir:output+'/video-historico',size:{width:1280,height:900}}});
const errors=[];
async function sessionPage(session){const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));await page.addInitScript(s=>sessionStorage.setItem('resolveai.auth.session',JSON.stringify({...s,expiresAt:Date.now()+s.expiresIn*1000})),session);return page;}
async function count(page,n){await page.waitForFunction(n=>document.querySelectorAll('app-historico-chamado li').length===n,n);}
try{
 const page=await sessionPage(operator);
 let release;const gate=new Promise(resolve=>release=resolve);
 await page.route(`**/api/chamados/${ticket.id}/historico`,async route=>{await gate;await route.continue();},{times:1});
 await page.goto(`${webBase}/atendimento/chamados/${ticket.id}`);
 await page.getByText('Carregando histórico…',{exact:true}).waitFor();release();await count(page,3);
 await page.getByRole('button',{name:'Alterar prioridade',exact:true}).click();
 await page.getByLabel('Nova prioridade').selectOption('ALTA');
 await page.getByRole('button',{name:'Confirmar alteração',exact:true}).click();await count(page,4);
 assert.match(await page.locator('app-historico-chamado li').first().textContent(),/Prioridade alterada.*Anterior: Média.*Novo: Alta/s);
 await page.getByRole('button',{name:'Resolver chamado',exact:true}).click();
 await page.getByLabel('Solução',{exact:true}).fill('Solução acadêmica validada no navegador.');
 await page.getByRole('button',{name:'Confirmar alteração',exact:true}).click();await count(page,5);
 const ownerPage=await sessionPage(owner);
 await ownerPage.goto(`${webBase}/chamados/${ticket.id}`);await count(ownerPage,5);
 const timeline=ownerPage.locator('app-historico-chamado');
 assert.match(await timeline.textContent(),/Pessoa acadêmica atendente/);
 await ownerPage.screenshot({path:output+'/historico-desktop.png',fullPage:true});
 await ownerPage.setViewportSize({width:360,height:800});
 await ownerPage.screenshot({path:output+'/historico-mobile.png',fullPage:true});
 assert.ok(await ownerPage.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),'360px horizontal overflow');
 await ownerPage.route(`**/api/chamados/${ticket.id}/historico`,route=>route.fulfill({status:503,contentType:'application/json',body:'{"message":"Falha acadêmica temporária"}'}),{times:1});
 await ownerPage.reload();await ownerPage.getByRole('alert').filter({hasText:'Falha acadêmica temporária'}).waitFor();
 const retry=ownerPage.getByRole('button',{name:'Tentar carregar histórico novamente'});await retry.focus();await retry.press('Enter');await count(ownerPage,5);
 sql(`UPDATE usuarios SET ativo=false WHERE id=${agent.id}`);
 await ownerPage.reload();await count(ownerPage,5);assert.match(await timeline.textContent(),/Pessoa acadêmica atendente/);
 await api(`chamados/${ticket.id}/historico`,'GET',undefined,operator.accessToken,401);
 await api(`chamados/${ticket.id}/historico`,'GET',undefined,other.accessToken,404);
 const forbidden=await sessionPage(other);let historyRequests=0;forbidden.on('request',r=>{if(r.url().endsWith('/historico'))historyRequests++;});
 await forbidden.goto(`${webBase}/chamados/${ticket.id}`);
 await forbidden.locator('[data-testid="chamado-detail-error"]').waitFor();
 assert.equal(await forbidden.locator('app-historico-chamado').count(),0);assert.equal(historyRequests,0);
 const events=await api(`chamados/${ticket.id}/historico`,'GET',undefined,owner.accessToken);assert.equal(events.length,5);
 assert.equal(new Set(events.map(e=>e.id)).size,5);
 for(const e of events){assert.ok(Number.isFinite(Date.parse(e.criadoEm)));assert.ok(e.autor.id);assert.ok(e.valorNovo);}
 const times=await ownerPage.locator('app-historico-chamado time').evaluateAll(nodes=>nodes.map(n=>n.dateTime));
 assert.deepEqual(times,events.map(e=>e.criadoEm));
 assert.deepEqual(errors,[]);
 console.log(JSON.stringify({resultado:'PASS',ticketId:ticket.id,eventos:events.length,checks:['criação','atribuição+status','prioridade e status via UI com refresh','autor desativado','401/404 API e UI sem timeline','loading/erro/retry por Enter','360px sem overflow','sem erros JS'],screenshots:[output+'/historico-desktop.png',output+'/historico-mobile.png']}));
}finally{await context.close();await browser.close();}

}
await validarLista();
await validarHistorico();
