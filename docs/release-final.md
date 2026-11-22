# ResolveAí v1.0.0 — Entrega acadêmica final

Versão proposta para a primeira release do repositório. O GitHub retornou a branch padrão `main`, repositório público e listas vazias de tags e releases na consulta desta entrega. A revisão remota verificada foi `de677e9904fab087e2a0d7949c7e3716af7f8719`. O código não inclui licença de distribuição explícita. A publicação da tag e da release aguarda autorização do responsável. [Repositório](https://github.com/RenanSouzadeOliveira/SOFTWARE_PRODUCT_IMPACTA_RESOLVEAI).

## Notas para publicação

O ResolveAí acompanha o atendimento desde a abertura pelo solicitante até a resolução e o fechamento. A fila permite assumir chamados e priorizar o trabalho. Comentários e histórico registram a evolução. Consultas filtradas, CSV e indicadores respeitam o perfil autenticado. A administração preserva vínculos ao desativar usuários e categorias. A interface inclui navegação móvel, foco por teclado e mensagens de erro sanitizadas.

O frontend usa Angular 21 e TypeScript. A API Spring Boot 3.5.16 usa Java 21, JWT, PostgreSQL 17 em UTF-8 e Flyway. Esta entrega disponibiliza uma carga acadêmica isolada com seis contas e 75 chamados, um teste de instalação limpa e a documentação para avaliação. [Instalação](../README.md) · [Arquitetura](architecture.md) · [Modelo de dados](modelo-entidade-relacionamento.md) · [API](api-ac2.md).

Consulte os resultados por camada, as limitações e os passos graváveis. [Validação final](validacao-final.md) · [Vídeo narrado](evidencias/apresentacao-final.webm) · [Roteiro de demonstração](demonstracao-final.md) · [Evidências de interface](evidencias/ux-p1/README.md) · [Cartões e commits](rastreabilidade-final.md).

## Checklist de publicação do responsável

1. Conferir no relatório os critérios técnicos e as pendências de privacidade. Identificar a revisão aprovada por `git rev-parse HEAD` e conferir a árvore limpa. O histórico de autoria já publicado contém um endereço pessoal. Confirmar a aceitação desse histórico antes de ampliar a distribuição.
2. Confirmar repositório, permissão de publicação e nome `v1.0.0`. Conferir com `git ls-remote origin refs/heads/main refs/tags/v1.0.0` que o remoto continua compatível e a tag está livre. Em caso de avanço remoto, revisar novamente a integração.
3. Após autorização, criar a tag anotada **no SHA verificado**, fazer push explícito de `main` e de `v1.0.0`. Criar a release com o título destas notas. Conferir anonimamente tag, commit, README, diagramas e evidências na revisão publicada.
4. Conferir o arquivo WebM já validado e o áudio sintético em português. Definir destino e permissão de acesso. Publicar somente com autorização, testar reprodução sem login e guardar a URL real.
5. Vincular commit, release, evidência e vídeo aos cartões correspondentes. Conferir como visitante o [quadro](https://trello.com/b/yDtytoca/resolvea%C3%AD-sistema-de-gest%C3%A3o-de-chamados) e o [cartão final](https://trello.com/c/wniJDZKv). Concluir o cartão apenas depois das verificações e da aprovação para a atualização externa.

| Recurso | Classificação desta rodada |
| --- | --- |
| Repositório GitHub e branch main de base | PUBLICAMENTE ACESSÍVEL |
| Quadro, 21 cartões e 29 commits históricos da matriz | PUBLICAMENTE ACESSÍVEL |
| Release, tag, documentação e evidências na revisão final publicada | AGUARDANDO PUBLICAÇÃO |
| Swagger do laboratório | PRIVADO POR DECISÃO, acessível em loopback |
| URL pública do vídeo final já gravado | AGUARDANDO PUBLICAÇÃO |
