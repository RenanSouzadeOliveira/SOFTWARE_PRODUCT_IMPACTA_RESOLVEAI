// Varredura visual P1: percorre rotas por perfil em 5 viewports, mede overflow e salva capturas.
// Uso: UX_CREDS=<json local fora do repositório> UX_OUT=<diretório> node scripts/ux-p1-evidencias.mjs
// O JSON contém contas fictícias descartáveis: {senha, users:{perfil:[nome,email]}, chamados:[ids]}.
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { abrirChrome } from './ux-p1-cdp.mjs';

const web = new URL(process.env.UX_WEB_URL ?? 'http://127.0.0.1:4471');
if (!['127.0.0.1', 'localhost'].includes(web.hostname)) throw new Error('Use somente ambiente local descartável.');
const creds = JSON.parse(readFileSync(process.env.UX_CREDS, 'utf8'));
const out = process.env.UX_OUT ?? 'docs/evidencias/ux-p1/varredura';
const fotos = process.env.UX_FOTOS !== '0';
mkdirSync(out, { recursive: true });

const viewports = [[360, 800], [390, 844], [768, 1024], [1024, 768], [1440, 900]];
const [c1] = creds.chamados;
const rotas = {
  publico: ['/', '/login', '/cadastro', '/rota-inexistente'],
  solicitante: ['/chamados', '/chamados/novo', `/chamados/${c1}`, '/dashboard', '/minha-conta', '/acesso-negado'],
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
for (const [perfil, lista] of Object.entries(rotas)) {
  await b.viewport(1440, 900);
  if (perfil === 'publico') { await b.ir(web.href); await b.avaliar('sessionStorage.clear()'); } else await entrar(perfil);
  for (const rota of lista) {
    for (const [w, h] of viewports) {
      await b.viewport(w, h);
      await b.ir(new URL(rota, web).href);
      await b.sleep(500);
      const m = await b.overflow();
      relatorio.push({ perfil, rota, viewport: `${w}x${h}`, final: await b.avaliar('location.pathname'), ...m });
      if (fotos) await b.foto(`${out}/${perfil}${rota.replaceAll('/', '_') || '_inicio'}-${w}.png`, true);
    }
  }
}
writeFileSync(`${out}/relatorio.json`, JSON.stringify({ relatorio, errosConsole: b.erros }, null, 1));
const falhas = relatorio.filter((r) => r.rolagemHorizontal || r.fora.length);
console.log(JSON.stringify({ verificacoes: relatorio.length, comOverflow: falhas.map((f) => `${f.perfil} ${f.rota} ${f.viewport} ${f.scrollWidth} ${f.fora.join(',')}`), errosConsole: b.erros }, null, 1));
b.fechar();
