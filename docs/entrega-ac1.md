# Entrega da AC1

Este roteiro consolida a base integrada, autenticação, abertura e consulta de chamados em uma execução reproduzível. Use somente dados fictícios durante a demonstração.

## Preparar um ambiente limpo

1. Clone o repositório público em um diretório novo.
2. Na raiz, execute `cp backend/.env.example .env`.
3. Gere um valor local com `openssl rand -hex 32` e substitua apenas o valor de `JWT_SECRET` no `.env`.
4. Confirme que `DB_URL`, `POSTGRES_PORT` e as credenciais fictícias apontam para o mesmo PostgreSQL local.
5. Execute `docker compose up -d postgres` e aguarde `docker compose ps` indicar `healthy`.
6. Inicie a API com os comandos do README. Confirme no log que o Flyway aplicou V1, V2, V3 e a carga fictícia do perfil `dev`, e que o Hibernate validou o schema.
7. Execute `npm ci` e `npm start` dentro de `frontend/`.
8. Abra `http://localhost:4200` e confira a apresentação do problema, do funcionamento do sistema e das ações de cadastro e login.

Para repetir a demonstração desde um banco vazio, execute `docker compose down -v` somente quando o volume local puder ser descartado e então refaça os passos acima.

## Dados fictícios

O perfil `dev` executa `db/devdata/R__seed_development_categories.sql` e disponibiliza categorias de demonstração sem usuários, senhas ou dados pessoais. Produção não carrega esse arquivo.

Crie os usuários pelo próprio fluxo público durante o vídeo, por exemplo:

- `solicitante.um@example.com`;
- `solicitante.dois@example.com`.

As senhas usadas na gravação devem ser descartáveis e não devem aparecer no vídeo, nos logs, no cartão ou no repositório. Os chamados também devem usar títulos e descrições inteiramente fictícios.

## Verificação automatizada

Front-end:

```bash
cd frontend
npm ci
npm test -- --watch=false
npm run build
```

Back-end:

```bash
cd backend
mvn clean verify
```

Critérios para prosseguir com a gravação:

- todos os testes passam;
- o build Angular termina sem erros;
- o JAR é criado em `backend/target/`;
- `git status --short` não mostra artefatos, `.env` ou credenciais;
- a documentação OpenAPI abre em `http://localhost:8080/swagger-ui.html`.

## Roteiro do vídeo

1. Mostre brevemente a página inicial, a arquitetura de três camadas e a resposta de `GET /api/health` pelo Swagger ou terminal.
2. Cadastre o primeiro solicitante e destaque que o perfil é definido pelo servidor.
3. Saia e entre novamente, mostrando login, rota protegida e **Minha conta**.
4. Altere o nome em **Minha conta**, saia, entre novamente e confirme a persistência.
5. Abra um chamado; primeiro provoque validações de título e descrição e depois corrija os campos.
6. Mostre o protocolo gerado, o status `ABERTO` e a ausência de atendente.
7. Abra um segundo chamado e mostre que os protocolos são diferentes.
8. Abra **Meus chamados**, confirme a ordem do mais recente e consulte um detalhe.
9. Cadastre ou entre com o segundo solicitante, compare as listagens e tente abrir pela URL o chamado do primeiro; mostre o `404` sem dados alheios.
10. Mostre no PostgreSQL as migrations, os relacionamentos e os chamados associados aos respectivos `solicitante_id`.
11. Mostre no terminal da API os eventos `USUARIO_CADASTRADO`, `LOGIN_REALIZADO`, `CONTA_ATUALIZADA` e `CHAMADO_ABERTO`, junto aos respectivos status HTTP.
12. Mostre os resultados finais dos testes e builds.

Os logs funcionais registram IDs, perfil, protocolo e estado, sem nome, e-mail ou conteúdo informado pelo usuário. Evite enquadrar terminais que contenham `.env`, tokens JWT, cabeçalhos `Authorization`, senhas ou dados reais.

## Evidências públicas

Antes de mover o cartão para **Concluído**, abra os links em uma janela anônima e registre no cartão:

| Evidência | Verificação |
| --- | --- |
| Repositório | Público, código e documentação da AC1 presentes. |
| Commit | Link para o commit final da AC1, com mensagem em português. |
| Release | Link para uma release pública que identifique a versão demonstrada. |
| Vídeo | Link público ou não listado acessível sem solicitar permissão. |
| Trello | Quadro e cartão acessíveis sem autenticação; links acima anexados ao cartão. |

O repositório público configurado neste projeto é [SOFTWARE_PRODUCT_IMPACTA_RESOLVEAI](https://github.com/RenanSouzadeOliveira/SOFTWARE_PRODUCT_IMPACTA_RESOLVEAI). Os links do cartão, release e vídeo devem ser registrados após sua criação; não invente URLs nem publique links privados no código.

## Consultas de apoio

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT id, protocolo, solicitante_id, atendente_id, categoria_id,
       prioridade, status, criado_em
FROM chamados
ORDER BY criado_em DESC;
```
