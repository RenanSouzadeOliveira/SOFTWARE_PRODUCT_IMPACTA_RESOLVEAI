# Guia da API e do Swagger

Com a demonstração em execução, abra `http://127.0.0.1:18595/swagger-ui.html`. No desenvolvimento convencional, use a porta 8080. A interface redireciona para `/swagger-ui/index.html`; o JSON está em `/v3/api-docs`. Para conferir a especificação da instância local:

```bash
curl -fsS http://127.0.0.1:18595/v3/api-docs | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["openapi"], len(d["paths"]))'
```

Na operação `POST /api/auth/login`, use uma conta sintética da sua execução. Copie apenas o `accessToken` para **Authorize → bearerAuth** na sua sessão local. Encerre a autorização ao terminar. A documentação, saúde, login e cadastro são públicos na configuração da aplicação. Os demais endpoints funcionais exigem JWT. A configuração de segurança também permite o Swagger em `prod`; qualquer restrição adicional depende da implantação. Nenhum host público da API foi definido para esta entrega.

O contrato final contempla as consultas, a abertura e o ciclo de vida, os catálogos, a exportação CSV, a administração e o dashboard. Os 27 caminhos e 32 operações HTTP foram observados na especificação gerada da aplicação. O Spring expõe o contrato HTTP; tarefas internas do Flyway e o script local de demonstração são executados fora dessa API.

## Endpoints disponíveis

| Método e caminho | Autorização e função |
| --- | --- |
| `GET /api/health` | Público: saúde da API |
| `POST /api/auth/register` | Público: cadastro sempre SOLICITANTE |
| `POST /api/auth/login` | Público: autenticação de conta ativa |
| `GET /api/usuarios/me` | Autenticado: própria conta |
| `PATCH /api/usuarios/me` | Autenticado: próprio nome |
| `GET /api/categorias` | Autenticado: categorias ativas para abertura |
| `POST /api/chamados` | SOLICITANTE: abertura com autor definido pelo token |
| `GET /api/chamados/me` | SOLICITANTE: seus chamados |
| `GET /api/chamados/{id}` | Proprietário, responsável ou ADMIN: detalhe e ações permitidas |
| `GET /api/atendimento/acesso` | ATENDENTE/ADMIN: verificação de acesso |
| `GET /api/atendimento/fila` | ATENDENTE/ADMIN: ABERTO sem responsável, filtros e paginação |
| `GET /api/atendimento/me` | ATENDENTE/ADMIN: sua carteira, filtros e paginação |
| `GET /api/atendimento/todos` | Somente ADMIN ativo: todos os chamados, filtros e paginação |
| `GET /api/atendimento/categorias` | ATENDENTE/ADMIN: categorias ativas e inativas para consulta |
| `PATCH /api/chamados/{id}/assumir` | ATENDENTE/ADMIN: responsável atual e EM_ATENDIMENTO |
| `PATCH /api/chamados/{id}/prioridade` | Responsável em atendimento ou ADMIN em estado não terminal |
| `PATCH /api/chamados/{id}/status` | Conforme máquina de estados, perfil e participação |
| `GET /api/chamados/{id}/comentarios` | Participantes/ADMIN: conversa, inclusive fechado |
| `POST /api/chamados/{id}/comentarios` | Participantes/ADMIN: comentário imutável, exceto fechado |
| `GET /api/chamados`, `/api/chamados/filtros`, `/api/chamados/exportacao` | Consulta por escopo, opções de filtro e CSV |
| `GET /api/chamados/{id}/historico` | Eventos de criação, atribuição, status e prioridade conforme visibilidade |
| `GET /api/dashboard/resumo` | Contagens e distribuições no escopo do perfil |
| `GET` e `POST /api/admin/usuarios` ou `/api/admin/categorias` | Listar e criar, somente ADMIN |
| `PATCH /api/admin/usuarios/{id}` ou `/api/admin/categorias/{id}` | Editar cadastro, somente ADMIN |
| `PATCH /api/admin/usuarios/{id}/situacao` ou `/api/admin/categorias/{id}/situacao` | Ativar ou desativar, preservando histórico |

## Fluxo e entradas

```text
ABERTO -- assumir --> EM_ATENDIMENTO -- resolver --> RESOLVIDO -- confirmar --> FECHADO
                           ^                           |
                           +------ reabrir ------------+
```

- Abertura: `{ titulo, descricao, categoriaId, prioridade }`.
- Atribuição: sem responsável no corpo; o servidor usa o usuário autenticado.
- Status: `{ status, justificativa? }`; resolver/reabrir exigem 10–500 caracteres após remoção de espaços externos.
- Prioridade: `{ prioridade }`, BAIXA/MEDIA/ALTA; mesmo valor retorna `409`.
- Comentário: `{ conteudo }`, 1–2.000 caracteres após remoção de espaços externos Unicode.

O solicitante proprietário confirma ou reabre a resolução; o responsável resolve; ADMIN pode realizar essas ações conforme o estado. FECHADO é terminal. Antes da atribuição, somente proprietário e ADMIN comentam. Comentários não modificam o status. Não há endpoints de edição ou exclusão de comentários.

## Contratos e erros

Fila/carteira/listagem global retornam `{ content, page, size, totalElements, totalPages }`, com ordenação por criação e ID decrescentes. `page` inicia em zero; `size` padrão 20, máximo 100 após a integração AC3. A ordenação aceita `sort`; filtros combinados incluem `search`, `prioridade`, `categoriaId`, `responsavel` e datas UTC, além de `status` fora da fila. Consulte [filtros AC3](filtros-chamados.md).

A listagem global não restringe os resultados ao proprietário ou responsável autenticado e inclui todos os estados, inclusive FECHADO; os filtros explícitos continuam aplicáveis. Exemplo: `GET /api/atendimento/todos?page=0&size=10&status=EM_ATENDIMENTO&prioridade=ALTA&categoriaId=1`. Sem token ou com conta inativa retorna `401`; ATENDENTE e SOLICITANTE recebem `403`; filtros ou paginação inválidos retornam `400`. A resposta usa o mesmo DTO seguro da carteira, sem credenciais, e-mail ou entidades JPA. Os detalhes e ações continuam em `/api/chamados/{id}`, respeitando a máquina de estados.

Detalhe inclui `acoesPermitidas` e `historicoStatus`. Comentários retornam `{ content, podeComentar }`, com data/ID crescentes. Datas são ISO 8601 UTC. DTOs não expõem entidades JPA, senha, hash ou dados desnecessários de usuários.

Erros seguem `{ timestamp, status, error, message, path, violations? }`. `400` indica entrada inválida; `401`, autenticação inválida/inativa; `403`, operação proibida; `404`, recurso inexistente ou consulta sem visibilidade; `409`, estado inválido ou concorrência. Falhas internas não retornam stack trace ou SQL.

Contratos detalhados: [autenticação](autenticacao.md), [abertura](abertura-chamado.md), [consulta](consulta-chamados.md), [fila](fila-atendimento.md), [status](status-prioridade.md) e [comentários](comentarios-chamados.md).
