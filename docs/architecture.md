# Arquitetura inicial

```text
Navegador
  |
  | HTTP /api
  v
Angular :4200  --->  Spring Boot :8080  --->  PostgreSQL :5432
                         |                       ^
                         +------ Flyway ---------+
```

## Responsabilidades

- O Angular apresenta o estado da aplicação e consome contratos tipados.
- O Spring Boot expõe os contratos REST, valida entradas e concentra regras de negócio.
- O PostgreSQL assegura integridade relacional.
- O Flyway é a fonte versionada de evolução do schema.

## Decisões da fundação

- APIs funcionais são publicadas sob `/api`.
- Configuração específica do ambiente é externa ao artefato.
- Datas da API usam ISO 8601 em UTC.
- Erros seguem um único envelope, sem stack trace ou detalhes SQL.
- O Hibernate usa `ddl-auto=validate` e nunca cria ou altera tabelas. A validação dos mapeamentos se torna material quando as primeiras entidades JPA forem implementadas.
- A migration inicial prepara as entidades centrais do domínio sem inserir usuários ou credenciais.
