# ResolveAí

ResolveAí é um sistema acadêmico de gestão de chamados. O incremento da AC2 permite registrar um problema, assumir seu atendimento, trocar comentários, resolver, reabrir e fechar o chamado, com autorização e histórico em três camadas:

- Angular e TypeScript no front-end;
- Java 21 e Spring Boot no back-end;
- PostgreSQL com migrations Flyway.

## Arquitetura

```text
Angular :4200  ── HTTP /api + JWT ──>  Spring Boot :8080  ── JPA ──>  PostgreSQL :5432
                                                └──────── Flyway ──────────────┘
```

O Angular concentra a interface e os contratos tipados; o Spring Boot aplica validação, autenticação, autorização e regras de negócio; o PostgreSQL garante a integridade relacional. O Flyway é a única ferramenta que cria ou evolui o schema e o Hibernate apenas o valida. As decisões completas estão em [Arquitetura inicial](docs/architecture.md).

## Pré-requisitos

- Node.js 22.12 ou superior na linha 22 e npm 10+
- Java 21
- Maven 3.6.3+
- Docker com Docker Compose

## Configuração local

Crie o arquivo local de ambiente a partir do exemplo e ajuste os valores fictícios:

```bash
cp backend/.env.example .env
openssl rand -hex 32
```

Copie a saída aleatória do segundo comando para `JWT_SECRET` no `.env`. O placeholder do exemplo é deliberadamente curto e a API recusa a inicialização até que ele seja substituído.

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
| `SPRING_PROFILES_ACTIVE` | Não | Perfil Spring; o exemplo local usa `dev`. |

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
AUDITORIA evento=USUARIO_CADASTRADO usuarioId=1 perfil=SOLICITANTE
HTTP metodo=POST caminho=/api/auth/register status=201 duracaoMs=120
AUDITORIA evento=LOGIN_REALIZADO usuarioId=1 perfil=SOLICITANTE
AUDITORIA evento=CONTA_ATUALIZADA usuarioId=1 campos=nome
AUDITORIA evento=CHAMADO_ABERTO chamadoId=10 protocolo=RA-... solicitanteId=1 status=ABERTO atendenteId=null
HTTP metodo=POST caminho=/api/chamados status=201 duracaoMs=35
```

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

- `GET /api/atendimento/fila`: fila paginada de chamados disponíveis.
- `GET /api/atendimento/me`: carteira paginada do usuário autenticado.
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

## Modelo de dados

- [Diagrama de casos de uso](docs/diagrama-casos-de-uso.md): visão completa por ator, com separação entre AC1 implementada e funcionalidades planejadas.
- [Modelo entidade-relacionamento](docs/modelo-entidade-relacionamento.md): diagrama Mermaid, cardinalidades, constraints e índices do schema.
- [Catálogo de campos do front-end](docs/campos-frontend.md): DTOs planejados para formulários e telas, sem exposição das entidades JPA.
- [Autenticação e autorização](docs/autenticacao.md): contratos HTTP, segurança do JWT e roteiro de evidência.
- [Abertura de chamado](docs/abertura-chamado.md): formulário, contratos, regras protegidas e roteiro de validação.
- [Consulta dos próprios chamados](docs/consulta-chamados.md): listagem, detalhe, isolamento entre solicitantes e roteiro de evidência.

As entidades `Usuario`, `Categoria` e `Chamado` mapeiam explicitamente o schema resultante das migrations. Os relacionamentos JPA são lazy, não possuem cascata de remoção e os repositórios expõem apenas operações de persistência e consulta. Usuários e categorias são desativados logicamente.

## Testes e builds

### Consolidação reproduzível da AC2

```bash
bash scripts/validar-ac2.sh
```

O comando executa testes e builds, inicia uma API e um PostgreSQL **isolados e vazios**, aplica V1–V6, prepara contas fictícias dos três perfis e chamados nos quatro estados e verifica ciclo, permissões, concorrência e rollback. Não utiliza seu `.env` nem apaga o volume habitual. Portas padrão: banco `55439`, API `18082` e interface `4200`; portas ocupadas são recusadas.

Para validar a interface e gravar uma demonstração técnica local, usando Playwright configurado:

```bash
bash scripts/validar-ac2.sh --com-navegador
```

Relatórios, OpenAPI, capturas e vídeo silencioso são salvos em uma pasta privada `/tmp/resolveai-ac2-*`. O ambiente é removido ao terminar. `--manter` preserva o ambiente de demonstração após sucesso, usando API na porta `8080` para funcionar também fora da automação, com instruções privadas para encerrá-lo. Portas ocupadas são recusadas. **Nunca publique `fixtures.json`: contém senha e tokens fictícios locais.**

Veja [Entrega e evidências da AC2](docs/entrega-ac2.md), [API consolidada](docs/api-ac2.md) e [notas da versão](docs/release-ac2.md). O cartão final não é considerado concluído antes da publicação e vinculação de commit, release e vídeo.

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

Use o roteiro isolado e confirme em `api.log` que o Flyway aplicou V1–V6 antes da validação do Hibernate:

```bash
bash scripts/validar-ac2.sh
```

Esse comando cria e remove somente seu próprio container descartável, sem apagar o volume do PostgreSQL habitual. Não use `docker compose down -v` quando houver dados locais a preservar.

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
- CORS aceita somente as origens informadas por `CORS_ALLOWED_ORIGINS`.
- Nenhuma senha, token ou chave é fornecida pelo código-fonte.
