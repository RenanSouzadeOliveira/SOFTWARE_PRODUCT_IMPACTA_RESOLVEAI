# Evidências — Entrega final | P1 — Revisar experiência visual e responsividade

Capturas feitas em Chrome headless (via Chrome DevTools Protocol) contra o build de produção servido por `scripts/ac3-serve.mjs`, com API no perfil `dev` e PostgreSQL 17 descartável. Todos os nomes e e-mails são fictícios (`@example.invalid`); nenhuma senha ou token aparece nas imagens ou nos JSON.

## Capturas da apresentação

| Arquivo | Conteúdo | Viewport |
| --- | --- | --- |
| `01-login-desktop-1440.png` | Login | 1440 × 900 |
| `02-login-mobile-360.png` | Login | 360 × 800 |
| `03-lista-chamados-desktop-1440.png` | Meus chamados (SOLICITANTE) | 1440 × 900 |
| `04-lista-chamados-mobile-360.png` | Meus chamados, página inteira | 360 × 800 |
| `05-detalhe-chamado-mobile-360.png` | Detalhe, histórico e comentários | 360 × 800 |
| `06-dashboard-desktop-1440.png` | Dashboard (ADMIN) | 1440 × 900 |
| `07-dashboard-mobile-360.png` | Dashboard, página inteira | 360 × 800 |
| `08-admin-usuarios-desktop-1440.png` | Administração de usuários | 1440 × 900 |
| `09-admin-usuarios-mobile-360.png` | Administração de usuários | 360 × 800 |
| `10-validacao-login-mobile-360.png` / `10b-validacao-novo-chamado-mobile-360.png` | Validação junto aos campos | 360 × 800 |
| `11-dialogo-assumir-mobile-360.png` / `16-dialogo-desativar-usuario-mobile-360.png` | Diálogos de confirmação | 360 × 800 |
| `12-estado-vazio-filtros-mobile-360.png` | Estado vazio por filtro | 360 × 800 |
| `13-menu-movel-aberto-360.png` | Menu recolhível aberto | 360 × 800 |
| `14-sucesso-abertura-mobile-360.png` | Sucesso ao abrir chamado | 360 × 800 |
| `15-fila-atendimento-desktop-1440.png` | Fila de atendimento (ATENDENTE) | 1440 × 900 |
| `17-acesso-negado-mobile-360.png` | SOLICITANTE em rota de ADMIN | 360 × 800 |
| `viewports/` | Lista em 390, dashboard em 768, categorias em 1024 | — |
| `antes-depois/` | Menu e perfis (360) e indicadores na fila (768), antes e depois | — |

## Verificações automatizadas

- `verificacoes.json`: 16 verificações de fluxo (teclado, foco, diálogos, validação, estado vazio, sucesso, dupla submissão, guarda de rota) — saída de `scripts/ux-p1-fluxos.mjs`.
- `varredura-antes.json` / `varredura-depois.json`: 20 rotas × 5 viewports (360, 390, 768, 1024, 1440) por perfil, medindo rolagem horizontal do documento e elementos fora da viewport — saída de `scripts/ux-p1-evidencias.mjs`.

## Reprodução

1. Suba PostgreSQL descartável, a API com `SPRING_PROFILES_ACTIVE=dev` e `npm run build` em `frontend/`.
2. `AC3_API_URL=http://127.0.0.1:<porta-api>/api AC3_WEB_PORT=4471 node scripts/ac3-serve.mjs`
3. Crie contas fictícias (SOLICITANTE, ATENDENTE, ADMIN) e um JSON local **fora do repositório** com `{senha, users, chamados}`.
4. `UX_CREDS=<json> UX_OUT=docs/evidencias/ux-p1 node scripts/ux-p1-fluxos.mjs`
5. `UX_CREDS=<json> UX_OUT=<dir> node scripts/ux-p1-evidencias.mjs`

Requer Google Chrome (`UX_CHROME_PATH` para outro binário). Não substitui teste com leitor de tela real, que não foi executado.
