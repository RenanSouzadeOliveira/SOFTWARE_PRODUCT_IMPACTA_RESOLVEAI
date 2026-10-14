# API consolidada — AC2

A especificação executável é gerada pelo código em `/v3/api-docs`; a interface fica em `/swagger-ui.html`. O roteiro isolado exporta a especificação para `openapi.json`, junto à evidência técnica da versão executada.

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

Fila/carteira/listagem global retornam `{ content, page, size, totalElements, totalPages }`, com ordenação por criação e ID decrescentes. `page` inicia em zero; `size` padrão 10, máximo 50. Filtros: `prioridade`, `categoriaId` e, na carteira e listagem global, `status`.

A listagem global não filtra por proprietário ou responsável e inclui todos os estados, inclusive FECHADO. Exemplo: `GET /api/atendimento/todos?page=0&size=10&status=EM_ATENDIMENTO&prioridade=ALTA&categoriaId=1`. Sem token ou com conta inativa retorna `401`; ATENDENTE e SOLICITANTE recebem `403`; filtros ou paginação inválidos retornam `400`. A resposta usa o mesmo DTO seguro da carteira, sem credenciais, e-mail ou entidades JPA. Os detalhes e ações continuam em `/api/chamados/{id}`, respeitando a máquina de estados.

Detalhe inclui `acoesPermitidas` e `historicoStatus`. Comentários retornam `{ content, podeComentar }`, com data/ID crescentes. Datas são ISO 8601 UTC. DTOs não expõem entidades JPA, senha, hash ou dados desnecessários de usuários.

Erros seguem `{ timestamp, status, error, message, path, violations? }`. `400` indica entrada inválida; `401`, autenticação inválida/inativa; `403`, operação proibida; `404`, recurso inexistente ou consulta sem visibilidade; `409`, estado inválido ou concorrência. Falhas internas não retornam stack trace ou SQL.

Contratos detalhados: [autenticação](autenticacao.md), [abertura](abertura-chamado.md), [consulta](consulta-chamados.md), [fila](fila-atendimento.md), [status](status-prioridade.md) e [comentários](comentarios-chamados.md).
