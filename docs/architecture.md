# Arquitetura inicial

```text
Navegador
  |
  | HTTP /api + Bearer JWT
  v
Angular :4200  --->  Spring Security  --->  Spring Boot :8080  --->  PostgreSQL :5432
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
- A autenticação é stateless: senhas usam BCrypt e tokens JWT têm assinatura, emissor e expiração validados.
- O perfil melhora a navegação no Angular, mas toda autorização é aplicada novamente pelo Spring Security.
- O usuário autenticado é recarregado do banco em cada requisição, impedindo que contas inativas continuem usando um token emitido anteriormente.
- O Hibernate usa `ddl-auto=validate`, nunca cria ou altera tabelas e valida os mapeamentos de `Usuario`, `Categoria` e `Chamado` contra o schema criado pelo Flyway.
- A migration inicial prepara as entidades centrais do domínio sem inserir usuários ou credenciais.
- A abertura de chamado é transacional: a API recarrega o solicitante ativo, bloqueia a categoria ativa para leitura durante a criação e persiste o chamado antes de produzir o DTO.
- Protocolos usam 128 bits gerados por `SecureRandom`; a constraint única do PostgreSQL é a barreira final e colisões são repetidas em transações independentes e limitadas.
- Somente o perfil `dev` carrega categorias fictícias reproduzíveis por uma migration em `db/devdata`; produção executa apenas `db/migration`.
- As consultas do solicitante são escopadas pelo identificador autenticado já no repositório. Um detalhe inexistente e um detalhe pertencente a terceiros produzem a mesma resposta `404`, evitando confirmar a existência de dados alheios.
- A listagem simples da AC1 usa o índice existente em `(solicitante_id, criado_em)` e não antecipa paginação ou filtros da AC3.
