# AC3 P0 — Paginação e ordenação de chamados

**Validação em 05/11/2026:** builds e testes padrão aprovados diretamente no repositório, sem cópias de fontes ou overrides. Veja [limpeza, backup e reprodução](limpeza-repositorio.md).

## Contrato HTTP

Listagens existentes, todas com paginação no banco:

| Endpoint | Escopo, inclusive no total |
| --- | --- |
| `GET /api/chamados/me` | Apenas chamados do SOLICITANTE autenticado |
| `GET /api/atendimento/fila` | ATENDENTE/ADMIN: ABERTO sem responsável |
| `GET /api/atendimento/me` | Apenas atribuídos ao ATENDENTE/ADMIN autenticado |

A carteira do ADMIN continua pessoal. Não foi criado um endpoint global de administração. Os filtros existentes permanecem: `prioridade` e `categoriaId` na fila/carteira, mais `status` na carteira. Os identificadores de escopo vêm da autenticação, nunca do cliente.

- `page`: inteiro a partir de **zero**, padrão `0`.
- `size`: inteiro entre **1 e 100**, padrão **20**. Valores acima do limite são rejeitados, sem truncamento silencioso.
- `sort`: exatamente um `campo,asc` ou `campo,desc`, direção em minúsculas.
- Campos permitidos: `criadoEm`, `atualizadoEm`, `titulo`, `status`, `prioridade`, `id`.
- Padrão: `criadoEm,desc`. Acrescenta-se `id DESC` para desempatar qualquer campo diferente de `id`.
- Ordenar diretamente por `id` respeita a direção solicitada. Status/prioridade usam a ordem textual dos enums; não representam uma classificação de urgência.
- Paginação vazia, negativa, fracionária, não numérica, repetida ou com overflow retorna `400`. O deslocamento `page * size` não pode ultrapassar `2147483647`.
- Sort vazio, campo/direção desconhecidos, múltiplos critérios ou parâmetros `sort` repetidos retornam `400` com mensagem pt-BR sanitizada.

Exemplo: `/api/atendimento/me?page=1&size=20&sort=titulo,asc&status=EM_ATENDIMENTO&prioridade=ALTA&categoriaId=1`.

Resposta, preservando os DTOs de cada conteúdo:

```json
{"content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0}
```

Página além do fim retorna conteúdo vazio e mantém o total correto. A UI redireciona para a última página válida (ou zero para conjunto vazio), preservando os demais parâmetros.

**Compatibilidade:** `/api/chamados/me` passa do array sem limite para o envelope acima; consumidores devem usar `response.content`. O serviço Angular e seus testes foram atualizados em conjunto. Fila/carteira mantêm o formato; o padrão muda de 10 para 20 e o máximo de 50 para 100. OpenAPI documenta os três parâmetros.

## Frontend

As três listagens oferecem total, anterior/próxima, tamanho 20/50/100 e ordenação. Tamanhos válidos recebidos por URL também são representados no seletor. Navegação preserva filtros/tamanho/sort via merge dos parâmetros; alteração de filtros, tamanho ou ordem volta à página zero. A URL restaura o estado ao recarregar ou voltar pelo navegador.

`switchMap` cancela respostas antigas; comparação dos parâmetros efetivos evita requisições repetidas. Navegação inválida e controles durante carregamento são bloqueados. Labels, botões nativos, foco visível e layout flexível atendem teclado e telas estreitas. Busca textual ainda não existe nas listagens AC1/AC2 e não foi acrescentada neste P0.

## PostgreSQL e Flyway

A V7 substitui dois índices, sem alterar migrations anteriores:

| Índice | Colunas | Motivo |
| --- | --- | --- |
| `idx_chamados_solicitante_criado_id` | solicitante_id, criado_em DESC, id DESC | Ordenação completa do solicitante; substitui o índice V1 sem ID |
| `idx_chamados_fila_ordem` | status, criado_em DESC, id DESC, atendente_id | Evita sort da fila quando o predicado do responsável é IS NULL; substitui o índice da fila V4 |
| `idx_chamados_carteira_criado_id` | atendente_id, criado_em DESC, id DESC | Reutilizado da V4 |

Continuam os índices existentes de status/prioridade, categoria/status e protocolo. Não se criaram índices para cada ordenação possível. Entity graphs usam somente associações singulares; não há join de comentários/históricos que multiplique registros. Os predicados de visibilidade/filtro também compõem os counts derivados pelo Spring Data.

Comparação local com PostgreSQL 18 e 50.000 chamados fictícios, datas repetidas: o solicitante passou de incremental sort sobre 1.000 linhas para leitura ordenada de 40 linhas (offset 20 + limite 20). A fila passou de sort sobre 33.334 linhas para leitura ordenada de 20. São evidências deste conjunto, sem promessa de latência em outro ambiente. `scripts/ac3-query-plans.sql` reproduz planos de solicitante, fila, carteira, filtros combinados e count em uma transação revertida ao final.

## Verificação e reprodução

Com Java 21, Maven e Node/npm nas versões do projeto:

```bash
mvn -f backend/pom.xml test
mvn -f backend/pom.xml package
cd frontend
npm test -- --watch=false
npm run build
```

Focados: `mvn -f backend/pom.xml -Dtest=PaginacaoIntegrationTest,PaginacaoMigrationTest,ChamadoIntegrationTest,AtendimentoIntegrationTest,InitialMigrationTest test`.

Para repetir os testes novos em PostgreSQL, crie um banco **descartável vazio**, forneça suas credenciais de teste e execute:

```bash
mvn -f backend/pom.xml -Dtest=PaginacaoIntegrationTest \
  -Dspring.datasource.url=jdbc:postgresql://localhost:5432/resolveai_ac3_test \
  -Dspring.datasource.driver-class-name=org.postgresql.Driver \
  -Dspring.datasource.username=usuario_de_teste \
  -Dspring.datasource.password=senha_de_teste test
```

O teste mantém os filtros de segurança reais, verifica os três perfis, 45 registros em 3 páginas, timestamps iguais, diferentes ordenações, limites, parâmetros repetidos, vazio, página fora do limite, count com vários comentários no mesmo chamado e SQL com limite no banco. A V7 é verificada em instalação limpa e atualização V6→V7, preservando registros.

## Roteiro reproduzível para evidência acadêmica

1. Inicie PostgreSQL/API/Angular conforme README em banco descartável, com uma categoria ativa e Flyway V7 aplicado.
2. Execute `node scripts/ac3-demonstracao.mjs` (`AC3_API_URL` opcional, padrão `http://localhost:8080/api`). Cria um solicitante fictício e 45 chamados, verifica páginas 20/20/5, ausência de duplicatas/omissões, retorno à página anterior, ordenação, limite 100 e sort inválido 400. Senha e token ficam apenas em memória e não são impressos.
3. Para gravação visual, cadastre outra conta fictícia pela UI e abra mais de 20 chamados, ou prepare registros fictícios no banco local. Mostre total, primeira/última página e controles desabilitados; navegue para trás.
4. Altere a ordem e o tamanho: mostre página zero, URL e requests de rede com `page`, `size`, `sort`. Recarregue e confirme restauração. Confira também em viewport de 360px e navegação por Tab.
5. Como atendente, combine prioridade/categoria na fila e status na carteira. Avance e confirme filtros na URL/API. Como outro atendente e ADMIN, mostre que cada carteira mantém seus próprios totais; solicitante recebe 403 nas listagens de atendimento.
6. Mostre `400` para `sort=senhaHash,asc`, `size=101`, `page=abc`, `sort=id&sort=asc`. Mostre uma conta vazia com ambos os botões bloqueados.
7. Execute `psql "$URL_DO_BANCO_DESCARTAVEL" -v ON_ERROR_STOP=1 -f scripts/ac3-query-plans.sql` e capture os planos. Use somente dados fictícios e oculte credenciais/tokens na gravação.

Não foi verificada estabilidade sob inserções, exclusões ou alterações concorrentes; paginação por offset pode deslocar registros nesses casos. Offsets muito altos e ordenações alternativas podem custar mais. Não há snapshot entre requests de páginas diferentes.

## Resultados verificados em 05/11/2026

- Backend: `mvn clean verify`, **77 testes**, sem falhas, erros ou skips; JAR gerado.
- Angular: `npm ci`, `npm run build` e `npm test -- --watch=false`; **90 testes** em 20 arquivos e build de produção aprovados, sem override de esbuild.
- PostgreSQL 18: **cinco testes REST** de paginação aprovados pelo Maven, com segurança ativa e descoberta normal de migrations. Instalação limpa V1→V7 e upgrade V6→V7 pelo JAR aprovados, preservando registros e checksums.
- Roteiro API: **45 chamados fictícios em páginas 20/20/5**, sem duplicatas/omissões no dataset estável; retorno à página anterior, tamanho 100, ordenação e rejeição de sort inválido aprovados.
- Verificação visual da implementação em Chromium: URL restaurada após reload; resets por tamanho/sort; fila filtrada 20/2 e carteira com status 20/3; viewport de 360px sem overflow horizontal e Tab entre controles. A etapa de limpeza não alterou componentes e não repetiu essa gravação.
- Planos PostgreSQL avaliados com 50.000 registros fictícios; `scripts/ac3-query-plans.sql` reproduz as consultas sem persistir esses dados.

O relatório de [reprodução dos builds](limpeza-repositorio.md) registra os comandos, a preservação dos arquivos duplicados fora do Git e os limites da verificação. Backups, instalações e credenciais não fazem parte da entrega.
