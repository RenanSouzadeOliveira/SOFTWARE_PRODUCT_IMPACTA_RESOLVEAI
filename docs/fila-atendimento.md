# Fila e Meus atendimentos — AC2

Usuários ativos com perfil `ATENDENTE` ou `ADMIN` acessam a área de atendimento. A fila contém exclusivamente chamados `ABERTO` sem responsável. A carteira **Meus atendimentos** contém exclusivamente chamados atribuídos ao usuário autenticado, inclusive quando esse usuário é administrador.

## Interface

- `/atendimento`: fila, filtros de prioridade e categoria, paginação e confirmação para assumir.
- `/atendimento/me`: carteira pessoal, filtros de prioridade, categoria e status, com data da atribuição.
- Filtros e página permanecem na URL. Alterar um filtro reinicia a paginação.
- Carregamento, lista vazia, falha e sucesso possuem feedback. A confirmação impede envio duplicado e permite cancelar.
- Em conflito, a interface explica que o chamado deixou de estar disponível e atualiza a fila.

## Contratos HTTP

Todas as operações exigem `Authorization: Bearer <token>`. O cadastro público continua criando somente `SOLICITANTE`.

```http
GET /api/atendimento/fila?page=0&size=10&prioridade=ALTA&categoriaId=1
GET /api/atendimento/me?page=0&size=10&status=EM_ATENDIMENTO
GET /api/atendimento/categorias
PATCH /api/chamados/{id}/assumir
```

`page` começa em zero, `size` tem padrão 10 e limite 50. `prioridade` e `categoriaId` são filtros opcionais; `status` é opcional apenas na carteira. Os resultados têm ordenação estável por `criadoEm DESC, id DESC`. Filtros e paginação são aplicados no banco.

O seletor de atendimento inclui categorias ativas e inativas para permitir localizar chamados já registrados. Essa consulta não altera a regra de abertura, que exige categoria ativa.

Resposta das consultas:

```json
{
  "content": [
    {
      "id": 12,
      "protocolo": "RA-protocolo-ilustrativo",
      "titulo": "Falha ao acessar o portal",
      "categoria": { "id": 1, "nome": "Acesso" },
      "prioridade": "ALTA",
      "status": "ABERTO",
      "criadoEm": "2026-10-09T12:00:00Z",
      "atualizadoEm": "2026-10-09T12:00:00Z",
      "atribuidoEm": null,
      "atendente": null
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

O `PATCH` não exige corpo: responsável, status e datas são definidos pelo servidor. Em sucesso, retorna `200` com um item desse mesmo contrato, atualizado para `EM_ATENDIMENTO`, com `atendente` contendo `id` e `nome` do usuário autenticado e `atribuidoEm` em UTC.

| Código | Condição |
| --- | --- |
| `200` | Consulta ou atribuição concluída |
| `400` | Filtro, identificador ou paginação inválidos |
| `401` | Token ausente/inválido ou usuário inativo |
| `403` | Perfil sem permissão, inclusive solicitante |
| `404` | Chamado inexistente |
| `409` | Chamado indisponível ou atribuição concorrente |

Erros mantêm o envelope JSON utilizado na AC1. Os contratos completos estão no Swagger em `/swagger-ui.html`.

## Persistência e concorrência

A migration V4 acrescenta `chamados.atribuido_em` e índices para as consultas paginadas. A coluna de versão existente participa da proteção contra atualizações concorrentes. Atribuição, mudança de status e evento `ATRIBUICAO` no histórico são registrados na mesma transação. Chamados legados sem uma data comprovada conservam `atribuido_em = NULL`.

Duas tentativas de assumir o mesmo chamado podem produzir apenas uma atribuição. A segunda recebe `409` e não substitui o responsável nem cria outro evento de atribuição.

## Validação manual

1. Cadastre contas fictícias pela interface. Para preparar atendentes locais, promova somente essas contas no banco de desenvolvimento; nunca acrescente seleção de perfil ao cadastro público.
2. Como solicitante, abra chamados em categorias e prioridades diferentes. Tente acessar a fila pela API e confirme `403`.
3. Entre como atendente e filtre a fila. Confirme também a paginação.
4. Selecione **Assumir**, cancele uma vez e confirme novamente. O chamado deve sair da fila e aparecer em **Meus atendimentos**.
5. Tente assumir o mesmo ID com outra conta de atendimento. Confirme `409` e a preservação do primeiro responsável.
6. Confira no PostgreSQL:

```sql
SELECT id, protocolo, atendente_id, atribuido_em, status, versao
FROM chamados
ORDER BY criado_em DESC, id DESC;

SELECT chamado_id, autor_id, tipo_evento, status_anterior, status_novo,
       atendente_anterior_id, atendente_novo_id, criado_em
FROM historico_chamados
WHERE tipo_evento = 'ATRIBUICAO'
ORDER BY criado_em DESC, id DESC;
```

Registre no cartão o commit e a evidência da fila filtrada, confirmação, carteira e recusa da segunda tentativa. A conclusão pública depende do registro dessas evidências.

Para preparar uma conta fictícia **somente no desenvolvimento**, após cadastrá-la normalmente:

```sql
UPDATE usuarios
SET perfil = 'ATENDENTE', atualizado_em = CURRENT_TIMESTAMP, versao = versao + 1
WHERE email = 'atendente.demo@example.com';
```

Faça logout e login novamente. Use `ADMIN` em outra conta fictícia se precisar validar esse perfil. A administração de usuários pela interface pertence a outro cartão; o cadastro público não aceita escolher o perfil.

## Verificação realizada em 09/10/2026

- Angular: 63 testes aprovados em 17 arquivos e build de produção sem erros.
- Spring Boot: `mvn verify` aprovado; com a regressão adicional de atualização V3 → V4, 45 testes aprovados no total. Cobertura inclui permissões, filtros, paginação, concorrência determinística, concorrência HTTP e rollback quando o histórico falha.
- PostgreSQL 17 descartável e inicialmente vazio: migrations V1–V4 e dados de categorias de desenvolvimento aplicados automaticamente; Hibernate iniciou com `ddl-auto=validate`.
- API real: seis rodadas de duas requisições simultâneas produziram exatamente `200`/`409` em cada rodada. SQL confirmou um responsável, versão 1, data de atribuição, status `EM_ATENDIMENTO` e um evento de atribuição por chamado.
- API real: verificadas respostas `401`, `403`, `400` (incluindo deslocamento de página fora do limite), `404`, carteira pessoal, acesso de administrador e filtro de categoria inativa.
- Chromium: login, fila, cancelamento da confirmação, atribuição, carteira, lista vazia, erro de API e recuperação. Layout de 360 px sem rolagem horizontal.

Essas verificações técnicas não substituem o vídeo e o registro das evidências públicas no cartão. O banco habitual da aplicação foi preservado; as contas e chamados utilizados eram fictícios e isolados.
