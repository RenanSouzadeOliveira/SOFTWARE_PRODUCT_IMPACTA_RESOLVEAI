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

## Validação

```bash
npm test -- --watch=false
npm run build
```

O primeiro comando executa os testes uma vez, em ambiente headless. O build de produção usa
o caminho relativo `/api`, permitindo que o front-end e a API sejam publicados sob a mesma
origem.
