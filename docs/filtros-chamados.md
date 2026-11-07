# ResolveAí — AC3 P0: busca e filtros combinados

Implementação verificada em 05/11/2026. Paginação/ordenação AC3 reutilizada, sem mudanças no componente ou parser compartilhados de paginação. AC1/AC2 preservadas. Nenhum commit, push, deploy ou atualização do Trello realizado.

## Contrato HTTP e visibilidade

`GET /api/chamados` retorna o mesmo envelope: `content`, `page`, `size`, `totalElements`, `totalPages`. Padrões: página 0, tamanho 20, máximo 100, `sort=criadoEm,desc` e desempate por ID DESC. Mantém a allowlist de ordenação AC3.

| Parâmetro | Contrato |
| --- | --- |
| `search` | Substring literal, sem distinguir maiúsculas/minúsculas; protocolo OU título OU descrição. Espaços externos ignorados; vazio equivale a ausência. `%`, `_` e `!` escapados com `ESCAPE '!'`; valores vinculados pelo Hibernate. |
| `status` | `ABERTO`, `EM_ATENDIMENTO`, `RESOLVIDO`, `FECHADO`. |
| `prioridade` | `BAIXA`, `MEDIA`, `ALTA`. |
| `categoriaId` | ID inteiro positivo. Preserva nome já estabelecido; aceita alias `categoria`. Ambos podem coexistir somente com o mesmo valor. |
| `responsavel` | ID inteiro positivo, aplicado à FK existente `atendente_id`. |
| `dataInicial` | Criação >= início do dia em UTC. Data válida `AAAA-MM-DD`, anos 0001..9999. |
| `dataFinal` | Criação < início do dia seguinte em UTC, incluindo todo o dia final sem depender da precisão do timestamp. Mesmo formato/limites. |

Todos os filtros ativos e a visibilidade são combinados com AND. Parâmetros repetidos, enum inválido, IDs não positivos/malformados/overflow, datas inválidas e intervalo invertido retornam HTTP 400 sanitizado em pt-BR. IDs positivos inexistentes não encontram registros. Não há filtragem de tickets em memória.

| Endpoint | Restrição obrigatória, antes de paginação/count |
| --- | --- |
| `/api/chamados` | SOLICITANTE: `solicitante_id` próprio; ATENDENTE: `atendente_id` próprio; ADMIN: visão global. |
| `/api/chamados/me` | Apenas SOLICITANTE, próprios chamados. |
| `/api/atendimento/fila` | ATENDENTE/ADMIN, somente ABERTO sem responsável. Filtros incompatíveis resultam em vazio. |
| `/api/atendimento/me` | ATENDENTE/ADMIN, somente atribuídos ao usuário, inclusive quando ADMIN. |

Os endpoints anteriores preservam seus DTOs, permissões e paginação. Novos filtros também estão disponíveis neles. Conteúdo e contagem usam a mesma Specification, sem joins de coleções que multipliquem chamados. EntityGraph de associações singulares evita N+1 na serialização.

`GET /api/chamados/filtros` retorna `{categorias:[{id,nome}], responsaveis:[{id,nome}]}` derivados exclusivamente dos chamados autorizados. Preserva referências inativas; conta sem chamados recebe opções vazias. Não expõe e-mails, perfis, hashes ou contas sem vínculo visível. Não existia uma API geral de usuários para reutilizar. A fila continua reutilizando `/api/atendimento/categorias`, inclusive categorias inativas.

## Angular e URL

Painel de filtros compartilhado por `/chamados`, `/atendimento`, `/atendimento/me` e a nova rota ADMIN `/consulta-chamados`. A fila não oferece responsável porque contém apenas chamados sem responsável. Solicitante e carteira veem somente opções derivadas de seu escopo autorizado.

A URL é a fonte da consulta. Refresh, voltar/avançar, filtros, página, tamanho e ordem são restaurados. Filtros reiniciam página 0; página e ordenação preservam filtros usando merge. Busca usa debounce de 350 ms; selects/datas aplicam no evento de mudança e há envio por Enter/botão. Consultas equivalentes são deduplicadas. `switchMap` cancela a requisição anterior e impede resposta antiga de sobrescrever o resultado novo.

Limpar filtros remove todos os filtros e alias, define página 0, tamanho 20 e ordem `criadoEm,desc`. Preserva estados de carregamento, sucesso, erro recuperável e vazio; navegação bloqueada para resultado vazio. Intervalo invertido recebe aviso acessível antes de nova consulta. URLs com filtros inválidos recebem o erro de validação do backend. Labels em pt-BR, foco visível, controles com labels e largura responsiva conferida a 360 px.

## PostgreSQL e migrations

Reutilizados: unicidade de protocolo; índices de status/prioridade/criação, categoria/status, atendente/status; V4 carteira `(atendente_id, criado_em DESC, id DESC)`; V7 solicitante `(solicitante_id, criado_em DESC, id DESC)` e fila `(status, criado_em DESC, id DESC, atendente_id)`.

Nova V8: `idx_chamados_criado_id (criado_em DESC, id DESC)`, necessária à nova consulta global sem igualdade nas colunas iniciais dos índices existentes. Nenhuma migration anterior foi alterada, nenhuma extensão adicionada.

EXPLAIN ANALYZE/BUFFERS com 50.000 chamados fictícios, mais os registros de teste: consulta global sem V8 leu aproximadamente 50 mil linhas e ordenou (top-N); com V8 leu 20 entradas via índice. Na repetição registrada, aproximadamente 12,47 ms vs. 0,11 ms. São medições locais de uma projeção de ID/data, não garantia de latência do endpoint. Filtro combinado e count reutilizaram o índice do solicitante, com os mesmos predicados. `scripts/ac3-filtros-query-plans.sql` reproduz os planos antes/depois em transação revertida, inclusive restauração do índice V8. Execute apenas em banco descartável.

Instalação V1→V8 e validação Hibernate/Flyway verificadas; upgrade V7→V8 em PostgreSQL preservou 159/159 IDs e protocolos. Teste dedicado de upgrade também preserva registros e índices anteriores em H2.

## Verificações executadas

- Backend: testes focados primeiro; `mvn clean verify`: 85 testes, zero falhas, erros ou skips; JAR gerado. Inclui AC1, AC2, paginação AC3, segurança, persistência e migrations.
- Filtros e paginação em PostgreSQL 18: 12 testes REST com segurança ativa; instalação limpa e upgrade. Repetição em banco UTF-8/C.UTF-8 inclui busca com acentos em maiúsculas, além de protocolo, título, descrição, literal `%`/`_`/`!`, trim, busca vazia/inexistente, AND, limites inclusivos, entradas inválidas e isolamento de totais/opções para os três perfis.
- Angular: entrypoint local do Angular CLI executado com Node (`ng test --watch=false` e `ng build`, equivalentes aos scripts npm): 100 testes em 22 arquivos e build de produção aprovados. Inclui HTTP, restauração de URL, mudanças de filtros, debounce, limpeza e repetição do mesmo termo após limpar, período inválido, recuperação de opções, paginação e cancelamento de requisição antiga.
- Demonstração API: 45 chamados fictícios, 25 filtrados em páginas 20/5; restauração, vazio e retorno à consulta inicial verificados.
- Chromium, build de produção + JAR + PostgreSQL: filtros status/prioridade/período/busca, páginas 20/5, refresh, voltar/avançar, responsável/categoria e ordenação preservando filtros; limpeza e vazio com botões bloqueados; 360 px sem overflow horizontal, Tab e ausência de erros JavaScript. Capturas desktop/mobile inspecionadas. Carteira/fila do ATENDENTE e consulta global do ADMIN também verificadas no Chromium: filtros, páginas, refresh, limpeza e 360 px.

Comandos reproduzíveis com ferramentas nas versões do projeto:

```bash
mvn -f backend/pom.xml -Dtest=FiltrosChamadosIntegrationTest,PaginacaoIntegrationTest,FiltrosMigrationTest test
mvn -f backend/pom.xml clean verify
cd frontend
npm test -- --watch=false
npm run build
```

Para PostgreSQL, use banco **descartável UTF-8** e substitua apenas os valores de teste:

```bash
mvn -f backend/pom.xml -Dtest=FiltrosChamadosIntegrationTest,PaginacaoIntegrationTest \
  -Dspring.datasource.url=jdbc:postgresql://localhost:5432/resolveai_filtros_test \
  -Dspring.datasource.driver-class-name=org.postgresql.Driver \
  -Dspring.datasource.username=usuario_de_teste \
  -Dspring.datasource.password=senha_de_teste test
node scripts/ac3-filtros-demonstracao.mjs
psql "$URL_DO_BANCO_DESCARTAVEL" -v ON_ERROR_STOP=1 -f scripts/ac3-filtros-query-plans.sql
```

O script Node usa `AC3_API_URL` opcional (padrão `http://localhost:8080/api`), requer uma categoria ativa e cria conta/registros fictícios novos. Não imprime senha ou token. Não apaga registros. Mantém credenciais somente em memória durante a execução. Para reutilizar a conta na gravação, forneça `AC3_DEMO_EMAIL` e `AC3_DEMO_PASSWORD` de uma conta fictícia nova; depois entre na UI com esses mesmos dados. Cada execução cadastra uma conta, portanto use um e-mail novo.

## Matriz de aceite

| Critério | Resultado | Evidência |
| --- | --- | --- |
| AC1 — individual/combinado | PASS | Testes REST de cada filtro e todos os filtros com AND; controles/API no navegador. |
| AC2 — busca parcial sem diferenciar caixa | PASS | Protocolo, título, descrição, espaços, texto acentuado e maiúsculas em H2/PostgreSQL UTF-8. |
| AC3 — inexistente retorna vazio | PASS | REST e Chromium; total zero sem erro. |
| AC4 — limpar restaura consulta/paginação | PASS | Teste Angular e Chromium: 45 iniciais, filtros removidos, padrões 0/20/criadoEm DESC. |
| AC5 — refresh restaura estado | PASS | URL completa, refresh e histórico no Chromium; testes Angular. |
| AC6 — conteúdo/totais autorizados | PASS | Counts forçados por tamanho pequeno, três perfis, AND, páginas 20/5. |
| AC7 — validação HTTP 400 | PASS | Enums, IDs, formatos/datas, reversão, overflow e duplicados nas quatro listagens. |
| AC8 — visibilidade por perfil | PASS | Solicitante próprio, carteira própria, fila restrita, ADMIN global; opções sem contas externas. |
| AC9 — paginação/ordenação estáveis | PASS | Dataset estável sem omissões/duplicatas, ID de desempate, regressões AC3 e navegação filtrada. |
| AC10 — execução no servidor | PASS | Specifications paginadas no repositório, SQL com limite verificado pela suíte AC3 e planos PostgreSQL. |

## Roteiro do vídeo acadêmico

1. Inicie PostgreSQL descartável, API e Angular conforme README, com V8 aplicada e categorias fictícias. Cadastre uma conta fictícia. Prepare mais de 20 chamados: por exemplo, 25 de prioridade Alta com título contendo “Portal” e 20 de prioridade Baixa com outro texto, em duas categorias. O script demonstração automatiza esse conjunto e verifica a API. Execute com `AC3_DEMO_EMAIL` e `AC3_DEMO_PASSWORD` fictícios conhecidos para depois entrar com essa conta pela UI; use e-mail novo em cada execução.
2. Abra a listagem e mostre o total inicial de 45 e os controles da primeira página. Se desejar demonstrar Em atendimento, use um atendente fictício para assumir os 25 chamados Alta, pela fila existente.
3. Selecione status (Aberto, ou Em atendimento após atribuição), prioridade Alta e datas de criação inicial/final do conjunto, em UTC. Digite “pOrTaL”. Mostre URL, total 25 e primeira página com 20 registros.
4. Avance: segunda página com 5 registros. Recarregue e confirme busca/status/prioridade/datas, página e total. Use voltar/avançar e confira restauração.
5. Combine responsável (se atribuídos) e categoria. Mostre a redução do conjunto e a página reiniciada em 0. Mude a ordenação e confirme os filtros na URL/rede.
6. Limpe filtros: 45 registros, primeira página, tamanho 20 e ordem padrão. Digite termo inexistente e mostre vazio com Anterior/Próxima desabilitados.
7. Mostre a carteira do atendente e o ADMIN em `/consulta-chamados`, destacando os escopos. Mostre 400 para intervalo invertido pela API e para `status=INVALIDO` sem expor tokens. Confira mobile 360 px e Tab pelos controles.
8. Apresente os resultados dos testes e o plano antes/depois da V8. Oculte credenciais e utilize somente registros fictícios. A gravação/publicação do vídeo permanece a cargo do usuário.

## Limites

Busca substring com `%texto%` pode examinar muitas linhas do escopo autorizado; B-tree de protocolo não resolve esse padrão. Não houve necessidade medida de extensão pg_trgm neste escopo. Counts continuam proporcionais ao conjunto filtrado. Grandes volumes, offsets altos e ordenações alternativas podem exigir novas medições.

Paginação por offset não oferece snapshot entre requisições: alterações concorrentes podem deslocar registros. A garantia de ausência de lacunas/duplicatas foi verificada em dataset estável. Busca não remove acentos: “descricao” e “descrição” são textos distintos; caixa segue `lower`/collation do banco. Use banco UTF-8 com locale apropriado para português.

Sem blockers funcionais identificados na validação executada. Acessibilidade foi conferida por labels/foco/teclado e responsividade, sem auditoria WCAG completa ou teste com leitor de tela. Nenhuma dependência do projeto foi adicionada.

## Arquivos envolvidos

- `README.md`
- `backend/src/main/java/br/edu/impacta/resolveai/atendimento/AtendimentoController.java`
- `backend/src/main/java/br/edu/impacta/resolveai/atendimento/AtendimentoService.java`
- `backend/src/main/java/br/edu/impacta/resolveai/chamado/ChamadoController.java`
- `backend/src/main/java/br/edu/impacta/resolveai/chamado/ChamadoRepository.java`
- `backend/src/main/java/br/edu/impacta/resolveai/chamado/ChamadoService.java`
- `backend/src/main/java/br/edu/impacta/resolveai/config/SecurityConfig.java`
- `backend/src/test/java/br/edu/impacta/resolveai/chamado/ChamadoServiceTest.java`
- `backend/src/test/java/br/edu/impacta/resolveai/migration/InitialMigrationTest.java`
- `backend/src/test/java/br/edu/impacta/resolveai/migration/PaginacaoMigrationTest.java`
- `docs/modelo-entidade-relacionamento.md`
- `frontend/src/app/app.html`
- `frontend/src/app/app.routes.ts`
- `frontend/src/app/features/atendimento/atendimento.component.html`
- `frontend/src/app/features/atendimento/atendimento.component.spec.ts`
- `frontend/src/app/features/atendimento/atendimento.component.ts`
- `frontend/src/app/features/atendimento/atendimento.model.ts`
- `frontend/src/app/features/atendimento/atendimento.service.spec.ts`
- `frontend/src/app/features/atendimento/atendimento.service.ts`
- `frontend/src/app/features/chamados/chamados.service.ts`
- `frontend/src/app/features/chamados/meus-chamados.component.html`
- `frontend/src/app/features/chamados/meus-chamados.component.spec.ts`
- `frontend/src/app/features/chamados/meus-chamados.component.ts`
- `backend/src/main/java/br/edu/impacta/resolveai/chamado/FiltrosChamados.java`
- `backend/src/main/java/br/edu/impacta/resolveai/chamado/OpcoesFiltrosChamados.java`
- `backend/src/main/java/br/edu/impacta/resolveai/chamado/VisibilidadeChamados.java`
- `backend/src/main/resources/db/migration/V8__index_global_ticket_order.sql`
- `backend/src/test/java/br/edu/impacta/resolveai/chamado/FiltrosChamadosIntegrationTest.java`
- `backend/src/test/java/br/edu/impacta/resolveai/migration/FiltrosMigrationTest.java`
- `docs/filtros-chamados.md`
- `frontend/src/app/features/chamados/filtros-chamados.component.html`
- `frontend/src/app/features/chamados/filtros-chamados.component.scss`
- `frontend/src/app/features/chamados/filtros-chamados.component.spec.ts`
- `frontend/src/app/features/chamados/filtros-chamados.component.ts`
- `frontend/src/app/features/chamados/filtros-chamados.model.ts`
- `frontend/src/app/features/chamados/filtros-chamados.service.spec.ts`
- `frontend/src/app/features/chamados/filtros-chamados.service.ts`
- `scripts/ac3-filtros-demonstracao.mjs`
- `scripts/ac3-filtros-query-plans.sql`

## Exportação da consulta

O botão **Exportar CSV** em Meus chamados e Consultar chamados usa os filtros e a ordenação atuais sem limitar o arquivo à página visível. Veja [contrato, segurança, testes e demonstração da exportação](exportacao-csv.md).
