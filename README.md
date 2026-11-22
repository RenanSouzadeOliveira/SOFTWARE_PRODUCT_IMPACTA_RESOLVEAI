# ResolveAí

O ResolveAí é um projeto acadêmico de gestão de chamados. Um solicitante registra o problema e acompanha a resposta pelo protocolo. O atendente assume o chamado na fila, registra as ações e propõe uma solução. A administração acompanha os indicadores e mantém os cadastros de usuários e categorias. Busca, filtros, paginação, exportação CSV, comentários e histórico atendem aos três perfis dentro das permissões de cada um.

## Executar a demonstração em um ambiente limpo

Em Linux com Bash, instale Git, Java/JDK 21 (incluindo `jar`), Maven, Node.js compatível com Angular 21 (por exemplo, 22.12+ na linha 22), npm 10, Docker com acesso ao daemon, curl e OpenSSL. O Compose v2 atende ao modo de desenvolvimento descrito abaixo. A primeira instalação precisa acessar os registros Maven, npm e a imagem `postgres:17-alpine`.

```bash
git clone https://github.com/RenanSouzadeOliveira/SOFTWARE_PRODUCT_IMPACTA_RESOLVEAI.git
cd SOFTWARE_PRODUCT_IMPACTA_RESOLVEAI
# Para avaliar uma entrega específica, selecione a revisão fornecida com a entrega.
mvn -B -ntp -f backend/pom.xml clean verify
(cd frontend && npm ci && npm test -- --watch=false && npm run build)
bash scripts/demo-final.sh --verificar
bash scripts/demo-final.sh
```

A distribuição externa precisa conter `scripts/demo-final.sh`. Enquanto a publicação estiver pendente, use o checkout da entrega local. O comando de verificação inicia o JAR, aplica as migrations, prepara a massa duas vezes e confere os totais pela API através do frontend. O último comando abre um laboratório novo. Acesse `http://127.0.0.1:14595` e use uma conta indicada no arquivo privado cujo caminho aparece no terminal. As seis contas têm senhas aleatórias e são exclusivas dessa execução. Escolha `solicitante1`, `atendente1` ou `admin` conforme a cena. Feche o visualizador das contas antes de gravar.

O banco fica em memória e só aceita conexões pela interface local. Ctrl+C encerra este laboratório e descarta banco e credenciais. Para reiniciar a demo use o mesmo comando. Para escolher portas livres, defina `DEMO_API_PORT` e `DEMO_WEB_PORT`; os padrões são 18595 e 14595. Este procedimento independe de arquivos `.env` e de cadastros anteriores. A criação do primeiro ADMIN na demo é feita pelo inicializador acadêmico, que usa o codificador de senhas da aplicação. O cadastro público cria SOLICITANTE. Não há bootstrap administrativo automático no perfil de produção.

| Componente | Versão de referência |
| --- | --- |
| API | Java 21, Spring Boot 3.5.16 e springdoc 2.8.16 |
| Interface | Angular 21.2.26, CLI 21.2.30 e TypeScript 5.9.3. Demais versões em `frontend/package-lock.json`; npm declarado 10.9.2 |
| Persistência | PostgreSQL 17 com UTF-8, Hibernate em modo `validate`, Flyway V1–V12 |
| Ferramentas de contêiner | Docker 29.1.3 e Compose 2.40.3 observados na validação |

O JAR executável fica em `backend/target/resolveai-api-0.0.1-SNAPSHOT.jar`. O site estático fica em `frontend/dist/frontend/browser/`; em produção, o servidor da interface precisa encaminhar `/api` ao backend. O nome interno do JAR é o definido no POM.

## Guia do avaliador

- [Arquitetura da aplicação](docs/architecture.md) e [modelo entidade-relacionamento](docs/modelo-entidade-relacionamento.md)
- [OpenAPI e contratos da API](docs/api-ac2.md)
- [Vídeo narrado](docs/evidencias/apresentacao-final.webm), [cenas e narração](docs/demonstracao-final.md) e [capturas de tela](docs/evidencias/ux-p1/README.md)
- [Relatório de validação e limitações](docs/validacao-final.md), [rastreabilidade das quatro entregas](docs/rastreabilidade-final.md) e [notas e checklist de publicação](docs/release-final.md)

## Desenvolvimento com PostgreSQL persistente

O Compose inicia o PostgreSQL. A API e o Angular rodam em terminais separados. As portas padrão deste modo são 5432, 8080 e 4200. Use a demonstração descartável acima para apresentar os três perfis. Para desenvolver em um checkout novo, crie um arquivo de ambiente privado. O comando a seguir recusa substituir um `.env` existente e gera uma senha única para o banco e uma chave de assinatura independente.

```bash
(
  set -euC
  umask 077
  senha_local=$(openssl rand -hex 24)
  chave_local=$(openssl rand -hex 48)
  sed -e "s/troque-esta-senha-local/$senha_local/g" \
      -e "s/troque-esta-chave/$chave_local/" backend/.env.example > .env
)
```


### Variáveis de ambiente

| Variável | Obrigatória | Uso |
| --- | --- | --- |
| `POSTGRES_DB` | Sim | Nome do banco criado pelo container. |
| `POSTGRES_USER` | Sim | Usuário local do PostgreSQL. |
| `POSTGRES_PASSWORD` | Sim | Senha local do PostgreSQL; nunca deve ser versionada. |
| `POSTGRES_PORT` | Não | Porta publicada pelo container; padrão `5432`. |
| `DB_URL` | Sim | URL JDBC da API, coerente com banco e porta configurados. |
| `DB_USERNAME` | Sim | Usuário usado pela API. |
| `DB_PASSWORD` | Sim | Senha usada pela API. |
| `CORS_ALLOWED_ORIGINS` | Sim | Origens autorizadas, separadas conforme a configuração da aplicação. |
| `JWT_SECRET` | Sim | Segredo de assinatura com pelo menos 32 bytes. |
| `JWT_EXPIRATION` | Não | Duração ISO 8601 do token; padrão `PT2H`. |
| `SPRING_PROFILES_ACTIVE` | Sim | Exatamente um entre `dev`, `test` e `prod`; sem ele a API recusa iniciar (não há perfil padrão). O exemplo local usa `dev`. |

O repositório contém somente valores fictícios em `.env.example`. O arquivo `.env` é ignorado pelo Git e deve permanecer local.

## Executar os três componentes

### 1. PostgreSQL

Na raiz do projeto:

```bash
docker compose up -d postgres
docker compose ps
```

O healthcheck deve mostrar o banco como `healthy`.

### 2. API Spring Boot

Em outro terminal:

```bash
cd backend
set -a
source ../.env
set +a
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

O Flyway aplica automaticamente todas as migrations antes de o Hibernate validar o schema. A API fica disponível em `http://localhost:8080`; a documentação OpenAPI fica em `http://localhost:8080/swagger-ui.html`.

`JWT_SECRET` deve permanecer com pelo menos 32 bytes em todos os ambientes. `JWT_EXPIRATION` aceita uma duração ISO 8601, como `PT2H`.

Valide diretamente:

```bash
curl -i http://localhost:8080/api/health
```

### Logs da API para demonstração

O terminal da API registra uma linha HTTP para cada chamada em `/api` e eventos funcionais de auditoria. Os eventos incluem somente identificadores técnicos e estados; e-mail, nome, senha, hash, JWT, cabeçalhos e conteúdo dos formulários não são registrados.

Exemplos exibidos durante cadastro, login, atualização da conta e abertura de chamado:

```text
AUDITORIA evento=CADASTRO_PUBLICO resultado=CONFIRMADO atorId=1 alvoTipo=USUARIO alvoId=1 perfil=SOLICITANTE ativo=true data=...
HTTP metodo=POST caminho=/api/auth/register status=201 duracaoMs=120
AUDITORIA evento=LOGIN_REALIZADO usuarioId=1 perfil=SOLICITANTE
AUDITORIA evento=CONTA_ATUALIZADA usuarioId=1 campos=nome
AUDITORIA evento=CHAMADO_ABERTO chamadoId=10 protocolo=RA-... solicitanteId=1 status=ABERTO atendenteId=null
HTTP metodo=POST caminho=/api/chamados status=201 duracaoMs=35
AUDITORIA evento=CHAMADO_PRIORIDADE chamadoId=10 protocolo=RA-... autorId=2 perfil=ATENDENTE prioridadeAnterior=MEDIA prioridadeNova=ALTA
AUDITORIA evento=COMENTARIO_CRIADO comentarioId=1 chamadoId=10 protocolo=RA-... autorId=2 perfil=ATENDENTE
AUDITORIA evento=CHAMADO_RESOLVIDO chamadoId=10 protocolo=RA-... autorId=2 perfil=ATENDENTE statusAnterior=EM_ATENDIMENTO statusNovo=RESOLVIDO
AUDITORIA evento=CHAMADO_REABERTO chamadoId=10 protocolo=RA-... autorId=1 perfil=SOLICITANTE statusAnterior=RESOLVIDO statusNovo=EM_ATENDIMENTO
AUDITORIA evento=CHAMADO_FECHADO chamadoId=10 protocolo=RA-... autorId=1 perfil=SOLICITANTE statusAnterior=RESOLVIDO statusNovo=FECHADO
```

Os eventos de alteração da AC2 são emitidos somente após a confirmação da transação no banco: uma falha ou rollback não gera log de sucesso. Comentários e justificativas não aparecem nos logs. Requisições recusadas podem ser identificadas na linha HTTP pelo código 400, 403 ou 409.

Os valores variam em cada execução. Para usar como evidência, mantenha o terminal em uma área visível durante o fluxo e não mostre o arquivo `.env`.

### 3. Angular

Em outro terminal:

```bash
cd frontend
npm ci
npm start
```

Abra `http://localhost:4200`. A página inicial apresenta o problema resolvido pelo ResolveAí, seu fluxo de atendimento e ações adequadas ao estado de autenticação. No desenvolvimento, a URL configurada aponta para `http://localhost:8080/api`; no build de produção, usa o caminho relativo `/api`.

## Contrato inicial da API

Todas as APIs funcionais usam o prefixo `/api`.

```http
GET /api/health
```

Resposta de sucesso (`200 OK`):

```json
{
  "status": "UP",
  "service": "resolveai-api",
  "timestamp": "2026-09-08T12:00:00Z"
}
```

Os erros usam um objeto JSON consistente com data/hora, status HTTP, tipo, mensagem e caminho. Erros de validação também informam os campos inválidos.

## Autenticação e perfis

Os contratos e o roteiro de validação estão documentados em [Autenticação e autorização](docs/autenticacao.md). Endpoints disponíveis:

- `POST /api/auth/register`: cadastro público, sempre como `SOLICITANTE`;
- `POST /api/auth/login`: autenticação e emissão do JWT;
- `GET /api/usuarios/me`: consulta da conta autenticada;
- `PATCH /api/usuarios/me`: alteração do próprio nome;
- `GET /api/atendimento/acesso`: verificação protegida para `ATENDENTE` ou `ADMIN`.

O Angular disponibiliza as rotas `/cadastro`, `/login`, `/minha-conta` e `/atendimento`. O token permanece apenas na sessão da aba e é removido no logout.

## Abertura de chamado

O primeiro fluxo do solicitante está disponível na rota protegida `/chamados/novo`. O formulário consulta as categorias ativas e envia apenas título, descrição, categoria e prioridade. O protocolo, o solicitante, o status inicial e o atendente são controlados pela API.

- `GET /api/categorias`: lista autenticada de categorias ativas, ordenadas por nome;
- `POST /api/chamados`: abre um chamado para o `SOLICITANTE` autenticado e retorna `201 Created` com o protocolo.

No perfil `dev`, uma migration de dados separada cria categorias fictícias para a demonstração local. Essa carga não é executada em produção. O contrato e o roteiro completo de evidência estão em [Abertura de chamado](docs/abertura-chamado.md).

## Consulta dos próprios chamados

O solicitante autenticado acompanha seus registros pelas rotas protegidas `/chamados` e `/chamados/:id`. A listagem apresenta primeiro os chamados mais recentes e a página de detalhe exibe somente um chamado pertencente à conta atual.

- `GET /api/chamados/me`: lista exclusivamente os chamados do solicitante autenticado;
- `GET /api/chamados/{id}`: consulta um chamado próprio e responde `404` para identificadores inexistentes ou pertencentes a terceiros.

O contrato, a decisão de segurança e o roteiro com dois solicitantes estão em [Consulta dos próprios chamados](docs/consulta-chamados.md).

## Fila e Meus atendimentos — AC2

A área `/atendimento` permite que `ATENDENTE` e `ADMIN` encontrem chamados `ABERTO` sem responsável, filtrem por prioridade/categoria e assumam um atendimento com confirmação. Em `/atendimento/me`, cada usuário consulta sua própria carteira, com filtros e paginação no servidor.

O menu e a aba **Todos os chamados** (`/atendimento/todos`) são exclusivos de `ADMIN`. Essa visão reúne todos os solicitantes, responsáveis e estados, com filtros por status, prioridade e categoria. Cada item abre o detalhe diretamente, com retorno à listagem administrativa. Ela não muda o escopo da fila nem da carteira pessoal e não permite alterar chamados fechados.

- `GET /api/atendimento/fila`: fila paginada de chamados disponíveis.
- `GET /api/atendimento/me`: carteira paginada do usuário autenticado.
- `GET /api/atendimento/todos`: listagem global paginada exclusiva de ADMIN ativo; outros perfis recebem `403`.
- `GET /api/atendimento/categorias`: categorias do filtro, incluindo inativas para consultar chamados existentes.
- `PATCH /api/chamados/{id}/assumir`: registra responsável e data de atribuição, muda o status para `EM_ATENDIMENTO` e impede sobrescrita concorrente com resposta `409`.

Os contratos, regras de concorrência e validação estão em [Fila e Meus atendimentos](docs/fila-atendimento.md). A migration V4 evolui o schema automaticamente; os comandos de execução permanecem os mesmos.

## Transições de status e prioridade — AC2

O detalhe mostra as ações autorizadas para cada perfil e estado: resolver como responsável, reabrir ou fechar como solicitante proprietário, e operações administrativas. Justificativas são exigidas para resolução e reabertura; chamados fechados são terminais.

- `PATCH /api/chamados/{id}/status`: transição validada pela máquina de estados.
- `PATCH /api/chamados/{id}/prioridade`: atualização autorizada de prioridade.

A migration V5 acrescenta o histórico de status. Alteração e evento são transacionais e protegidos por versão otimista. Consulte [Status, prioridade e histórico](docs/status-prioridade.md) para contratos, permissões e validação.

## Comentários no chamado — AC2

O detalhe permite uma conversa persistente entre solicitante proprietário, atendente atribuído e ADMIN. A conversa tem ordenação cronológica estável, autor, perfil e data. Chamados fechados são somente leitura; comentários não alteram o status e não podem ser editados ou excluídos pela API.

- `GET /api/chamados/{id}/comentarios`: conversa e permissão atual de envio.
- `POST /api/chamados/{id}/comentarios`: comentário com autor obtido da autenticação.

A migration V6 evolui a tabela existente. Consulte [Comunicação por comentários](docs/comentarios-chamados.md) para contratos, persistência e validação.

## Busca e filtros combinados — AC3

As listagens de solicitante, fila e carteira agora combinam busca por protocolo/título/descrição, status, prioridade, categoria, responsável (quando aplicável) e período de criação. A URL mantém filtros, página, tamanho e ordenação; a busca aguarda 350 ms de pausa e Limpar filtros restaura os padrões AC3. O ADMIN dispõe da consulta global em `/consulta-chamados`.

- `GET /api/chamados`: consulta paginada autorizada; solicitante vê próprios, atendente vê atribuídos e ADMIN vê todos.
- `GET /api/chamados/filtros`: categorias/responsáveis dos chamados visíveis, somente ID e nome.
- `/api/chamados/me`, `/api/atendimento/fila` e `/api/atendimento/me`: contratos anteriores preservados, com os novos filtros opcionais.

Parâmetros: `search`, `status`, `prioridade`, `categoriaId` (alias `categoria`), `responsavel`, `dataInicial`, `dataFinal`, além de `page`, `size` e `sort`. Datas no formato `AAAA-MM-DD`, incluindo todo o dia final em UTC. A V8 acrescenta somente o índice de ordenação global, justificado por EXPLAIN. Consulte [Busca e filtros AC3](docs/filtros-chamados.md) para contrato, matriz de aceite, evidências, limitações e roteiro do vídeo.

## Dashboard operacional

O menu **Dashboard** abre `/dashboard` para usuários autenticados. O resumo usa a mesma visibilidade de `GET /api/chamados`: chamados próprios para solicitantes, carteira para atendentes e visão global para administradores.

`GET /api/dashboard/resumo` retorna `total`, `abertos`, `emAtendimento`, `concluidos` e as distribuições `porStatus`, `porPrioridade` e `porCategoria`. Concluídos somam `RESOLVIDO` e `FECHADO`. Isso inclui chamados com resolução registrada que ainda aguardam fechamento. Status, prioridades e categorias ativas aparecem mesmo com quantidade zero. Categorias inativas aparecem se tiverem chamados visíveis. O [Swagger do ambiente local](http://localhost:8080/swagger-ui.html) detalha os campos da resposta.

## Modelo de dados

- [Diagrama de casos de uso](docs/diagrama-casos-de-uso.md): visão completa por ator, com separação entre AC1 implementada e funcionalidades planejadas.
- [Modelo entidade-relacionamento](docs/modelo-entidade-relacionamento.md): diagrama Mermaid, cardinalidades, constraints e índices do schema.
- [Catálogo de campos do front-end](docs/campos-frontend.md): DTOs planejados para formulários e telas, sem exposição das entidades JPA.
- [Autenticação e autorização](docs/autenticacao.md): contratos HTTP, segurança do JWT e roteiro de evidência.
- [Abertura de chamado](docs/abertura-chamado.md): formulário, contratos, regras protegidas e roteiro de validação.
- [Limpeza e reprodução dos builds](docs/limpeza-repositorio.md): inventário, backup reversível e verificações padrão aprovadas.
- [Paginação e ordenação AC3](docs/paginacao-chamados.md): contrato, índices, testes e demonstração reproduzível.
- [Consulta dos próprios chamados](docs/consulta-chamados.md): listagem, detalhe, isolamento entre solicitantes e roteiro de evidência.

As entidades `Usuario`, `Categoria` e `Chamado` mapeiam explicitamente o schema resultante das migrations. Os relacionamentos JPA são lazy, não possuem cascata de remoção e os repositórios expõem apenas operações de persistência e consulta. Usuários e categorias são desativados logicamente.

## Administração de usuários e categorias

O perfil `ADMIN` acessa **Administração** na navegação. Em **Usuários**, pode criar contas com senha inicial, editar nome, e-mail e perfil, além de ativar ou desativar outras contas. Em **Categorias**, pode criar, editar, ativar e desativar registros. A API impede autodesativação e remoção do próprio perfil administrativo. Edições sem mudança de perfil preservam o perfil atual; a mudança de perfil exige a versão observada do registro e retorna `409` se outra pessoa alterou a conta. Mudanças de perfil ou situação encerram as sessões anteriores da conta.

A API usa `GET` e `POST` em `/api/admin/usuarios` e `/api/admin/categorias`. Use `PATCH /{id}` para editar os dados e `PATCH /{id}/situacao` para alterar a situação. As listas administrativas incluem registros inativos. O catálogo `GET /api/categorias` continua exibindo apenas as categorias disponíveis para abrir chamados. A desativação preserva vínculos, histórico e a reserva do e-mail ou nome. O catálogo e os contratos administrativos estão disponíveis no Swagger.

As alterações administrativas geram eventos de auditoria somente após a confirmação da transação, com o administrador em `atorId` e o registro afetado em `alvoTipo`/`alvoId`. O cadastro público usa `CADASTRO_PUBLICO`; a conta criada por um administrador usa `USUARIO_CRIADO_POR_ADMIN`. Os demais eventos são `USUARIO_ATUALIZADO`, `USUARIO_SITUACAO_ALTERADA`, `CATEGORIA_CRIADA`, `CATEGORIA_EDITADA` e `CATEGORIA_SITUACAO_ALTERADA`:

```text
AUDITORIA evento=USUARIO_CRIADO_POR_ADMIN resultado=CONFIRMADO atorId=1 alvoTipo=USUARIO alvoId=5 perfil=ATENDENTE ativo=true data=...
AUDITORIA evento=CATEGORIA_SITUACAO_ALTERADA resultado=CONFIRMADO atorId=1 alvoTipo=CATEGORIA alvoId=3 perfil=null ativo=false data=...
```

## Testes e builds

A suíte padrão combina unidades, H2 e MockMvc. Para incluir os testes reais de concorrência, snapshot e upgrades com PostgreSQL 17, use o script abaixo. Ele inicia uma instância em memória exclusiva e cria quatro bancos vazios. Nenhuma variável de conexão ou papel previamente cadastrado é necessário.

```bash
bash scripts/testar-postgres17.sh
```

A [validação final](docs/validacao-final.md) discrimina os resultados. O [relatório da AC2](docs/entrega-ac2.md) e o [relatório da AC3](docs/ac3-release.md) registram as respectivas revisões históricas.

Front-end:

```bash
cd frontend
npm test -- --watch=false
npm run build
```

Back-end:

```bash
cd backend
mvn clean verify
```

O comando `verify` executa os testes e gera `backend/target/resolveai-api-0.0.1-SNAPSHOT.jar`.

## Validar migrations desde um banco vazio

O [laboratório descartável](scripts/demo-final.sh) parte de um banco novo, inicia o JAR no perfil `dev` e aplica V1–V12 e a migration repetível das categorias antes do seed acadêmico. O [relatório final](docs/validacao-final.md) registra a instalação distinta em `prod` sem categorias ou contas iniciais.

Para inspecionar as tabelas e os relacionamentos usados como evidência:

```bash
set -a
source .env
set +a
docker compose exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c '\dt'
docker compose exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c '\d usuarios'
docker compose exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c '\d categorias'
docker compose exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c '\d chamados'
```

## Perfis e segurança de configuração

- `dev`: desenvolvimento local, com migrations automáticas e sem criação de schema pelo Hibernate.
- `prod`: exige as variáveis de conexão e mantém `ddl-auto=validate`, validando as entidades JPA contra o schema criado pelo Flyway.
- `test`: isolado para a suíte automatizada.
- Flyway é a única ferramenta autorizada a criar ou evoluir o schema.
- Não existe perfil padrão: sem `SPRING_PROFILES_ACTIVE` (ou com mais de um entre `dev`/`test`/`prod`) a API falha antes de conectar ao banco. O seed de categorias (`db/devdata`) roda somente no perfil `dev`.
- CORS aceita somente as origens informadas por `CORS_ALLOWED_ORIGINS` (lista separada por vírgula no formato `esquema://host[:porta]`); a API recusa iniciar com curingas (`*`), valores vazios, caminho, query, fragmento ou credenciais na URL. HTTPS não é imposto no perfil `prod` porque implantações atrás de proxy reverso ou em rede interna podem usar origens HTTP legítimas; use HTTPS sempre que a interface for pública. Como o JWT trafega no cabeçalho `Authorization`, credenciais de navegador (cookies) não são habilitadas no CORS.
- Erros seguem o contrato `ApiErrorResponse`: `400` (com `violations` por campo quando aplicável), `401` (token ausente, inválido, expirado, revogado ou conta inativa), `403` (perfil sem permissão), `404` (recurso inexistente **ou fora do escopo do usuário**, inclusive em escritas como comentário, status e prioridade, para não revelar chamados de terceiros), `409` (conflitos de negócio) e `5xx` com mensagem genérica, sem stack trace, SQL ou segredos. O frontend mostra uma página de acesso negado (`/acesso-negado`) e nunca exibe conteúdo do servidor em falhas `5xx`/rede.
- Nenhuma senha, token ou chave é fornecida pelo código-fonte.
