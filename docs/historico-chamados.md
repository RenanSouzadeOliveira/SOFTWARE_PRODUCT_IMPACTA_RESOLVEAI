# AC3 — Histórico completo de chamados

Implementado e verificado em 05/11/2026. Escopo: criação, status, prioridade e responsável; PostgreSQL/Flyway, API e timeline Angular.

## Auditoria inicial e reutilização

| Requisito | Estado inicial | Entrega |
| --- | --- | --- |
| Criação | Ausente na aplicação; schema V1 já admite CRIACAO | Evento na transação existente de abertura |
| Status | Implementado na AC2, tabela historico_status, transações e @Version | Escrita e contrato incorporado mantidos |
| Prioridade | Implementado na AC2, entidade HistoricoAtribuicao mapeia historico_chamados | Escrita existente mantida; leitura incluída na timeline |
| Responsável | Assumir escreve atribuição e transição de status atomicamente | Dois eventos na timeline, um por campo alterado |
| Valores inalterados | Status/prioridade/atribuição repetidos retornam 409 | Comportamento preservado; nenhum novo evento |
| Autorização do detalhe | Proprietário, atendente responsável ou ADMIN ativo | Mesma regra em GET /historico; 404 oculta recursos inacessíveis |
| Timeline | Somente histórico de status incorporado, crescente | Todos os tipos, decrescente, estados de loading/erro/vazio |
| Imutabilidade | Sem endpoints de edição/exclusão; entidades sem setters | @Immutable e triggers PostgreSQL contra UPDATE/DELETE/TRUNCATE |

As duas tabelas AC2 são as únicas fontes persistidas. Não foi criada tabela, fila ou arquitetura paralela de auditoria. `HistoricoAtribuicao` continua mapeando `historico_chamados`. A tabela física `historico_atribuicao` não existe.

`ConsultaHistorico` faz uma única consulta SQL com UNION ALL, joins para identificação e ordenação no banco. Uma cópia legada de STATUS em historico_chamados é omitida da resposta somente quando há uma transição correspondente em historico_status com mesmo chamado, autor, instante, estados e observação normalizada. As linhas originais permanecem intactas. ATRIBUICAO e STATUS são aspectos diferentes de assumir, portanto ambos aparecem.

Não se inferem eventos ausentes a partir do estado atual. Um chamado legado sem eventos retorna lista vazia. A versão não adiciona reatribuição nem novos comandos ao ciclo de vida; a atribuição suportada continua sendo assumir.

## Consistência, integridade e identificação

- A abertura usa a transação REQUIRES_NEW existente, incluindo o novo CRIACAO. Retentativa de protocolo permanece fora da transação que falhou.
- Status, prioridade e assumir mantêm @Transactional, saveAndFlush e @Version da AC2. Falhas de gravação de eventos revertem o chamado e todos os eventos da operação.
- Uma operação com vários campos auditados grava um evento por campo alterado. Assumir grava responsável + status com o mesmo Instant.
- A leitura usa REPEATABLE_READ para compartilhar um snapshot entre autorização e consulta; a consulta unificada nunca combina snapshots diferentes das duas fontes.
- Autor vem do principal autenticado validado contra usuário ativo. Autor e responsável são FKs distintas; o DTO não presume que sejam a mesma pessoa.
- FKs sem cascade de exclusão preservam chamado, autor e responsáveis referenciados. A desativação não remove identidade: ID e nome continuam disponíveis. O nome exibido é o nome atual da conta, conforme convenção AC2; não há duplicação de dados pessoais em snapshots.
- Datas usam Clock/Instant, TIMESTAMPTZ e UTC no backend. Angular formata na zona do navegador, com dd/MM/yyyy HH:mm:ss.
- Histórico não recebe payload de escrita por HTTP. Campos autorId/datas enviados em comandos não controlam a autoria ou o instante persistidos.

## Migration V9

Arquivo: `backend/src/main/java/db/migration/V9__complete_ticket_history.java`.

É uma migration Java Flyway para distinguir PostgreSQL do H2 usado como test double, sem alterar V1–V8:

1. Substitui somente o índice redundante `(chamado_id, criado_em)` do histórico geral por `idx_historico_chamado_data_id (chamado_id, criado_em DESC, id DESC)`.
2. Reutiliza o índice de status `(chamado_id, criado_em, id)`, que suporta scan reverso. Não duplica índice.
3. No PostgreSQL, instala função e triggers BEFORE UPDATE OR DELETE OR TRUNCATE, por statement, nas duas tabelas. A operação é rejeitada integralmente com SQLSTATE 23514.
4. Preserva todas as colunas, FKs e linhas. Não cria eventos fictícios de criação para legado, nem reescreve ou remove eventos.

O H2 executa a mudança de índice, mas **não** os triggers PL/pgSQL. A evidência de imutabilidade do banco depende do teste executado em PostgreSQL, não do H2.

`EXPLAIN (ANALYZE, BUFFERS)` foi executado na consulta real, inclusive com os cinco eventos da demonstração. Há uma consulta com joins, sem carregamento de autor por evento/N+1. O plano pode preferir scans sequenciais nos conjuntos pequenos; o índice começa pelo chamado e cobre o desempate usado na consulta. Esta entrega não reivindica benchmark de carga nem paginação do histórico (não solicitada).

## Contrato HTTP

`GET /api/chamados/{id}/historico`, autenticação bearer, resposta application/json:

```json
[
  {
    "id": "CHAMADO-17",
    "chamadoId": 8,
    "tipoEvento": "PRIORIDADE",
    "autor": { "id": 3, "nome": "Pessoa acadêmica atendente" },
    "criadoEm": "2026-11-05T18:00:00Z",
    "valorAnterior": { "codigo": "MEDIA", "nome": null },
    "valorNovo": { "codigo": "ALTA", "nome": null },
    "observacao": null
  }
]
```

- `tipoEvento`: CRIACAO, STATUS, PRIORIDADE, ATRIBUICAO.
- IDs estáveis com prefixo da fonte evitam colisão entre as tabelas.
- Ordem: criado_em DESC, origem DESC, id numérico DESC. A origem é STATUS ou CHAMADO; o cliente preserva a ordem recebida.
- `valorAnterior`/`valorNovo`: objeto com `codigo` e `nome`, ou null quando não registrado. Código é enum para status/prioridade/criação; ID textual para responsável. Nome só acompanha valores de responsável.
- CRIACAO registra null → ABERTO. A prioridade inicial pertence ao chamado criado; não é uma alteração de prioridade.
- Autor e responsáveis expõem somente ID e nome; nenhum email, senha, hash ou token.
- 200: eventos ou []; 400: ID inválido; 401: sem autenticação/conta inativa; 404: inexistente ou sem visibilidade.
- POST, PATCH e DELETE de /historico não existem e retornam 405 para usuário autenticado.
- OpenAPI gerado em /v3/api-docs documenta operação, tipos, enum, schema da lista e erros. O contrato `historicoStatus` incorporado ao detalhe continua crescente e inalterado para clientes AC2.

## Angular

`HistoricoChamadoComponent` é montado somente após o detalhe autorizado carregar. Usa `ChamadosService.listarHistorico`, a infraestrutura HTTP/interceptor existente e assinaturas encerradas no destroy.

Exibe tipo em pt-BR, autor, data/hora, anterior quando aplicável, novo e observação como texto seguro. Para atribuição, null significa “Sem responsável”. A lista é semântica, tem heading e elementos time com datetime. Ações de nova tentativa são botões com foco visível.

Carregamento, erro com nova tentativa e vazio legado são independentes do restante do detalhe. Sucesso de status/prioridade atualiza a chave de refresh existente; conflito 409 recarrega o detalhe e a timeline. Mudança de chamado cancela a busca anterior e limpa eventos antigos. Não há timeline montada ou consulta de histórico quando o detalhe é negado.

## Verificação executada

| Execução | Resultado final |
| --- | --- |
| Maven verify, perfil test/H2 | 93 testes, zero falhas/erros/skips; JAR gerado |
| Maven verify, datasource PostgreSQL 18 isolado | 93 testes, zero falhas/erros/skips; JAR gerado |
| Migration V8→V9 com linhas legadas em PostgreSQL | Valores completos antes/depois iguais; FKs e índice válidos |
| Proteção PostgreSQL | UPDATE, DELETE e TRUNCATE rejeitados nas duas tabelas; desativação permitida e exclusão de autor referenciado rejeitada |
| Angular npm test -- --watch=false | 23 arquivos, 105 testes, todos passaram |
| Angular npm run build | Build de produção passou |
| Chrome integrado: Angular + API + PostgreSQL | Criação, assumir, prioridade/status via UI com refresh, autor desativado, 401/404 e UI negada passaram |
| Chrome: loading/erro/retry e teclado | Resposta retardada, 503 simulado e nova tentativa via Enter passaram |
| Chrome: desktop/360 px | Capturas inspecionadas; sem overflow horizontal em 360 px; sem erros JavaScript |
| git diff --check | Passou |

Testes focados novos: `HistoricoChamadoIntegrationTest` (sete cenários), `HistoricoMigrationTest`, `historico-chamado.component.spec.ts`; serviços e detalhe atualizados. Os testes AC2 de concorrência foram ampliados para conferir a resposta unificada. Foi ajustado um parâmetro JDBC do teste de atribuição de Instant para Timestamp, para funcionar também no driver PostgreSQL.

A primeira execução PostgreSQL encontrou essa incompatibilidade no teste AC2; após o ajuste, a suíte inteira passou em banco novo. As primeiras verificações também ajustaram a contagem da nova migration e a declaração application/json do OpenAPI. Os resultados acima correspondem à versão final.

Concorrência/falhas verificadas: um vencedor em atribuição e resolução simultâneas, nenhuma duplicação na timeline, leitor não vê atribuição sem commit, publicação conjunta dos dois eventos ao commit, rollback de atribuição + prioridade na mesma transação, falhas de criação/status/prioridade/histórico sem atualização parcial.

Não foram feitas auditoria completa WCAG, medição de carga ou validação final de toda a release AC3. A evidência visual se limita aos fluxos e dimensões descritos.

## Matriz AC1–AC8

| Critério | Resultado | Evidência |
| --- | --- | --- |
| AC1 — Criação com evento inicial | Atendido | creationAndEveryChangedFieldAppearOnceWithSafeAuthenticatedActor; script acadêmico |
| AC2 — Um evento por campo realmente alterado | Atendido | Mesmo teste; assumir produz atribuição+status; lifecycle/prioridade AC2 |
| AC3 — Sem duplicatas em valores inalterados | Atendido | Repetição de status/prioridade/assumir retorna 409 sem novos eventos; testes e script |
| AC4 — Rollback sem parcialidade ou órfãos | Atendido | failedCreationRollsBackTicketAndItsInitialEvent; rollbackOfMultipleChangesPreservesOnlyPreviouslyCommittedHistory; falhas AC2; FKs |
| AC5 — Sem acesso indevido API/UI | Atendido | sameVisibilityAsDetailsAndDeactivatedActorsRemainIdentifiable; 401/404 e ausência da timeline no Chrome |
| AC6 — Autor, instante e valores corretos | Atendido | Eventos de todos os tipos, ordem/ties, distinção ator/responsável, testes Angular e Chrome |
| AC7 — Identidade após desativação | Atendido | Teste de autorização/desativação; script; Chrome e joins sem filtro ativo para autor |
| AC8 — Histórico AC2 preservado | Atendido | Upgrade V8→V9 compara todas as colunas de ambas as tabelas; legado deduplicado somente na resposta; contrato incorporado AC2 passa |

## Reproduzir os testes em infraestrutura isolada

Execute a partir da raiz do repositório, com Java 21, Maven, Node/npm do projeto e PostgreSQL disponíveis no PATH. Cada execução de `HistoricoMigrationTest` em PostgreSQL deve usar um banco de upgrade novo, pois insere um dataset legado intencionalmente fixo.

```bash
export HISTORY_WORK="$(mktemp -d /tmp/resolveai-history.XXXXXX)"
export PGHOST=127.0.0.1 PGPORT=54129 PGUSER=history_test
initdb -D "$HISTORY_WORK/pg" -U history_test --auth=trust --encoding=UTF8 --locale=C.UTF-8
pg_ctl -D "$HISTORY_WORK/pg" -l "$HISTORY_WORK/pg.log" -o "-p 54129 -h 127.0.0.1 -k $HISTORY_WORK" start
createdb history_runtime
createdb history_upgrade
createdb history_browser

HISTORY_MIGRATION_URL=jdbc:postgresql://127.0.0.1:54129/history_upgrade \
  mvn -f backend/pom.xml -B -ntp \
  -Dspring.datasource.url=jdbc:postgresql://127.0.0.1:54129/history_runtime \
  -Dspring.datasource.username=history_test -Dspring.datasource.password= \
  -Dspring.datasource.driver-class-name=org.postgresql.Driver verify
mvn -f backend/pom.xml -B -ntp verify
cd frontend
npm test -- --watch=false
npm run build
cd ..
```

Se a porta estiver ocupada por uma execução anterior, encerre aquele cluster ou escolha outra porta e ajuste todas as URLs. O banco de aplicação e os de testes são distintos. Não é necessário acessar bancos ou dados reais.

## Roteiro reproduzível do vídeo AC3

1. Após o build, inicie a API em outro terminal com o banco acadêmico `history_browser`:

```bash
DB_URL=jdbc:postgresql://127.0.0.1:54129/history_browser \
DB_USERNAME=history_test DB_PASSWORD= \
JWT_SECRET=segredo-ficticio-da-demonstracao-com-mais-de-32-bytes \
CORS_ALLOWED_ORIGINS=http://localhost:4200 \
java -jar backend/target/resolveai-api-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=dev --server.port=8080
```

2. Prepare contas fictícias e um chamado novo. O script promove exclusivamente o atendente recém-criado no banco isolado. Não grava nem exibe tokens; os emails de demonstração e caminhos dos detalhes são impressos.

```bash
export PGPORT=54129 PGUSER=history_test
export HISTORY_API_URL=http://127.0.0.1:8080/api
export HISTORY_DEMO_DATABASE=history_browser
export HISTORY_DEMO_PASSWORD='Senha-academica-historico-2026!'
node scripts/ac3-historico-demonstracao.mjs
```

3. Rode `npm start` em frontend e abra http://localhost:4200. O environment.development.ts já usa localhost:8080/api. Alternativamente, sirva o build de produção com /api encaminhado à API. A verificação Chrome desta entrega usou esse segundo arranjo, nas portas 4209/8089; o roteiro acima usa as portas padrão do desenvolvimento Angular/API.
4. Entre como solicitante usando o email impresso e HISTORY_DEMO_PASSWORD. Abra o detalhe: um evento “Chamado criado”, autor e data/hora, novo “Aberto”.
5. Em sessão separada, entre como atendente. Na fila, assuma o protocolo impresso. Abra seu detalhe: “Responsável alterado” (Sem responsável → atendente) e “Status alterado” (Aberto → Em atendimento), mesmo instante.
6. Altere a prioridade para Alta: novo evento aparece no topo sem recarregar a página. Mostre que a confirmação desabilita prioridade igual. O teste/script demonstra também 409 da API sem duplicata.
7. Resolva com uma justificativa acadêmica de pelo menos dez caracteres: status anterior/novo, autor, instante e observação aparecem no topo.
8. Volte ao solicitante e recarregue o detalhe. Mostre a ordem mais recente primeiro e, em 360 px, as informações sem overflow. Se desejar, demonstre reabrir/resolver/fechar conforme as ações autorizadas AC2.
9. Como outro solicitante impresso pelo script, tente o mesmo ID: detalhe negado e timeline ausente. Mostre o GET /historico retornando 404 pelo cliente HTTP autenticado, sem expor o token na gravação.
10. Para uma verificação automática adicional de criação, assumir, prioridade, no-op, resolução, desativação e acesso indevido, execute `node scripts/ac3-historico-demonstracao.mjs --verify`. Ele cria outro conjunto de dados fictícios; o atendente desse conjunto termina desativado e seus eventos continuam identificados. Execução validada nesta entrega: PASS, cinco eventos.
11. Mostre o resultado dos testes de rollback, concorrência e migration. Esses casos possuem testes determinísticos; não simule erro em um banco compartilhado durante a gravação.
12. Ao encerrar a infraestrutura isolada: `pg_ctl -D "$HISTORY_WORK/pg" stop -m fast`. Push, deploy e atualização de Trello dependem de autorização específica.

## Arquivos desta entrega

- `README.md`
- `backend/src/main/java/br/edu/impacta/resolveai/atendimento/HistoricoAtribuicao.java`
- `backend/src/main/java/br/edu/impacta/resolveai/chamado/ChamadoController.java`
- `backend/src/main/java/br/edu/impacta/resolveai/chamado/ChamadoCreationTransaction.java`
- `backend/src/main/java/br/edu/impacta/resolveai/chamado/CicloChamadoService.java`
- `backend/src/main/java/br/edu/impacta/resolveai/chamado/ConsultaHistorico.java`
- `backend/src/main/java/br/edu/impacta/resolveai/chamado/HistoricoStatus.java`
- `backend/src/main/java/br/edu/impacta/resolveai/chamado/TipoEventoHistorico.java`
- `backend/src/main/java/br/edu/impacta/resolveai/chamado/dto/EventoHistoricoResponse.java`
- `backend/src/main/java/db/migration/V9__complete_ticket_history.java`
- `backend/src/test/java/br/edu/impacta/resolveai/atendimento/AtendimentoIntegrationTest.java`
- `backend/src/test/java/br/edu/impacta/resolveai/chamado/ChamadoServiceTest.java`
- `backend/src/test/java/br/edu/impacta/resolveai/chamado/CicloChamadoIntegrationTest.java`
- `backend/src/test/java/br/edu/impacta/resolveai/chamado/HistoricoChamadoIntegrationTest.java`
- `backend/src/test/java/br/edu/impacta/resolveai/migration/FiltrosMigrationTest.java`
- `backend/src/test/java/br/edu/impacta/resolveai/migration/HistoricoMigrationTest.java`
- `backend/src/test/java/br/edu/impacta/resolveai/migration/InitialMigrationTest.java`
- `docs/historico-chamados.md`
- `docs/modelo-entidade-relacionamento.md`
- `frontend/src/app/features/chamados/chamados.service.spec.ts`
- `frontend/src/app/features/chamados/chamados.service.ts`
- `frontend/src/app/features/chamados/detalhe-chamado.component.html`
- `frontend/src/app/features/chamados/detalhe-chamado.component.spec.ts`
- `frontend/src/app/features/chamados/detalhe-chamado.component.ts`
- `frontend/src/app/features/chamados/historico-chamado.component.html`
- `frontend/src/app/features/chamados/historico-chamado.component.scss`
- `frontend/src/app/features/chamados/historico-chamado.component.spec.ts`
- `frontend/src/app/features/chamados/historico-chamado.component.ts`
- `frontend/src/app/features/chamados/historico-chamado.model.ts`
- `scripts/ac3-historico-demonstracao.mjs`

Os quatro arquivos não rastreados que já existiam (plano acadêmico e scripts AC2) foram preservados.
