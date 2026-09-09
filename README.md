# ResolveAí

Fundação executável do ResolveAí, sistema acadêmico de gestão de chamados em três camadas:

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
- `GET /api/atendimento/acesso`: verificação protegida exclusiva de `ATENDENTE`.

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

## Modelo de dados

- [Modelo entidade-relacionamento](docs/modelo-entidade-relacionamento.md): diagrama Mermaid, cardinalidades, constraints e índices do schema.
- [Catálogo de campos do front-end](docs/campos-frontend.md): DTOs planejados para formulários e telas, sem exposição das entidades JPA.
- [Autenticação e autorização](docs/autenticacao.md): contratos HTTP, segurança do JWT e roteiro de evidência.
- [Abertura de chamado](docs/abertura-chamado.md): formulário, contratos, regras protegidas e roteiro de validação.
- [Consulta dos próprios chamados](docs/consulta-chamados.md): listagem, detalhe, isolamento entre solicitantes e roteiro de evidência.

As entidades `Usuario`, `Categoria` e `Chamado` mapeiam explicitamente o schema resultante das migrations. Os relacionamentos JPA são lazy, não possuem cascata de remoção e os repositórios expõem apenas operações de persistência e consulta. Usuários e categorias são desativados logicamente.

## Testes e builds

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

Use um volume descartável de desenvolvimento e confirme no log da API que o Flyway aplicou todas as migrations pendentes:

```bash
docker compose down -v
docker compose up -d postgres
cd backend
set -a
source ../.env
set +a
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

O comando `docker compose down -v` apaga apenas o volume local declarado neste projeto. Não o execute quando houver dados locais que precisem ser preservados.

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

## Consolidar e demonstrar a AC1

O roteiro consolidado para ambiente limpo, dados fictícios, funcionalidades do vídeo e registro das evidências está em [Entrega da AC1](docs/entrega-ac1.md). Não considere a entrega pública concluída antes de preencher no cartão os links do commit, da release e do vídeo e confirmar que repositório e quadro estão acessíveis sem autenticação.
