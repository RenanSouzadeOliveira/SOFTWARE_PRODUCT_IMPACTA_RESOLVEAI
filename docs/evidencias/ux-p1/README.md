# Evidências — Entrega final | P1 — Revisar experiência visual e responsividade

A validação pós-auditoria usou Chrome headless via Chrome DevTools Protocol, builds Angular de produção, o proxy local `/api` de `scripts/ac3-serve.mjs`, Spring no perfil `dev` e PostgreSQL 18 descartável. A massa tinha 21 chamados fictícios, dos quais 18 pertenciam ao solicitante usado na validação. Os e-mails de teste usam `@example.invalid`.

## Capturas da apresentação

| Arquivo | Conteúdo | Viewport |
| --- | --- | --- |
| `01-login-desktop-1440.png` / `02-login-mobile-360.png` | Login | 1440 × 900 / 360 × 800 |
| `03-lista-chamados-desktop-1440.png` | Meus chamados | 1440 × 900 |
| [Lista móvel, parte 1](04-lista-chamados-mobile-360.png) · [2](04-lista-chamados-mobile-360-parte-2.png) · [3](04-lista-chamados-mobile-360-parte-3.png) · [4](04-lista-chamados-mobile-360-parte-4.png) · [5](04-lista-chamados-mobile-360-parte-5.png) | A lista inteira, do cabeçalho ao rodapé, com cortes entre cartões | 360 × 800 |
| `05-detalhe-chamado-mobile-360.png` | Detalhe, histórico e comentários | 360 × 800 |
| `06-dashboard-desktop-1440.png` / `07-dashboard-mobile-360.png` | Dashboard (ADMIN) | 1440 × 900 / 360 × 800 |
| `08-admin-usuarios-desktop-1440.png` / `09-admin-usuarios-mobile-360.png` | Administração de usuários | 1440 × 900 / 360 × 800 |
| `10-validacao-login-mobile-360.png` / `10b-validacao-novo-chamado-mobile-360.png` | Validação junto aos campos | 360 × 800 |
| `11-dialogo-assumir-mobile-360.png` / `16-dialogo-desativar-usuario-mobile-360.png` | Diálogos de confirmação | 360 × 800 |
| `12-estado-vazio-filtros-mobile-320.png` / `12-estado-vazio-filtros-mobile-360.png` | Estado vazio por filtro com a ação “Abrir chamado” | 320 × 640 / 360 × 800 |
| `13-menu-movel-aberto-360.png` | Menu aberto com a seção ativa | 360 × 800 |
| `14-sucesso-abertura-mobile-360.png` | Confirmação de abertura com foco no título | 360 × 800 |
| `15-fila-atendimento-desktop-1440.png` | Fila de atendimento (ATENDENTE) | 1440 × 900 |
| `17-acesso-negado-mobile-360.png` | SOLICITANTE em rota de ADMIN | 360 × 800 |
| `viewports/` | Capturas da entrega anterior em 390, 768 e 1024 px | — |

A captura original de 360 px terminava em 4.000 px no meio de um cartão. A nova sequência cobre 8.300 px de altura contínua. O JSON registra os limites de cada parte, e a revisão visual conferiu os cartões nas junções e a paginação e o rodapé na última parte.

## Comparações com a mesma massa sintética

- `antes-depois/antes-fila-768.png` e `antes-depois/depois-fila-768.png` usam o baseline `407aead` e o build corrigido. São 13 cartões com os mesmos protocolos, títulos e ordem nos dois builds. As listas de cartões e suas dimensões constam nos JSON de varredura. A comparação mostra os rótulos visíveis de status e prioridade e a organização dos filtros.
- `antes-depois/antes-admin-usuarios-360.png` e `antes-depois/depois-admin-usuarios-360.png` mostram a administração nas mesmas condições, com as mesmas contas fictícias.
- `antes-depois/antes-estado-vazio-filtros-mobile-320.png` registra `9e1e681` e `12-estado-vazio-filtros-mobile-320.png` registra a correção com os mesmos filtros. O `scrollWidth` caiu de 340 para 320 CSS px. Em diagnóstico separado após a captura da versão original, permitir quebra de linha só no link do estado vazio fez o documento voltar a 320 px. A captura final mostra o texto revisado e o botão inteiro.

## Verificações automatizadas

- [verificacoes.json](verificacoes.json) reúne 27 verificações no Chrome. A identidade do acionador foi confirmada após Escape e Cancelar. A abertura focou a confirmação e Tab alcançou “Abrir outro chamado”. Houve um PATCH e um POST nas respectivas submissões repetidas. Nos três fluxos de paginação, o CDP reteve a requisição inicial e respondeu com um HTTP 503 controlado. “Tentar novamente” consultou a API real e exibiu a contagem recuperada e permitiu avançar para a segunda página.
- [verificacoes-controle-negativo.json](verificacoes-controle-negativo.json) registra as falhas esperadas de foco e overflow ao executar as mesmas asserções contra `9e1e681`. O botão estava habilitado e conectado em ambos os cancelamentos, enquanto o elemento focado era `BODY`.
- [varredura-antes.json](varredura-antes.json) e [varredura-depois.json](varredura-depois.json) registram 21 rotas e estados de quatro perfis de acesso em cinco viewports, totalizando 105 medições por build. O build corrigido teve zero casos de overflow. As dimensões usadas foram 320 × 640, 360 × 800, 390 × 844, 768 × 1024 e 1440 × 900.

## Reprodução

1. Suba o PostgreSQL e Spring descartáveis, com `SPRING_PROFILES_ACTIVE=dev`. Crie contas fictícias SOLICITANTE, ATENDENTE e ADMIN, chamados em estado aberto e um chamado já atribuído para a rota de detalhes. Salve o JSON `{senha, users, chamados}` fora do repositório. `users` mapeia os três perfis em minúsculas para pares `[nome, email]`. O primeiro ID em `chamados` deve ser visível ao solicitante, ao atendente e ao administrador.
2. Gere o build Angular com `npm run build` em `frontend/`. Inicie o proxy com `AC3_API_URL=http://127.0.0.1:<porta-api>/api AC3_WEB_PORT=<porta-web> node scripts/ac3-serve.mjs`.
3. Execute `UX_WEB_URL=http://127.0.0.1:<porta-web> UX_CREDS=<json> UX_OUT=<dir> UX_FOTOS=0 node scripts/ux-p1-evidencias.mjs` para a varredura e as imagens comparativas. Para comparar com `407aead`, sirva o build do commit a partir de uma cópia descartável, use `UX_REFERENCIA=antes` e a mesma massa antes das operações de escrita.
4. Execute `UX_WEB_URL=http://127.0.0.1:<porta-web> UX_CREDS=<json> UX_OUT=<dir> node scripts/ux-p1-fluxos.mjs`. Use `UX_CONTROLE_NEGATIVO=1` com o build descartável de `9e1e681` para reproduzir as três falhas esperadas. Nesse modo, o comando termina com código 1.

O roteiro requer Node.js com WebSocket nativo e Google Chrome (`UX_CHROME_PATH` para indicar outro binário). A validação incluiu foco, teclado e atributos de acessibilidade no Chrome. A leitura com tecnologia assistiva real ficou fora da execução.
