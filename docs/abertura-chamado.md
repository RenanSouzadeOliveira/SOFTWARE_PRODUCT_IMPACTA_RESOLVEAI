# Abertura de chamado

## Fluxo implementado

Um usuário autenticado com perfil `SOLICITANTE` acessa `/chamados/novo`, seleciona uma categoria ativa e informa título, descrição e prioridade. O Angular impede envio duplicado e preserva os dados quando a API falha. Em caso de sucesso, a tela substitui o formulário por uma confirmação com o protocolo.

O Spring Security rejeita token ausente ou conta inativa antes do caso de uso. O back-end recarrega o solicitante a partir do identificador autenticado; nenhum identificador de usuário enviado no JSON participa da criação. A categoria é verificada como ativa dentro da transação. Todo chamado nasce `ABERTO`, sem atendente, solução ou datas de resolução/fechamento.

## Categorias disponíveis

```http
GET /api/categorias
Authorization: Bearer <token>
```

Resposta `200 OK`:

```json
[
  { "id": 1, "nome": "Acesso e autenticação" }
]
```

A lista contém somente categorias ativas, ordenadas por nome. Sem token válido, a API retorna `401`.

## Criar chamado

```http
POST /api/chamados
Authorization: Bearer <token>
Content-Type: application/json
```

Entrada:

```json
{
  "titulo": "Erro ao acessar o portal",
  "descricao": "Ao entrar no portal, a página exibe acesso negado.",
  "categoriaId": 1,
  "prioridade": "MEDIA"
}
```

O título possui de 5 a 120 caracteres e a descrição de 20 a 2.000, ambos medidos após a remoção de espaços externos. `categoriaId` deve ser positivo e `prioridade` aceita somente `BAIXA`, `MEDIA` ou `ALTA`.

Resposta `201 Created`:

```json
{
  "id": 10,
  "protocolo": "RA-xN4W9j8vR0uM6zK2bT5cQg",
  "titulo": "Erro ao acessar o portal",
  "descricao": "Ao entrar no portal, a página exibe acesso negado.",
  "status": "ABERTO",
  "prioridade": "MEDIA",
  "solicitante": { "id": 7, "nome": "Pessoa Solicitante" },
  "atendente": null,
  "categoria": { "id": 1, "nome": "Acesso e autenticação" },
  "solucao": null,
  "criadoEm": "2026-09-09T12:00:00Z",
  "atualizadoEm": "2026-09-09T12:00:00Z",
  "resolvidoEm": null,
  "fechadoEm": null
}
```

O cabeçalho `Location` aponta para `/api/chamados/{id}`. Campos adicionais como `protocolo`, `solicitanteId`, `atendenteId`, `status`, `solucao` e timestamps são ignorados e não alteram valores protegidos.

Erros usam o envelope comum da API:

- `400`: validação por campo ou categoria inexistente/inativa;
- `401`: token ausente, inválido ou pertencente a conta inativa;
- `403`: perfil diferente de `SOLICITANTE`;
- `409`: as tentativas limitadas de reservar um protocolo único foram esgotadas.

## Persistência e concorrência

O protocolo tem prefixo `RA-` e 128 bits aleatórios codificados em Base64 URL-safe. A constraint `uk_chamados_protocolo` garante unicidade no banco. Uma colisão é repetida em nova transação, até três tentativas, para que uma falha de unicidade não contamine a tentativa seguinte.

A V3 ajusta `titulo` para `VARCHAR(120)`, `descricao` para `VARCHAR(2000)` e acrescenta os limites após `TRIM`. Os índices necessários já são fornecidos pela V1 para protocolo, solicitante, status e categoria. O perfil `dev` também executa `db/devdata/R__seed_development_categories.sql`; esse dado fictício não faz parte de produção.

## Roteiro de evidência da AC1

1. Inicie PostgreSQL, API com perfil `dev` e Angular conforme o README.
2. Cadastre e autentique um solicitante.
3. Abra `/chamados/novo`, tente enviar campos curtos e mostre as mensagens de validação.
4. Corrija os campos, selecione categoria e prioridade e crie o primeiro chamado.
5. Registre o protocolo exibido, clique em “Abrir outro chamado” e crie um segundo.
6. Mostre que os protocolos são diferentes.
7. Consulte no PostgreSQL `protocolo`, `solicitante_id`, `atendente_id`, `status`, `prioridade` e `categoria_id`; confirme `ABERTO` e atendente nulo.
8. Anexe ao cartão o link do commit e o trecho do vídeo correspondente.

Consulta sugerida:

```sql
SELECT protocolo, solicitante_id, atendente_id, status, prioridade, categoria_id
FROM chamados
ORDER BY criado_em DESC
LIMIT 2;
```
