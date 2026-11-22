// Cliente mínimo do Chrome DevTools Protocol para evidências visuais (sem Playwright).
// Somente para ambiente local descartável com dados fictícios.
import { spawn } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export async function abrirChrome({ porta = 0, executavel = process.env.UX_CHROME_PATH ?? 'google-chrome' } = {}) {
  const perfil = mkdtempSync(path.join(tmpdir(), 'resolveai-ux-chrome-'));
  const proc = spawn(executavel, ['--headless=new', `--remote-debugging-port=${porta}`, `--user-data-dir=${perfil}`,
    '--no-first-run', '--no-default-browser-check', '--hide-scrollbars', 'about:blank'], { stdio: 'ignore' });
  const portaAtiva = path.join(perfil, 'DevToolsActivePort');
  let alvo;
  let falhaChrome;
  proc.once('error', (erro) => { falhaChrome = erro; });
  for (let i = 0; i < 50 && !alvo; i++) {
    await sleep(200);
    if (falhaChrome || proc.exitCode !== null) break;
    if (!porta && existsSync(portaAtiva)) porta = Number(readFileSync(portaAtiva, 'utf8').split('\n')[0]);
    if (!porta) continue;
    alvo = await fetch(`http://127.0.0.1:${porta}/json/list`).then((r) => r.json()).then((l) => l.find((t) => t.type === 'page')).catch(() => null);
  }
  if (!alvo) {
    proc.kill();
    throw new Error('Chrome não iniciou.', { cause: falhaChrome });
  }
  const ws = new WebSocket(alvo.webSocketDebuggerUrl);
  await new Promise((ok, erro) => { ws.onopen = ok; ws.onerror = erro; });
  let id = 0;
  const pendentes = new Map();
  const erros = [];
  const requisicoes = [];
  const respostasSimuladas = new Set();
  let pausa;
  ws.onmessage = ({ data }) => {
    const msg = JSON.parse(data);
    if (msg.id && pendentes.has(msg.id)) { pendentes.get(msg.id)(msg); pendentes.delete(msg.id); }
    if (msg.method === 'Runtime.exceptionThrown') erros.push(msg.params.exceptionDetails.text);
    if (msg.method === 'Runtime.consoleAPICalled' && msg.params.type === 'error') erros.push(msg.params.args.map((a) => a.value ?? a.description).join(' '));
    if (msg.method === 'Network.requestWillBeSent') requisicoes.push(`${msg.params.request.method} ${new URL(msg.params.request.url).pathname}`);
    if (msg.method === 'Fetch.requestPaused') {
      const { requestId, networkId, request } = msg.params;
      if (pausa && !pausa.requestId && request.method === 'GET' && new URL(request.url).pathname === pausa.caminho) {
        pausa.requestId = requestId;
        pausa.networkId = networkId;
      } else {
        enviar('Fetch.continueRequest', { requestId }).catch((erro) => erros.push(erro.message));
      }
    }
    if (msg.method === 'Network.responseReceived' && msg.params.response.status >= 500 &&
      !respostasSimuladas.delete(msg.params.requestId)) {
      erros.push(`HTTP ${msg.params.response.status} ${msg.params.response.url}`);
    }
    if (msg.method === 'Network.loadingFailed' && !msg.params.canceled) erros.push(`Falha de rede ${msg.params.errorText}`);
  };
  const enviar = (method, params = {}) => new Promise((ok, erro) => {
    const n = ++id;
    pendentes.set(n, (m) => (m.error ? erro(new Error(`${method}: ${m.error.message}`)) : ok(m.result)));
    ws.send(JSON.stringify({ id: n, method, params }));
  });
  await enviar('Page.enable'); await enviar('Runtime.enable'); await enviar('Network.enable');

  const avaliar = async (expressao) => {
    const r = await enviar('Runtime.evaluate', { expression: expressao, awaitPromise: true, returnByValue: true });
    if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description ?? r.exceptionDetails.text);
    return r.result.value;
  };
  const esperar = async (expressao, ms = 10000) => {
    for (let t = 0; t < ms; t += 100) { if (await avaliar(expressao).catch(() => false)) return; await sleep(100); }
    throw new Error(`Tempo esgotado aguardando: ${expressao}`);
  };
  async function capturar(arquivo, clip) {
    const params = { format: 'png', captureBeyondViewport: !!clip, ...(clip && { clip }) };
    const png = Buffer.from((await enviar('Page.captureScreenshot', params)).data, 'base64');
    writeFileSync(arquivo, png);
    return { largura: png.readUInt32BE(16), altura: png.readUInt32BE(20) };
  }
  return {
    enviar, avaliar, esperar, erros, requisicoes, sleep,
    async pausarProximoGet(url, navegou) {
      const { pathname } = new URL(url);
      if (pausa) throw new Error('Já existe uma requisição sob controle do teste.');
      pausa = { caminho: pathname };
      try {
        await enviar('Fetch.enable', { patterns: [{ urlPattern: url + '*', requestStage: 'Request' }] });
        await navegou();
        for (let i = 0; !pausa.requestId && i < 50; i++) await sleep(100);
        if (!pausa.requestId) throw new Error(`GET não interceptado: ${pathname}`);
      } catch (erro) {
        await enviar('Fetch.disable');
        pausa = undefined;
        throw erro;
      }
      return async (falhar) => {
        const { requestId, networkId } = pausa;
        try {
          if (falhar) {
            respostasSimuladas.add(networkId);
            await enviar('Fetch.fulfillRequest', {
              requestId,
              responseCode: 503,
              responseHeaders: [{ name: 'content-type', value: 'application/json' }],
              body: Buffer.from(JSON.stringify({ message: 'Indisponibilidade simulada.' })).toString('base64'),
            });
          } else {
            await enviar('Fetch.continueRequest', { requestId });
          }
        } finally {
          await enviar('Fetch.disable');
          pausa = undefined;
        }
      };
    },
    async viewport(width, height) {
      await enviar('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: width < 768 });
    },
    async ir(url) { await enviar('Page.navigate', { url }); await sleep(300); await esperar("document.readyState==='complete'"); await sleep(400); },
    async tecla(key, code = key, keyCode = 0, shift = false) {
      const base = { key, code, windowsVirtualKeyCode: keyCode, modifiers: shift ? 8 : 0 };
      // Enter precisa de texto para gerar a ativação nativa de botões.
      await enviar('Input.dispatchKeyEvent', { type: 'keyDown', ...base, ...(key === 'Enter' ? { text: '\r' } : {}) });
      await enviar('Input.dispatchKeyEvent', { type: 'keyUp', ...base });
    },
    async digitar(seletor, texto) {
      await avaliar(`document.querySelector(${JSON.stringify(seletor)}).focus()`);
      await avaliar(`(()=>{const e=document.querySelector(${JSON.stringify(seletor)}); if (e.select) e.select();})()`);
      await enviar('Input.insertText', { text: texto });
    },
    async foto(arquivo, inteira = false) {
      if (!inteira) return capturar(arquivo);
      const { cssContentSize } = await enviar('Page.getLayoutMetrics');
      return capturar(arquivo, { x: 0, y: 0, ...cssContentSize, scale: 1 });
    },
    // Divide capturas longas no espaço entre cartões, sem recortar cartões nem omitir conteúdo.
    async fotosSequenciais(arquivoBase, seletor, limite = 2200) {
      const { cssContentSize: { width, height } } = await enviar('Page.getLayoutMetrics');
      const cortes = await avaliar(`Array.from(document.querySelectorAll(${JSON.stringify(seletor)}),
        e => (e.getBoundingClientRect().top + scrollY))
        .slice(1).map(Math.floor)`);
      const partes = [];
      for (let y = 0; y < height;) {
        const ate = height - y <= limite ? height :
          cortes.findLast((corte) => corte > y && corte <= y + limite) ??
          cortes.find((corte) => corte > y) ?? height;
        const nome = partes.length ? `${arquivoBase}-parte-${partes.length + 1}.png` : `${arquivoBase}.png`;
        const dimensoes = await capturar(nome, { x: 0, y, width, height: ate - y, scale: 1 });
        partes.push({ arquivo: path.basename(nome), inicio: y, fim: ate, ...dimensoes });
        y = ate;
      }
      return { alturaDocumento: height, partes };
    },
    // Mede rolagem horizontal do documento e elementos visíveis que ultrapassam a viewport.
    overflow() {
      return avaliar(`(()=>{const w=document.documentElement.clientWidth;const fora=[];
        for(const e of document.querySelectorAll('body *')){const r=e.getBoundingClientRect();
          if(r.width&&r.height&&(r.right>w+1||r.left<-1)){let p=e.parentElement,contido=false;
            while(p){const s=getComputedStyle(p);if(/(auto|scroll|hidden)/.test(s.overflowX)){const pr=p.getBoundingClientRect();if(pr.right<=w+1&&pr.left>=-1){contido=true;break;}}p=p.parentElement;}
            if(!contido)fora.push(e.tagName.toLowerCase()+(e.className&&typeof e.className==='string'?'.'+e.className.split(' ')[0]:''));}}
        return {scrollWidth:document.documentElement.scrollWidth,largura:w,rolagemHorizontal:document.documentElement.scrollWidth>w,fora:[...new Set(fora)].slice(0,8)};})()`);
    },
    async fechar() {
      if (proc.exitCode === null && proc.signalCode === null) {
        const saiu = new Promise((resolver) => proc.once('exit', resolver));
        const limite = setTimeout(() => proc.kill(), 5000);
        if (ws.readyState === WebSocket.OPEN) {
          enviar('Browser.close').catch(() => proc.kill());
        } else {
          proc.kill();
        }
        await saiu;
        clearTimeout(limite);
      }
      ws.close();
      await rm(perfil, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 });
    },
  };
}
