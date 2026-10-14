# AC2 — Fluxo completo de atendimento

Notas preparadas para a release pública da AC2. Este arquivo não comprova que uma release já foi criada; o endereço definitivo deve ser registrado no cartão após publicação.

## Incremento

- Fila filtrada e paginada, atribuição com confirmação e carteira pessoal.
- Máquina de estados com resolução, reabertura, fechamento e prioridade autorizados.
- Histórico de status e comentários persistentes e imutáveis, com autoria e data.
- Proteção contra atribuição concorrente e alterações parciais.
- Validação reproduzível em PostgreSQL vazio, massa fictícia e gravação técnica do navegador.

## Commits funcionais

- `e7cc691`: fila e atribuição segura.
- `b92d369`: transições, prioridade e histórico.
- `a065330`: comentários persistentes.

O commit de consolidação deve acompanhar esses três commits, preservando seus hashes. A release deve apontar para esse commit de consolidação, não para uma versão anterior da AC1.

## Artefatos e cuidados

Artefatos de build: JAR em `backend/target` e Angular em `frontend/dist`. A execução limpa exporta OpenAPI, relatórios sem credenciais, capturas e vídeo técnico silencioso. Esses artefatos podem ser revisados para publicação; **não publique `fixtures.json`, logs com dados privados, `.env` ou diretório inteiro de execução**.

## Limitações conhecidas

- Administração de usuários/categorias, consulta avançada e dashboard pertencem a entregas futuras.
- Contas ATENDENTE/ADMIN de demonstração são promovidas somente no PostgreSQL descartável, nunca por endpoint público de cadastro.
- A conversa atualiza por recarga, sem comunicação em tempo real.
- O vídeo automático não contém narração. Pode servir como demonstração técnica ou base para a apresentação narrada.

## Publicação

Publicar o commit preservando os commits existentes, criar uma release apontando para ele, disponibilizar o vídeo em endereço público e registrar os três links em [AC2 | P0 — Validar e publicar o fluxo de atendimento](https://trello.com/c/8EFsu8tr). Não marcar o cartão Concluído enquanto houver evidência pública pendente.
