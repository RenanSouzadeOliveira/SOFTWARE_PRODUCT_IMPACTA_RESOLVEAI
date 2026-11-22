# Validação da entrega final

Execução de 21 e 22 de novembro de 2026, horário de São Paulo. A base foi `de677e9904fab087e2a0d7949c7e3716af7f8719`, com árvore limpa em `main` e o mesmo SHA no GitHub. Os resultados finais abaixo incluem a atualização do lockfile e duas verificações de contrato OpenAPI adicionadas nesta entrega. Para identificar a revisão do pacote local, execute `git rev-parse HEAD` na cópia recebida. [Resultados por classe e verificação de navegador](evidencias/validacao-final.json).

## Reproduzir a avaliação

A sequência principal está no [README](../README.md). Em checkout independente, os builds partiram sem `node_modules`, `target`, banco anterior ou arquivo de ambiente. Na primeira rodada, Maven e npm também usaram caches de pacotes vazios. O JAR iniciou por `java -jar`. O navegador acessou o build estático com proxy local para a API. O Docker criou o usuário e o banco com os parâmetros da execução. A instalação de produção partiu de outro banco vazio. O teste de Compose usou um projeto, uma porta e um volume próprios, removidos após a consulta SQL.

| Camada | Execução e resultado |
| --- | --- |
| Frontend | Node 22.22.1, npm 10.9.2, Angular 21.2.26, CLI 21.2.30 e TypeScript 5.9.3. `npm ci`, suíte **185/185 em 28 arquivos**, build de produção aprovado. Bundle inicial de 297,61 kB, estimativa de transferência de 83,85 kB. Na base, 50 testes dirigidos de componentes e serviços precederam a suíte completa. |
| Navegador | Chrome verificou os três perfis, login, cadastro, listas, abertura, detalhes, fila, filtros, dashboard, administração e acesso negado. A varredura de 105 combinações na base cobriu 320, 360, 390, 768 e 1440 px, sem overflow ou erro de console. O build com lockfile final passou nas 27 verificações dirigidas, incluindo foco, menu, validação, carregamento, falhas e prevenção de envio duplicado. A regressão do ciclo no navegador confirmou comentário persistente, resolução, reabertura e fechamento terminal. |
| Backend padrão | Java 21.0.13.0.3, Spring Boot 3.5.16, Maven 3.9.12. `mvn -B -ntp -f backend/pom.xml clean verify` reportou 159 testes, dos quais 151 executados e oito condicionais ignorados, zero falhas e erros. JAR executável gerado em `backend/target/`. |
| Backend com PostgreSQL | `bash scripts/testar-postgres17.sh` concluiu **178/178**, zero falhas, erros ou skips. Vinte e oito casos usaram PostgreSQL real, os demais 150 são unidades ou integrações com H2/MockMvc. Uma classe parametrizada expande as invocações quando habilitada. A suíte cobre JWT e revogação, cadastro, autorização por objeto, concorrência, ciclo, histórico, filtros, paginação, CSV, dashboard, usuários e categorias. |
| PostgreSQL e Flyway | PostgreSQL 17.11, UTF-8 e `pg_catalog.pg_c_utf8`. Perfil `prod` aplicou V1–V12 e deixou as categorias vazias. Perfil `dev` aplicou também o seed repetível de três categorias. Hibernate validou os mapeamentos. O catálogo apresentou seis tabelas de domínio, a tabela Flyway, 22 índices no total, 42 constraints e dois triggers de imutabilidade. Verificados FKs históricas, índice único normalizado e persistência de `versao_seguranca`. |
| Upgrades | Banco PostgreSQL separado para o histórico V8→V9 e para normalização de categorias V9→V10→V12. Dados legados, rollback de conflito e equivalência Unicode foram exercitados. A instalação limpa e os upgrades usaram somente migrations do projeto. |
| Swagger | JSON OpenAPI 3.1.0 HTTP 200, UI renderizada no Chrome com 32 operações em 27 caminhos. Três operações funcionais públicas e 29 com JWT Bearer. O contrato corrigido documenta filtros, respostas de administração e os erros verificados. [Guia da API](api-ac2.md). |
| Massa e fluxo integrado | Inicialização em loopback com banco em memória. Primeira carga inseriu 75 chamados, segunda inseriu zero. Login das seis contas, histórico e comentário inicial conferidos. O ADMIN vê 15 abertos, 30 em atendimento, 15 resolvidos e 15 fechados. Prioridades baixa/média/alta somam 15/30/30. Cada uma das três categorias tem 25 chamados. O fluxo da verificação acrescentou um chamado, assumiu, priorizou, comentou, resolveu e fechou. O total passou a 76, com 31 concluídos. |

O comando `testar-postgres17.sh` configura quatro bancos exclusivos por meio de `AUTORIZACAO_POSTGRES17_URL`, `DASHBOARD_POSTGRES17_URL`, `P1_POSTGRES17_MIGRATION_URL` e `HISTORY_MIGRATION_URL`. O teste de autorização faz 24 invocações, o dashboard uma, os upgrades de categorias duas e o upgrade de histórico uma. A API recusou iniciar sem perfil ou com `dev,prod` simultâneos. A chave de sessão e o ADMIN do laboratório são gerados localmente, sem cadastro administrativo manual em SQL.

## Apresentação verificada

A gravação usou o build do checkout independente e a massa recém-criada. O navegador autenticou os três perfis pelos formulários, abriu o chamado, filtrou pelo protocolo, assumiu, comentou, resolveu, fechou e conferiu o painel. O ADMIN criou e desativou uma categoria. A sessão móvel de SOLICITANTE encontrou a tela de acesso negado na administração. O arquivo final foi reproduzido do início ao fim no Chrome, com áudio presente, sem erro do decodificador. Foram conferidos quadros das 12 cenas. WebM VP8/Opus, 1280 × 800, 153,527 segundos. SHA-256: `fe32907975046dd1a5d05123066bf9cf04473ef19401e1ca505cd2d64951d31c`. [Vídeo com narração sintética em pt-BR](evidencias/apresentacao-final.webm).

## Diferenças corrigidas durante a avaliação

- O seed histórico da AC3 verifica explicitamente a V9 e o seu contêiner de gravação. A entrega final reutiliza o gerador Java em outro laboratório, com PostgreSQL 17 em memória, banco novo e configuração isolada. A nova verificação cobre comentário, fechamento e totais de dashboard.
- A especificação gerada omitia respostas de sucesso na administração de usuários, erros na administração de categorias e parâmetros de filtros em algumas consultas. O teste de contrato verifica o JSON que o avaliador recebe. O roteiro AC3 de navegador passou a esperar a mensagem de erro sanitizada exibida ao receber HTTP 503.
- Uma verificação de comentário encontrou HTTP 400 em um processo que usava o caminho do JAR durante novo empacotamento. O mesmo payload no JAR final retornou 201 e o ciclo completo passou. As verificações finais usaram o JAR já empacotado antes da inicialização do laboratório.
- A atualização de dependências manteve as faixas originais de `package.json`. A instalação final com npm 10.9.2 passou. O lockfile foi gerado com npm 11.6.3 após falha interna do resolvedor npm 10 no comando de atualização.

## Segurança, privacidade e limitações

A inspeção dirigida da base incluiu 765 blobs textuais alcançáveis pelas referências locais, em 43 commits. Nenhum padrão pesquisado de chave privada, token de provedor ou JWT completo foi identificado. Os achados de configuração foram classificados como exemplos, comandos ou testes. Um caminho pessoal em um script histórico foi substituído por resolução de módulo configurável. Os arquivos locais de ambiente e credenciais estão ignorados. A varredura do build estático em 28 arquivos não encontrou atribuições de segredos do backend, chaves privadas, tokens nem caminhos pessoais. A revisão visual das 31 capturas preexistentes e a inspeção dos geradores apontaram para dados sintéticos.

O histórico Git já público contém um e-mail pessoal nos metadados de autoria. O commit da entrega usa o endereço público noreply da conta. A decisão sobre redistribuição do histórico é parte da autorização de publicação. A inspeção foi dirigida por padrões, sem scanner dedicado instalado. O audit inicial do npm registrou 34 alertas. Depois da atualização, `npm audit --omit=dev` registrou **zero** e o audit completo registrou **16 moderados**, todos na cadeia de ferramentas de desenvolvimento. A correção automática forçada sugerida pelo npm mudaria o major do Angular CLI.

O banco requer PostgreSQL 17 com UTF-8 para a normalização validada. CORS precisa listar origens explícitas e a aplicação exige exatamente um perfil. Tokens antigos sem o campo de versão de segurança exigem novo login após o upgrade. A avaliação de acessibilidade incluiu teclado, foco e larguras móveis. Leitor de tela real e carga sustentada estão fora das verificações executadas. A consulta de histórico não é paginada e a paginação por offset pode refletir mudanças entre requisições. Anexos e notificações em tempo real estão fora do escopo acadêmico.

## Critérios de aceite

| Critério | Resultado | Evidência |
| --- | --- | --- |
| AC-01 Frontend | APROVADO | 185 testes, build e regressão no Chrome |
| AC-02 Backend | APROVADO | 178 testes com os bancos condicionais habilitados, JAR iniciado |
| AC-03 Banco vazio | APROVADO | V1–V12 em dev e prod, UTF-8, catálogo e upgrades |
| AC-04 Reprodutibilidade | APROVADO | Instalação independente pelo README, seed, login e ciclo completo |
| AC-05 Documentação | APROVADO | README, arquitetura, modelo relacional, contrato OpenAPI e conferência dos links relativos |
| AC-06 Dados de demo | APROVADO | Senhas aleatórias, seed repetido, 75 chamados, comentário e agregados conferidos |
| AC-07 Abrangência da demo | APROVADO | [Cenas, papéis e narração](demonstracao-final.md), ciclo e administração exercitados |
| AC-08 Segurança da publicação | PENDENTE DE AUTORIZAÇÃO | Conteúdo candidato revisado. O responsável deve confirmar a redistribuição da autoria pessoal já presente no histórico público |
| AC-09 GitHub/Trello | PENDENTE DE AUTORIZAÇÃO | 21 cartões e 29 commits públicos conferidos, vínculo da nova release e do vídeo pendente |
| AC-10 Apresentação | APROVADO | Vídeo narrado reproduzido até o fim e roteiro completo, destino externo pendente |
| AC-11 Limitações | APROVADO | Limitações técnicas e de avaliação acima |
| AC-12 Integridade da release | PENDENTE DE AUTORIZAÇÃO | Revisão local e reprodução identificáveis. Tag, release e URLs finais dependem da publicação autorizada |

O [checklist de publicação](release-final.md) registra os passos pendentes, e a [matriz dos cartões](rastreabilidade-final.md) distingue os marcos acadêmicos e as evidências disponíveis.
