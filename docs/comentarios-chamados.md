# Comunicação por comentários — AC2

Solicitante proprietário, atendente atribuído e ADMIN podem consultar e comentar um chamado. Antes da atribuição, apenas o proprietário e ADMIN participam. Um chamado `FECHADO` mantém a conversa disponível para leitura, sem aceitar novos comentários.

## Contratos e autorização

Todas as operações exigem token de uma conta ativa.

```http
GET /api/chamados/{id}/comentarios
```

Resposta `200`:

```json
{
  "content": [
    {
      "id": 1,
      "conteudo": "O acesso continua apresentando a mensagem de erro.",
      "autor": { "id": 1, "nome": "Solicitante fictício", "perfil": "SOLICITANTE" },
      "criadoEm": "2026-10-09T12:00:00Z"
    }
  ],
  "podeComentar": true
}
```

A consulta retorna a conversa em ordem crescente de `criadoEm`, com desempate por `id`. `podeComentar` orienta a interface e é falso quando o chamado está fechado. A API revalida a permissão no envio, mesmo que a tela ainda mostre uma informação anterior ao fechamento.

```http
POST /api/chamados/{id}/comentarios
Content-Type: application/json

{"conteudo":"A configuração foi corrigida. Pode testar novamente?"}
```

Resposta `201`: o comentário criado, no mesmo formato de um item de `content`.

- O corpo aceita o conteúdo; autor e data são definidos pelo servidor, nunca pelo cliente.
- O texto deve ter de 1 a 2.000 caracteres após `trim`, com a mesma remoção de espaços externos Unicode no Angular e na API.
- A resposta contém apenas identificador, texto, nome/perfil do autor e data. Não expõe e-mail, credenciais ou entidades JPA.
- Nome e perfil são consultados pela FK do autor e representam os dados atuais da conta; não há cópia do cadastro nem snapshot histórico de perfil na tabela de comentários.
- Conteúdo é apresentado como texto, não como HTML.

| Resposta | Condição |
| --- | --- |
| `400` | Conteúdo inválido ou identificador inválido |
| `401` | Token ausente/inválido ou conta inativa |
| `403` | Tentativa de enviar sem participar do chamado |
| `404` | Chamado inexistente; também consulta sem participação |
| `409` | Tentativa de comentar um chamado fechado |

Não existem operações públicas de edição ou exclusão. Métodos não suportados são recusados e o conteúdo existente permanece intacto.

## Banco e consistência

A tabela `comentarios` foi criada na V1. A migration V6 a evolui, sem recriá-la: alinha a validação do texto normalizado e substitui o índice anterior por `(chamado_id, criado_em, id)` para ordenação estável. As FKs para chamado e autor continuam impedindo registros órfãos.

Comentários legados válidos são normalizados por `TRIM` SQL (espaço ASCII); conteúdo Unicode legado é preservado. Texto incompatível com as constraints não é truncado nem apagado: a migration falha para permitir correção explícita. Novos comentários usam a mesma normalização Unicode na API e no Angular. Cada comentário tem um conteúdo imutável e um instante UTC.

O envio bloqueia a linha do chamado durante a transação para ser serializado com um fechamento concorrente. Se o fechamento já foi concluído, o comentário é recusado; se o comentário foi aceito antes, ele permanece na conversa após o fechamento. Comentar não altera status, prioridade, versão, datas do chamado nem cria eventos de transição.

## Interface

O detalhe oferece conversa com autor, perfil e data, além de estados de carregamento, vazio e erro recuperável. O formulário valida o texto e bloqueia envio duplicado. Em falha, o conteúdo é preservado para nova tentativa. Após sucesso, a conversa é atualizada; após conflito de fechamento, a permissão e o detalhe são recarregados. Não há polling nem atualização em tempo real: recarregar a conversa busca os novos comentários.

## Roteiro de evidência

1. Como solicitante, abra um chamado e escreva uma mensagem antes da atribuição.
2. Tente consultar/enviar como outro solicitante ou atendente não atribuído; confirme a recusa sem conteúdo exposto.
3. Assuma como atendente, leia a mensagem e responda. Volte ao solicitante, recarregue e confira a resposta com autor, perfil e data.
4. Tente texto vazio e texto acima de 2.000 caracteres; confira `400` na API e validação na tela.
5. Resolva e feche o chamado. Confira que ambos ainda leem a conversa, mas não podem enviar.
6. Confira a persistência:

```sql
SELECT c.id, c.chamado_id, c.autor_id, u.nome, u.perfil,
       c.conteudo, c.criado_em
FROM comentarios c
JOIN usuarios u ON u.id = c.autor_id
ORDER BY c.chamado_id, c.criado_em, c.id;
```

Registre o commit e o vídeo no cartão. Os dados de demonstração devem ser fictícios.

## Verificação integrada em 09/10/2026

- Angular: 84 testes em 19 arquivos e build de produção aprovados.
- Spring Boot: `mvn -o verify -q` aprovado, 71 testes sem falhas ou erros e JAR executável gerado. Inclui autorização, validação Unicode, concorrência com fechamento, imutabilidade e atualização da migration sobre dados legados.
- PostgreSQL 17 inicialmente vazio: migrations V1–V6 e categorias de desenvolvimento aplicadas; Hibernate iniciou com validação do schema.
- API real: conversa de solicitante, atendente e ADMIN com autor definido pelo token, perfil e data UTC; mensagens persistiram após nova consulta.
- Verificados `401`, consulta sem participação `404`, envio sem participação `403`, texto vazio/Unicode invisível/acima do limite `400`, e envio após fechamento `409`.
- Ordem estável por data/ID comprovada com timestamps empatados. Tentativas de edição e exclusão foram recusadas sem alteração do conteúdo.
- Publicar não alterou status, versão, datas ou histórico do chamado. Um envio concorrente aceito antes do fechamento permaneceu na conversa; envio posterior foi recusado.
- Chromium: conversa entre duas contas, recarga, metadados, conteúdo semelhante a HTML exibido como texto, rascunho preservado após erro, recuperação da consulta e conflito de fechamento que tornou a tela somente leitura. Layout em 360 px sem rolagem horizontal.

O banco e os dados fictícios utilizados eram descartáveis; o banco habitual foi preservado. O vídeo e o registro público no cartão continuam sendo evidências de entrega separadas.
