# AC3 — sincronização do repositório

Data: 05/11/2026. Esta etapa complementa o [relatório histórico de validação](ac3-release.md). Os testes abaixo foram executados novamente sobre a integração; os resultados anteriores e os HTTP 404 daquele relatório descrevem o estado anterior ao push.

## Histórico preservado

A branch local tinha os três commits AC3 (`162ce37`, `ac82b74`, `facf63d`), enquanto o remoto tinha três commits AC2 adicionais, terminando em `0711a5b`. O commit local `5c3dc86` consolida documentação e o `.gitignore`. A integração usa merge, sem force-push e sem descartar nenhum dos históricos. A revisão resultante pode ser obtida com `git log -1 --format=%H` no commit que inclui esta nota.

Foram preservadas a visão administrativa `/atendimento/todos`, a consulta AC3 `/consulta-chamados`, os logs após commit e os roteiros AC2 existentes no remoto. Os conflitos do atendimento foram resolvidos reutilizando os filtros AC3, com busca, datas, ordenação e paginação padrão de 20, limite 100. A autorização exclusiva de ADMIN no endpoint administrativo permanece ativa.

## Arquivos locais e proteção

O `.gitignore` versionado exclui AGENTS.md/AGENTS.override.md, `.agents`, `.codex`, planejamento acadêmico local, ambientes, dependências, builds e logs. O exemplo oficial `backend/.env.example` permanece versionado. README, documentação acadêmica e roteiros AC3 foram mantidos porque são necessários à reprodução e à entrega.

As quatro referências Git auxiliares com conteúdo ou nomes inválidos foram preservadas em `.git/codex-ref-recovery-20261105/` antes de sair do namespace de referências. Os três auxiliares AC2 locais anteriores foram preservados em `.git/local-ac2-backup-20261105/`; as versões completas e já versionadas do remoto passaram a ocupar seus caminhos. Nenhum backup ou arquivo de agente integra o commit.

## Verificação desta integração

- Maven `clean verify`: 96 testes, zero falhas/erros/skips; JAR gerado.
- Suíte PostgreSQL 18 em bancos novos `publish_runtime`/`publish_upgrade`: 96 testes, zero falhas/erros/skips, incluindo V1–V9, upgrade e imutabilidade. Inclui o reforço dos testes de filtros e sort da listagem administrativa integrada.
- Angular: `npm ci`, 109 testes aprovados em 23 arquivos e build de produção aprovado.
- Chromium com build de produção e JAR integrados: solicitante, atendente e ADMIN; `/atendimento/me`, `/consulta-chamados` e `/atendimento/todos`; filtros, ordenação, paginação, reload e 360px.
- Timeline com prioridade/status alterados pela interface, autor e valores anteriores/novos, timestamps, persistência, autor desativado, acesso negado e recuperação de erro. Sem erros JavaScript observados.
- O roteiro AC2 foi adaptado ao limite de paginação AC3 e ao evento de criação já existente antes de uma transação revertida. O roteiro Docker AC2 completo não foi reexecutado nesta sincronização.

Ambiente: Java 21, Maven 3.9.5, Node 24/npm 10.9.2, PostgreSQL 18. Os limites dos ambientes documentados e da auditoria de acessibilidade permanecem os descritos no relatório histórico. Logs desta etapa ficam fora do repositório, em `/tmp/resolveai-publish-*.log`.

## Publicação

O usuário autorizou explicitamente commit e push da integração. A entrega acadêmica pública completa continua dependendo de tag/release, vídeo narrado publicado e atualização autorizada do Trello. Nenhuma dessas ações adicionais é incluída nesta sincronização. A disponibilidade pública dos commits deve ser confirmada após o push.
