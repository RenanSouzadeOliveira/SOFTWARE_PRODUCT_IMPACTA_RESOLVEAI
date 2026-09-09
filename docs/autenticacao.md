# Autenticação e autorização

O ResolveAí usa autenticação stateless com JWT assinado por HMAC SHA-256. O segredo nunca faz parte do artefato: ele é fornecido por `JWT_SECRET` e deve ter pelo menos 32 bytes. A duração do token é configurada por `JWT_EXPIRATION`, no formato ISO 8601, com `PT2H` como valor local sugerido.

O token contém o identificador do usuário no `subject`, o perfil na claim `perfil`, o emissor, a emissão e a expiração. A API não confia apenas nas claims: a cada requisição autenticada, ela consulta o usuário e rejeita tokens de contas inexistentes ou inativas.

## Contratos HTTP

### Cadastro público

```http
POST /api/auth/register
Content-Type: application/json

{
  "nome": "Maria da Silva",
  "email": "Maria@Example.com",
  "senha": "senha-segura"
}
```

A resposta `201 Created` contém uma projeção segura do usuário. O e-mail é persistido como `maria@example.com` e o perfil é sempre `SOLICITANTE`. Campos adicionais como `perfil` não alteram essa regra. E-mail já cadastrado retorna `409 Conflict` sem revelar senha ou hash.

### Login

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "maria@example.com",
  "senha": "senha-segura"
}
```

A resposta `200 OK` contém `accessToken`, `tokenType`, `expiresIn` e `usuario`. Credenciais incorretas ou uma conta inativa retornam a mesma resposta genérica `401 Unauthorized`.

### Minha conta

```http
GET /api/usuarios/me
Authorization: Bearer <token>
```

```http
PATCH /api/usuarios/me
Authorization: Bearer <token>
Content-Type: application/json

{
  "nome": "Maria Souza"
}
```

O usuário pode alterar apenas o próprio nome. Perfil, situação, e-mail, senha, timestamps e versão não fazem parte desse comando.

### Verificação do perfil de atendente

```http
GET /api/atendimento/acesso
Authorization: Bearer <token>
```

Somente `ATENDENTE` recebe `200 OK`. Um usuário autenticado com outro perfil recebe `403 Forbidden`; ausência, expiração ou adulteração do token retorna `401 Unauthorized`.

## Comportamento no Angular

- O token fica em `sessionStorage` e é removido no logout.
- O interceptor envia `Authorization: Bearer` somente para a API e limpa a sessão diante de `401` em uma chamada protegida.
- A guarda de autenticação protege `Minha conta`; a guarda de perfil também protege a área de atendimento.
- As guardas melhoram a navegação, mas a autorização definitiva permanece no Spring Security.

## Roteiro de evidência

1. Subir PostgreSQL, API e Angular conforme o README.
2. Cadastrar um visitante enviando, inclusive, `"perfil": "ADMIN"` pela ferramenta HTTP e conferir que a resposta mantém `SOLICITANTE`.
3. Tentar cadastrar novamente o mesmo e-mail com caixa diferente e conferir o `409`.
4. Entrar pela tela, abrir `Minha conta`, alterar o nome e sair.
5. Entrar novamente e conferir o nome persistido.
6. Remover o token e tentar `GET /api/usuarios/me`, conferindo o `401`.
7. Usar o token do solicitante em `GET /api/atendimento/acesso`, conferindo o `403`.
8. Registrar no cartão o commit público e o intervalo correspondente do vídeo da AC1.
