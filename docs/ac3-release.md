# ResolveAí — validação integrada e release acadêmica AC3

Registro histórico da validação anterior à sincronização dos branches. Os números e HTTP abaixo pertencem exclusivamente à revisão `facf63d`; a integração posterior é descrita em [Sincronização Git](ac3-sincronizacao.md).

Data: 05/11/2026. Estado geral: **PARTIALLY VERIFIED**. A implementação passou na validação técnica descrita abaixo; a entrega pública acadêmica permanece pendente. Nenhum push, tag, release, publicação de vídeo ou alteração no Trello foi realizado.

## Revisão e auditoria Git

- Revisão da aplicação efetivamente testada: `facf63df6fbd866724cb4d21d494837905064ae9`, branch original `main`.
- Commits presentes e ancestrais do HEAD: `162ce37` (paginação/ordenação), `ac82b74` (pesquisa/filtros), `facf63d` (histórico completo). O histórico **já estava commitado**.
- V7/V8 são migrations SQL; V9 é `backend/src/main/java/db/migration/V9__complete_ticket_history.java`.
- Quatro arquivos anteriores não rastreados foram preservados e excluídos desta entrega: `docs/ACADEMIC_DELIVERY_PLAN.md`, `scripts/ac2-api.mjs`, `scripts/ac2-browser.mjs`, `scripts/validar-ac2.sh`. Não são dependências dos builds ou do novo roteiro AC3. Os scripts API/shell AC2 estavam vazios.
- O caminho de BACKUP configurado na sessão estava indisponível. Foi utilizado o repositório de Documentos confirmado pelo usuário.
- A leitura pública de `refs/heads/main` retornou `0711a5bd315319710ea4b8c314d669c98386c9f1`, diferente do HEAD local validado.
- Referências auxiliares inválidas em `.git/refs` impediram a clonagem local comum. Não foram alteradas. Um repositório isolado recebeu somente os objetos alcançáveis do HEAD e fez checkout destacado desse mesmo SHA. Nenhuma cópia temporária antiga de fontes foi usada.
- Estes documentos e roteiros de validação são alterações posteriores ao SHA testado, preparadas para revisão/commit; nenhuma fonte funcional ou migration foi modificada. Não há commit novo desta rodada.

## Matriz de aceite

| Critério | Resultado | Evidência desta execução |
| --- | --- | --- |
| AC1 — builds/testes padrão | Atendido no ambiente testado | Maven clean verify: 93 testes; npm ci; Angular: 105 testes; JAR e build produção |
| AC2 — busca/filtros/paginação/URL | Atendido | API e Chromium: 20/20/5; filtros AND 20/5; protocolo, texto, categoria/responsável/datas, sort, reload, voltar/avançar, limpar e vazio |
| AC3 — auditoria íntegra | Atendido | Testes de atomicidade, rollback, concorrência, deduplicação e imutabilidade; timeline com cinco eventos, autor, timestamp e valores |
| AC4 — autorização | Atendido | Suítes com filtros de segurança ativos; três perfis no Chromium; 401/404 em histórico, escopos/totais e autor desativado |
| AC5 — PostgreSQL/Flyway | Atendido no PostgreSQL 18 | 93 testes; V1→V9 limpa, V8→V9 com legado, checksums iguais, FKs/triggers e EXPLAIN |
| AC6 — documentação | Atendido localmente; commit pendente | README e textos corrigidos; OpenAPI real inspecionado; MER Mermaid pós-V9 já correto e preservado |
| AC7 — demonstração reproduzível | Atendido localmente | Roteiros existentes + harness browser reproduzido; quatro screenshots e seis segmentos WebM |
| AC8 — evidência pública | Pendente | Três URLs de commits e documentos nesse SHA: HTTP 404 sem autenticação; release específica e vídeo público ausentes |

## Resultados e limites

Ambiente: Java 21.0.12.1, Maven 3.9.5, Node 24.22.1, npm 10.9.2, PostgreSQL 18.6 e Chromium. O Node 22 documentado e o PostgreSQL 17 do Compose não foram reexecutados nesta rodada.

1. `mvn -B -ntp clean verify`: **93 testes, zero falhas/erros/skips**, compilação e empacotamento aprovados.
2. `npm ci`, `npm test -- --watch=false`, `npm run build`: instalação padrão, **105 testes em 23 arquivos**, produção aprovada. O sandbox inicialmente bloqueou esbuild; os mesmos comandos passaram fora dele, sem alterar configuração de build.
3. Suíte completa com datasource PostgreSQL e `HISTORY_MIGRATION_URL`: **93 testes, zero falhas/erros/skips**. V9 rejeitou UPDATE/DELETE/TRUNCATE nas duas tabelas; preservação do legado e FKs verificadas. Checksums V1–V9 da instalação limpa e do upgrade são iguais. Os upgrades V6→V7 e V7→V8 têm fixtures/testes H2; não foram isoladamente reexecutados no PostgreSQL.
4. Chromium sobre o JAR e o build de produção: login do solicitante pelo formulário; três perfis; protocolo e texto parcial; filtros combinados; páginas; URL/reload/histórico do navegador; limpeza; vazio; ausência de overflow a 360px e navegação por Tab. ATENDENTE e ADMIN autenticam pela API e suas sessões são injetadas no browser; não se afirma login visual desses dois perfis.
5. Timeline: atribuição e status, prioridade e resolução; refresh automático; valores anteriores/novos; timestamps da interface iguais aos DTOs; persistência sem duplicação; autor identificado após desativação; loading, erro 503 simulado e retry por Enter. Nenhum erro JavaScript observado. A auditoria de acessibilidade limita-se às verificações executadas, sem certificação completa.
6. Scripts existentes de planos executados com 50 mil registros e ROLLBACK: uso dos índices de solicitante, fila e carteira. O script de filtros compara temporariamente ausência/presença do índice global e reverte tudo. Nenhum índice/extension/migration permanente foi criado. A consulta unificada real de histórico foi extraída da fonte e teve EXPLAIN registrado; isso não constitui benchmark de carga do histórico.
7. OpenAPI real contém os dez parâmetros combinados e o endpoint de histórico com respostas 200/400/401/404. Não foi necessário alterar anotações ou DTOs.

Limitações existentes: paginação por offset não fornece snapshot entre requisições concorrentes; busca parcial pode custar mais em grandes volumes; histórico não paginado; nomes de autores vêm do cadastro atual, sem snapshot histórico de renomeações; eventos antigos ausentes não são inventados. Nenhuma dessas limitações foi ocultada por novas migrations.

## Reprodução

Use somente infraestrutura local descartável, sem volumes/dados de produção. Instale os pré-requisitos do README. Os comandos abaixo não usam fontes não commitadas da aplicação.

Checkout limpo local, quando referências auxiliares impedirem `git clone`:

```bash
export REPO=/caminho/confirmado/do/repositorio
export REV=facf63df6fbd866724cb4d21d494837905064ae9
export AC3_CHECKOUT="$(mktemp -d /tmp/resolveai-ac3-checkout.XXXXXX)"
git init "$AC3_CHECKOUT"
printf '%s\n' "$REV" | git -C "$REPO" pack-objects --stdout --revs | git -C "$AC3_CHECKOUT" index-pack --stdin
git -C "$AC3_CHECKOUT" checkout --detach "$REV"
cd "$AC3_CHECKOUT"
mvn -f backend/pom.xml -B -ntp clean verify
cd frontend
npm ci
npm test -- --watch=false
npm run build
cd ..
```

Para usar os novos scripts e documentos por checkout, primeiro é necessário revisar/commitar o conjunto de validação e escolher o SHA resultante. A aplicação validada continua sendo o SHA acima. Não substitua arquivos funcionalmente durante o build.

PostgreSQL isolado (binários `initdb`, `pg_ctl`, `createdb`, `psql` no PATH):

```bash
export AC3_WORK="$(mktemp -d /tmp/resolveai-ac3-db.XXXXXX)"
export PGHOST=127.0.0.1 PGPORT=54239 PGUSER=history_test
initdb -D "$AC3_WORK/pg" -U history_test --auth=trust --encoding=UTF8 --locale=C.UTF-8
pg_ctl -D "$AC3_WORK/pg" -l "$AC3_WORK/pg.log" -o "-p $PGPORT -h 127.0.0.1 -k $AC3_WORK" start
createdb release_runtime
createdb release_upgrade
createdb release_demo
HISTORY_MIGRATION_URL=jdbc:postgresql://127.0.0.1:54239/release_upgrade \
  mvn -f backend/pom.xml -B -ntp verify \
  -Dspring.datasource.url=jdbc:postgresql://127.0.0.1:54239/release_runtime \
  -Dspring.datasource.username=history_test -Dspring.datasource.password= \
  -Dspring.datasource.driver-class-name=org.postgresql.Driver
```

Cada execução do teste de upgrade exige banco `release_upgrade` novo. O trust é exclusivo deste cluster acadêmico descartável em loopback.

Em outro terminal, exporte as mesmas variáveis PG e inicie a aplicação:

```bash
DB_URL=jdbc:postgresql://127.0.0.1:54239/release_demo \
DB_USERNAME=history_test DB_PASSWORD= JWT_SECRET="$(openssl rand -hex 32)" \
CORS_ALLOWED_ORIGINS=http://127.0.0.1:4239 \
java -jar backend/target/resolveai-api-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=dev --server.port=18239
```

No checkout que contém os roteiros de validação revisados:

```bash
AC3_API_URL=http://127.0.0.1:18239/api node scripts/ac3-demonstracao.mjs
AC3_API_URL=http://127.0.0.1:18239/api node scripts/ac3-filtros-demonstracao.mjs
HISTORY_API_URL=http://127.0.0.1:18239/api HISTORY_DEMO_DATABASE=release_demo \
  HISTORY_DEMO_PASSWORD="$(openssl rand -hex 16)" \
  node scripts/ac3-historico-demonstracao.mjs --verify
node scripts/ac3-serve.mjs
```

O servidor serve o build padrão e encaminha `/api`; não altera o build. Para o browser, use Playwright instalado fora do projeto (por exemplo, `npm install --prefix /tmp/ac3-browser-tools --no-save playwright` e `node /tmp/ac3-browser-tools/node_modules/playwright/cli.js install chromium ffmpeg`). Em outro terminal:

```bash
export AC3_PLAYWRIGHT_MODULE=file:///tmp/ac3-browser-tools/node_modules/playwright/index.mjs
export HISTORY_DEMO_DATABASE=release_demo
export AC3_EVIDENCE_DIR=/tmp/resolveai-release-evidence
node scripts/ac3-release-browser.mjs
```

Se utilizar Chromium do sistema, exporte `AC3_CHROMIUM_PATH` com seu caminho. Na execução registrada foi usado o Playwright já disponível em `/tmp/node_modules/playwright`, Chromium do sistema e FFmpeg auxiliar instalado em `/tmp`, sem acrescentar dependências ao projeto. Configure `PLAYWRIGHT_BROWSERS_PATH` consistentemente se escolher um cache externo.

O harness reutiliza `prepararDemonstracao`/`verificarDemonstracao` do script de filtros e os fluxos anteriormente utilizados de browser. Cria contas novas, 45 chamados, mais um chamado histórico com criação de dois dias antes e evento correspondente na mesma instrução SQL. Promoção de contas fictícias e desativação do autor são operações explícitas no PostgreSQL local. Não grava tokens/senhas. Não depende dos arquivos AC2 não rastreados.

Para planos: `psql -d release_demo -v ON_ERROR_STOP=1 -f scripts/ac3-query-plans.sql` e `psql -d release_demo -v ON_ERROR_STOP=1 -f scripts/ac3-filtros-query-plans.sql`. Ambos terminam em ROLLBACK. Ao finalizar, encerre a API/servidor e execute `pg_ctl -D "$AC3_WORK/pg" stop -m fast`.

## Evidências e URLs

As evidências locais desta rodada são entregues em pacote separado: logs Maven/Angular/PostgreSQL, OpenAPI JSON, schema SQL, checksums, triggers, planos, resultados dos roteiros API/browser, quatro PNG e seis WebM. Vídeos técnicos são silenciosos e segmentados; o vídeo acadêmico narrado/publicado está **PENDENTE**. Ver [narração e sequência de gravação](ac3-video.md).

Verificação HTTP real, sem autenticação:

| Item | URL ou situação | Resultado |
| --- | --- | --- |
| Commit atualmente público (não é a revisão AC3 validada) | [main público](https://github.com/RenanSouzadeOliveira/SOFTWARE_PRODUCT_IMPACTA_RESOLVEAI/commit/0711a5bd315319710ea4b8c314d669c98386c9f1) | 200 |
| README atualmente público | [README público anterior](https://github.com/RenanSouzadeOliveira/SOFTWARE_PRODUCT_IMPACTA_RESOLVEAI/blob/0711a5bd315319710ea4b8c314d669c98386c9f1/README.md) | 200 |
| Paginação | `https://github.com/RenanSouzadeOliveira/SOFTWARE_PRODUCT_IMPACTA_RESOLVEAI/commit/162ce375bb1b266cbd0de635fbd1558fcea667c7` | 404 |
| Filtros | `https://github.com/RenanSouzadeOliveira/SOFTWARE_PRODUCT_IMPACTA_RESOLVEAI/commit/ac82b74c61e42bd72152b72332d70d389937217a` | 404 |
| Histórico | `https://github.com/RenanSouzadeOliveira/SOFTWARE_PRODUCT_IMPACTA_RESOLVEAI/commit/facf63df6fbd866724cb4d21d494837905064ae9` | 404 |
| README no SHA | Mesmo repositório, `/blob/facf63df6fbd866724cb4d21d494837905064ae9/README.md` | 404 |
| MER no SHA | Mesmo repositório, `/blob/facf63df6fbd866724cb4d21d494837905064ae9/docs/modelo-entidade-relacionamento.md` | 404 |
| Índice de releases | [Página de releases](https://github.com/RenanSouzadeOliveira/SOFTWARE_PRODUCT_IMPACTA_RESOLVEAI/releases) | 200; não comprova release AC3 |
| Release AC3 específica / vídeo público | Nenhuma URL confirmada | PENDENTE |

404 não permite distinguir commit não publicado de restrição de acesso. Não há evidência pública confirmada desses commits. Documentos e diagramas anteriores AC1/AC2 existem; os links dos vídeos anteriores não foram localizados no inventário documental. Não se certifica completude dessas entregas anteriores.

## Release notes preparadas

ResolveAí AC3 consolida paginação e ordenação determinística no servidor, pesquisa textual com filtros combinados e estado persistido na URL. O histórico reúne criação, status, prioridade e atribuição, com autorização por participante/administrador, atomicidade e proteção de imutabilidade no PostgreSQL. Flyway V7–V9 preserva dados anteriores. Validação: 93 testes backend padrão, 93 no PostgreSQL, 105 Angular, builds de produção e demonstração Chromium com os três perfis.

## Checklist final de publicação

- [x] Confirmar os três commits e V7–V9 no SHA validado.
- [x] Executar builds/testes padrão em checkout limpo, integração PostgreSQL e browser.
- [x] Preparar documentação, roteiro pt-BR e evidências fictícias.
- [ ] Revisar diff e commitar somente README, documentos corrigidos/novos e os dois scripts de validação; preservar os quatro arquivos anteriores não rastreados.
- [ ] Escolher o SHA final incluindo documentação, conferir ausência de alterações funcionais e registrar esse SHA nas notas de release.
- [ ] Resolver, se necessário, a compatibilidade nos ambientes Node 22/PostgreSQL 17 exigidos pela avaliação; nesta rodada foram usados Node 24/PostgreSQL 18.
- [ ] Após autorização final explícita: push dos commits; verificar cada URL novamente sem autenticação.
- [ ] Após autorização: criar tag/release acadêmica no SHA escolhido, publicar notas e verificar a URL específica.
- [ ] Gravar/editar a narração, revisar os segmentos, publicar vídeo somente após autorização e verificar reprodução pública.
- [ ] Conferir evidências exigidas de AC1/AC2 e completar links ausentes.
- [ ] Após autorização: inserir links verificados no cartão Trello correto. Mover a Concluído somente com todos os critérios/evidências aceitos.

Plano mínimo de commits: um commit de documentação e ferramentas de validação, sem incluir código funcional, migrations, dados, `.env`, dependências ou os quatro arquivos anteriores. Os vídeos e logs ficam fora do repositório para revisão/publicação separada.
