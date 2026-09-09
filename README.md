# ResolveAí

Fundação executável do ResolveAí, sistema acadêmico de gestão de chamados em três camadas:

- Angular e TypeScript no front-end;
- Java 21 e Spring Boot no back-end;
- PostgreSQL com migrations Flyway.

## Pré-requisitos

- Node.js 22.12 ou superior na linha 22 e npm 10+
- Java 21
- Maven 3.6.3+
- Docker com Docker Compose

## Configuração local

Crie o arquivo local de ambiente a partir do exemplo e ajuste os valores fictícios:

```bash
cp .env.example .env
```

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

Abra `http://localhost:4200`. A página inicial consulta `GET /api/health` e apresenta os estados de carregamento, sucesso e erro. No desenvolvimento, a URL configurada aponta para `http://localhost:8080/api`; no build de produção, usa o caminho relativo `/api`.

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

## Modelo de dados

- [Modelo entidade-relacionamento](docs/modelo-entidade-relacionamento.md): diagrama Mermaid, cardinalidades, constraints e índices do schema.
- [Catálogo de campos do front-end](docs/campos-frontend.md): DTOs planejados para formulários e telas, sem exposição das entidades JPA.

As entidades `Usuario`, `Categoria` e `Chamado` mapeiam explicitamente a migration V1. Os relacionamentos JPA são lazy, não possuem cascata de remoção e os repositórios expõem apenas operações de persistência e consulta. Usuários e categorias são desativados logicamente.

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
mvn test
mvn package
```

## Validar migrations desde um banco vazio

Use um volume descartável de desenvolvimento e confirme no log da API que o Flyway aplicou a `V1`:

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
