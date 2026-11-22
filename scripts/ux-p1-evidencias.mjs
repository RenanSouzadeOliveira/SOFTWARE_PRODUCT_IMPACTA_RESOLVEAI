// Varredura visual P1: percorre rotas por perfil em 5 viewports, mede overflow e salva capturas.
// Uso: UX_CREDS=<json local fora do repositório> UX_OUT=<diretório> node scripts/ux-p1-evidencias.mjs
// O JSON contém contas fictícias descartáveis: {senha, users:{perfil:[nome,email]}, chamados:[ids]}.
import assert from 'node:assert/strict';
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { abrirChrome } from './ux-p1-cdp.mjs';

const web = new URL(process.env.UX_WEB_URL ?? 'http://127.0.0.1:4471');
if (web.protocol !== 'http:' || !['127.0.0.1', 'localhost'].includes(web.hostname)) throw new Error('Use somente ambiente local descartável.');
const creds = JSON.parse(readFileSync(process.env.UX_CREDS, 'utf8'));
const out = process.env.UX_OUT ?? 'docs/evidencias/ux-p1/varredura';
const fotos = process.env.UX_FOTOS !== '0';
const referencia = process.env.UX_REFERENCIA ?? 'depois';
const comparacoes = [];
mkdirSync(out, { recursive: true });

const viewports = [[320, 640], [360, 800], [390, 844], [768, 1024], [1440, 900]];
const [c1] = creds.chamados;
const rotaVazio = '/chamados?status=FECHADO&prioridade=ALTA';
const rotas = {
  publico: ['/', '/login', '/cadastro', '/rota-inexistente'],
  solicitante: ['/chamados', rotaVazio, '/chamados/novo', `/chamados/${c1}`, '/dashboard', '/minha-conta', '/acesso-negado'],
  atendente: ['/atendimento', '/atendimento/me', `/atendimento/chamados/${c1}`, '/dashboard'],
  admin: ['/dashboard', '/atendimento/todos', '/consulta-chamados', '/admin/usuarios', '/admin/categorias', `/atendimento/todos/${c1}`],
};

const b = await abrirChrome();
const relatorio = [];
async function entrar(perfil) {
  await b.avaliar('sessionStorage.clear()').catch(() => {});
  await b.ir(new URL('/login', web).href);
  await b.esperar("document.querySelector('#login-email')");
  await b.digitar('#login-email', creds.users[perfil][1]);
  await b.digitar('#login-senha', creds.senha);
  await b.avaliar("document.querySelector('form button[type=submit]').click()");
  await b.esperar("!location.pathname.startsWith('/login')");
}
try {
  for (const [perfil, lista] of Object.entries(rotas)) {
    await b.viewport(1440, 900);
    if (perfil === 'publico') { await b.ir(web.href); await b.avaliar('sessionStorage.clear()'); } else await entrar(perfil);
    for (const rota of lista) {
      for (const [w, h] of viewports) {
        await b.viewport(w, h);
        await b.ir(new URL(rota, web).href);
        await b.esperar('document.querySelector("main h1")');
        if (rota === rotaVazio) await b.esperar('document.querySelector("[data-testid=chamados-empty]")');
        if (rota === '/atendimento') await b.esperar('document.querySelector("article.ticket")');
        if (rota === '/chamados') await b.esperar('document.querySelector("[data-testid=chamados-success]")');
        if (rota.startsWith('/admin/')) await b.esperar('document.querySelector(".records")');
        const m = await b.overflow();
        relatorio.push({ perfil, rota, viewport: `${w}x${h}`, final: await b.avaliar('location.pathname'), ...m });
        if (fotos) await b.foto(`${out}/${perfil}${new URL(rota, web).pathname.replaceAll('/', '_') || '_inicio'}${rota === rotaVazio ? '-vazio' : ''}-${w}.png`, true);
        if (perfil === 'atendente' && rota === '/atendimento' && w === 768) {
          const cartoes = await b.avaliar(`Array.from(document.querySelectorAll('article.ticket'), c => ({
            protocolo: c.querySelector('.protocol').textContent.trim(),
            titulo: c.querySelector('h2').textContent.trim(),
            largura: Math.round(c.getBoundingClientRect().width),
            altura: Math.round(c.getBoundingClientRect().height),
          }))`);
          assert.ok(cartoes.length);
          const alturaDocumento = await b.avaliar('document.documentElement.scrollHeight');
          comparacoes.push({ rota, viewport: `${w}x${h}`, alturaDocumento, cartoes });
          await b.foto(`${out}/${referencia}-fila-768.png`, true);
        }
        if (perfil === 'admin' && rota === '/admin/usuarios' && w === 360) {
          await b.foto(`${out}/${referencia}-admin-usuarios-360.png`, true);
        }
      }
    }
  }
  writeFileSync(`${out}/relatorio.json`, JSON.stringify({ relatorio, comparacoes, errosConsole: b.erros }, null, 1) + '\n');
  const falhas = relatorio.filter((r) => r.rolagemHorizontal || r.fora.length);
  console.log(JSON.stringify({ verificacoes: relatorio.length, comOverflow: falhas.map((f) => `${f.perfil} ${f.rota} ${f.viewport} ${f.scrollWidth} ${f.fora.join(',')}`), errosConsole: b.erros }, null, 1));
  if (referencia === 'depois' && (falhas.length || b.erros.length)) process.exitCode = 1;
} finally {
  await b.fechar();
}
