# Consulta dos próprios chamados

## Fluxo e autorização

As páginas protegidas `/chamados` e `/chamados/:id` permitem que um usuário com perfil `SOLICITANTE` acompanhe apenas os registros que abriu. O Angular usa o perfil para orientar a navegação, enquanto o Spring Security e as consultas escopadas pelo identificador autenticado aplicam a autorização efetiva.

O cliente nunca envia `solicitanteId`. A API obtém esse identificador do JWT e o inclui na própria consulta. Para impedir enumeração, um chamado inexistente e um chamado de outro solicitante retornam o mesmo `404` e a mesma mensagem. Um perfil autenticado que não seja `SOLICITANTE` recebe `403`; token ausente, inválido ou de conta inativa recebe `401`.

## Listar meus chamados

```http
GET /api/chamados/me
Authorization: Bearer <token>
```

Resposta `200 OK`, ordenada do `criadoEm` mais recente para o mais antigo:

```json
[
  {
    "id": 18,
    "protocolo": "RA-xN4W9j8vR0uM6zK2bT5cQg",
    "titulo": "Erro ao acessar o portal",
    "status": "ABERTO",
    "prioridade": "MEDIA",
    "categoria": { "id": 1, "nome": "Acesso e autenticação" },
    "criadoEm": "2026-09-09T15:00:00Z",
    "atualizadoEm": "2026-09-09T15:00:00Z",
    "resolvidoEm": null,
    "fechadoEm": null
  }
]
```

Uma conta sem chamados recebe `200 OK` com `[]`. A tela apresenta estados próprios de carregamento, lista vazia, sucesso e falha, com nova tentativa após erro.

## Consultar detalhe

```http
GET /api/chamados/{id}
Authorization: Bearer <token>
```

A resposta `200 OK` contém protocolo, título, descrição, categoria, prioridade, status, solicitante, atendente, solução e datas por meio de DTOs. Nenhuma entidade JPA é serializada. Selecionar um resumo navega para `/chamados/{id}` e carrega esse contrato.

Respostas relevantes:

- `401`: autenticação ausente ou inválida;
- `403`: perfil autenticado sem permissão para a consulta do solicitante;
- `404`: identificador inexistente ou chamado pertencente a outro solicitante.

## Persistência e índices

O chamado mantém somente a FK `solicitante_id`; nome e e-mail não são duplicados na tabela `chamados`. O índice criado pela V1 em `(solicitante_id, criado_em)` atende a listagem própria e sua ordenação descendente por varredura reversa. Os índices iniciados por `status` já atendem as filas planejadas. Como este fluxo não filtra por status, nenhuma migration ou índice redundante foi acrescentado.

## Roteiro de evidência da AC1

1. Inicie PostgreSQL, API com perfil `dev` e Angular conforme o README.
2. Cadastre dois solicitantes diferentes e abra ao menos um chamado para cada um.
3. Entre com o primeiro solicitante e abra **Meus chamados**; mostre somente os registros dele e a ordenação mais recente primeiro.
4. Selecione um item e confira protocolo, título, categoria, prioridade, status e datas no detalhe.
5. Copie o identificador desse chamado, entre com o segundo solicitante e tente acessar diretamente `/chamados/{id}`; demonstre o `404` sem dados do primeiro usuário.
6. Mostre uma conta sem chamados ou o estado vazio automatizado e demonstre a recuperação do estado de erro.
7. Anexe ao cartão o link do commit e o trecho correspondente do vídeo.

Consulta de apoio para evidência local:

```sql
SELECT id, protocolo, solicitante_id, status, categoria_id, criado_em
FROM chamados
ORDER BY solicitante_id, criado_em DESC;
```
