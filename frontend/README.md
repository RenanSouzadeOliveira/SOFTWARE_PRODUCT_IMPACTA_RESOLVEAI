# ResolveAí — front-end

Base web do ResolveAí em Angular 21, com componentes standalone e TypeScript estrito.

## Requisitos

- Node.js 22
- npm 10 ou superior

## Execução local

```bash
npm ci
npm start
```

A aplicação fica disponível em `http://localhost:4200`. No modo de desenvolvimento, a URL
da API é `http://localhost:8080/api`.

## Configuração da API

As URLs ficam centralizadas em:

- `src/environments/environment.ts`: produção, com caminho relativo `/api`;
- `src/environments/environment.development.ts`: desenvolvimento local.

O contrato inicial esperado para `GET /api/health` é:

```json
{
  "status": "UP",
  "service": "resolveai-api",
  "timestamp": "2026-09-08T12:00:00Z"
}
```

## Fila e carteira de atendimento

As rotas `/atendimento` (fila) e `/atendimento/me` (carteira) são acessíveis a
`ATENDENTE` e `ADMIN`. Ambos os perfis entram na fila após o login. A carteira
consulta exclusivamente os chamados atribuídos à conta autenticada.

As listagens usam `GET /api/atendimento/fila` e `GET /api/atendimento/me`, com páginas
de 10 registros e filtros opcionais `prioridade` e `categoriaId`; a carteira também
aceita `status`. Filtros e página ficam na URL. A resposta contém `content`, `page`,
`size`, `totalElements` e `totalPages`.

O seletor consulta `GET /api/atendimento/categorias`, incluindo categorias inativas
para permitir filtrar chamados antigos. A abertura usa exclusivamente categorias ativas.

A ação de assumir requer confirmação e envia `PATCH /api/chamados/{id}/assumir`
com corpo vazio. A API obtém o responsável da sessão. Após sucesso, a fila é
atualizada e a interface oferece acesso à carteira. O conflito `409` informa que o
chamado deixou de estar disponível e também atualiza a fila. Requisições de listagem
obsoletas são canceladas ao mudar filtros; envios de atribuição duplicados são bloqueados.

## Validação

```bash
npm test -- --watch=false
npm run build
```

O primeiro comando executa os testes uma vez, em ambiente headless. O build de produção usa
o caminho relativo `/api`, permitindo que o front-end e a API sejam publicados sob a mesma
origem.
