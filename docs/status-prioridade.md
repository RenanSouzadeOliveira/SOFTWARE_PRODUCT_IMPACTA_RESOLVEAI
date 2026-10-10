# Status, prioridade e histórico — AC2

A API centraliza as transições de estado. A interface usa as ações autorizadas retornadas pelo servidor; esconder um botão não substitui a autorização no back-end.

## Fluxo permitido

| Estado atual | Próximo estado | Quem pode executar | Entrada |
| --- | --- | --- | --- |
| `ABERTO` | `EM_ATENDIMENTO` | ATENDENTE ou ADMIN ao assumir | Endpoint de atribuição, sem escolher responsável |
| `EM_ATENDIMENTO` | `RESOLVIDO` | Atendente responsável ou ADMIN | Justificativa de 10 a 500 caracteres |
| `RESOLVIDO` | `EM_ATENDIMENTO` | Solicitante proprietário ou ADMIN | Justificativa de 10 a 500 caracteres |
| `RESOLVIDO` | `FECHADO` | Solicitante proprietário ou ADMIN | Confirmação |
| `FECHADO` | Nenhum | Nenhum | Estado terminal |

A prioridade pode ser alterada pelo responsável durante `EM_ATENDIMENTO` e por ADMIN em qualquer estado não fechado. Solicitantes e outros atendentes não podem alterá-la. Informar a prioridade já vigente retorna `409`, sem gerar evento redundante. Essa política explicita a permissão de atualização de prioridade não detalhada no cartão.

Resolver registra a justificativa como solução e preenche `resolvido_em`. Reabrir preserva o responsável e a data de atribuição, mas limpa a solução e as datas de resolução/fechamento; a justificativa anterior permanece no histórico. Fechar preserva a solução e registra `fechado_em`.

## Contratos

As operações exigem token de uma conta ativa. Responsável, solicitante, autor, datas e versão nunca vêm do corpo da requisição.

```http
PATCH /api/chamados/{id}/status
Content-Type: application/json

{"status":"RESOLVIDO","justificativa":"A configuração de acesso foi corrigida e validada."}
```

Para reabrir, envie `status: "EM_ATENDIMENTO"` e a justificativa. Para confirmar a resolução, envie `status: "FECHADO"`. O endpoint de status não permite substituir a ação de assumir um chamado aberto.

```http
PATCH /api/chamados/{id}/prioridade
Content-Type: application/json

{"prioridade":"ALTA"}
```

As duas operações retornam `200` com o detalhe atualizado. O contrato de `GET /api/chamados/{id}` mantém os campos anteriores e acrescenta:

```json
{
  "atribuidoEm": "2026-10-09T12:00:00Z",
  "acoesPermitidas": ["RESOLVER", "ALTERAR_PRIORIDADE"],
  "historicoStatus": [
    {
      "id": 1,
      "statusAnterior": "ABERTO",
      "statusNovo": "EM_ATENDIMENTO",
      "autor": {"id": 2, "nome": "Atendente fictício"},
      "criadoEm": "2026-10-09T12:00:00Z",
      "observacao": null
    }
  ]
}
```

Esse exemplo mostra apenas os novos campos, não a resposta completa. As ações possíveis são `RESOLVER`, `REABRIR`, `FECHAR` e `ALTERAR_PRIORIDADE`. O histórico está ordenado por data e identificador crescentes e usa DTOs, sem expor entidades ou credenciais.

O solicitante consulta apenas seu próprio detalhe, o atendente apenas os chamados atribuídos a ele e ADMIN pode consultar qualquer detalhe. Consulta inexistente ou sem visibilidade retorna `404`. As listagens dos solicitantes e a carteira pessoal mantêm o escopo anterior.

| Resposta | Significado |
| --- | --- |
| `400` | Campo inválido, enum desconhecido ou justificativa fora do limite após trim |
| `401` | Autenticação ausente, inválida ou conta inativa |
| `403` | Usuário sem permissão para executar a operação |
| `404` | Chamado inexistente; também detalhe sem visibilidade |
| `409` | Transição inválida, estado terminal, prioridade repetida ou atualização concorrente |

## Persistência

A migration V5 cria `historico_status` com FKs para chamado e autor, estados anterior/novo, data UTC e observação. Ela preserva transições comprovadas no histórico legado, com autor e data originais, sem inventar eventos a partir do estado atual. Cada nova transição, incluindo assumir, grava exatamente um evento nessa tabela na mesma transação da atualização do chamado. A versão otimista impede que operações concorrentes sobrescrevam alterações; um conflito reverte o evento.

`historico_chamados` mantém os eventos próprios de atribuição e prioridade. Um evento de atribuição e um evento de status descrevem aspectos diferentes da mesma operação; não há duplicação de eventos de status na tabela nova.

## Interface e validação

- O detalhe apresenta status, prioridade, solução e histórico.
- Resolver/reabrir exigem justificativa validada; fechar e alterar prioridade exigem confirmação.
- Operações em andamento bloqueiam envio duplicado. Falhas permitem nova tentativa, e conflitos atualizam o detalhe para não conservar ações obsoletas.
- Atendentes acessam o detalhe a partir de Meus atendimentos; ADMIN também pode abrir detalhes da fila. O solicitante mantém o acesso por Meus chamados.

Para demonstrar o cartão:

1. Abra um chamado como solicitante; tente `ABERTO → FECHADO` como ADMIN e confirme `409`, sem alteração nem histórico.
2. Assuma como atendente; tente resolver com outro atendente e confirme `403`.
3. Altere a prioridade e resolva com justificativa. Confira a data e a solução.
4. Entre como solicitante, reabra com justificativa e confirme a permanência do responsável.
5. Resolva novamente como responsável; confirme a resolução como solicitante para fechar.
6. Tente outra transição no chamado fechado e confirme `409`.
7. Confira a persistência:

```sql
SELECT id, protocolo, status, prioridade, atendente_id,
       atribuido_em, resolvido_em, fechado_em, versao
FROM chamados
ORDER BY criado_em DESC, id DESC;

SELECT chamado_id, status_anterior, status_novo, autor_id, criado_em, observacao
FROM historico_status
ORDER BY chamado_id, criado_em, id;

SELECT chamado_id, autor_id, prioridade_anterior, prioridade_nova, criado_em
FROM historico_chamados
WHERE tipo_evento = 'PRIORIDADE'
ORDER BY criado_em, id;
```

Registre o commit e o vídeo no cartão. A verificação técnica não substitui as evidências públicas de entrega.

## Verificação integrada realizada em 09/10/2026

- Angular: 74 testes aprovados em 17 arquivos; build de produção aprovado.
- Spring Boot: `mvn -o verify -q` aprovado, com 58 testes, sem falhas ou erros, e JAR executável gerado. Testes incluem máquina de estados, autorização, concorrência determinística, rollback de status/prioridade e atualização do histórico legado.
- PostgreSQL 17 inicialmente vazio: migrations V1–V5 e categorias de desenvolvimento aplicadas automaticamente; Hibernate iniciou validando o schema.
- API real: ciclo atribuir → resolver → reabrir → resolver → fechar com cinco eventos de status, um por transição. Conferidas datas, solução, responsável e permissões por perfil.
- Requisições inválidas: transição pulada e estado terminal retornaram `409`; justificativas inválidas retornaram `400`; outro atendente não alterou o chamado (`403`) nem consultou o detalhe (`404`).
- Concorrência de resolução: uma resposta `200`, outra `409`, um evento válido e nenhum responsável sobrescrito.
- Falha controlada de gravação de histórico no PostgreSQL: resposta `500` sem alteração de status, versão ou histórico, comprovando o rollback transacional.
- Chromium: prioridade, cancelamento, justificativa curta, erro recuperável, resolução, reabertura, nova resolução e fechamento; ações por perfil e linha do tempo corretas. Layout móvel de 360 px sem rolagem horizontal.

Os dados de validação foram fictícios e isolados do banco habitual. O vídeo e as evidências públicas do cartão devem ser registrados separadamente.
