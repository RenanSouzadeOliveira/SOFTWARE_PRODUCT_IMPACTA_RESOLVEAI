# Consolidação e evidências da AC2

Objetivo: demonstrar o ciclo completo em uma versão estável e reproduzível, sem antecipar funcionalidades da AC3. A validação técnica e a publicação das evidências são etapas separadas.

## Execução limpa

Requisitos: os do README, além de `curl` e `openssl`. Execute na raiz:

```bash
bash scripts/validar-ac2.sh
```

O roteiro executa testes/builds, cria PostgreSQL 17 vazio e API isolados, aplica V1–V6, valida autorização/concorrência/rollback e prepara dados fictícios nos estados ABERTO, EM_ATENDIMENTO, RESOLVIDO e FECHADO. Não lê seu `.env`, não substitui seus serviços e não apaga o volume habitual. Portas podem ser ajustadas por `AC2_DB_PORT`, `AC2_API_PORT` e `AC2_WEB_PORT`.

Para reaproveitar artefatos já testados nesta execução, existe `--sem-build`; ele não executa testes nem build e não é evidência suficiente sozinho.

## Interface e gravação local

```bash
bash scripts/validar-ac2.sh --com-navegador
```

Playwright e Chromium são necessários apenas para essa opção. No ambiente Codex, o módulo disponível é usado por padrão. Em outra máquina, configure uma instalação separada, sem alterar as dependências da aplicação:

```bash
npm install --prefix /tmp/resolveai-playwright playwright
/tmp/resolveai-playwright/node_modules/.bin/playwright install chromium
export AC2_PLAYWRIGHT_MODULE=/tmp/resolveai-playwright/node_modules/playwright/index.mjs
bash scripts/validar-ac2.sh --com-navegador
```

A gravação usa o navegador real com contas fictícias. Erros de interface controlados são simulados pelo roteiro e identificados como tal; as demais operações usam a API real. O vídeo é **local e silencioso**, não uma publicação automática.

Para praticar a apresentação com o ambiente ainda funcionando:

```bash
bash scripts/validar-ac2.sh --com-navegador --manter
```

Neste modo, a API utiliza `8080`, coerente com o Angular de desenvolvimento; se essa porta já estiver ocupada, o comando recusa a execução sem encerrar seu serviço. `--manter` não permite sobrescrever a porta da API por outra. No Linux, exige `nohup` e `setsid` para manter os processos independentes da sessão que executou o comando. A reescrita para uma porta diferente existe apenas na automação do navegador, não na apresentação manual.

Depois de sucesso, consulte o arquivo privado `ambiente.txt` indicado pelo comando. Ele informa URLs e como encerrar exatamente aquele ambiente. Contas e senha estão em `fixtures.json`, com acesso privado, e devem permanecer locais. Por padrão, o ambiente é removido e os relatórios são preservados em `/tmp`.

## Roteiro da apresentação

1. Apresente o problema: centralizar solicitações e tornar o atendimento rastreável.
2. Como solicitante, abra um chamado; mostre protocolo, Meus chamados e comentário inicial.
3. Como atendente, filtre a fila, assuma com confirmação e encontre o item na carteira.
4. Responda o comentário, altere a prioridade e resolva com justificativa.
5. Como solicitante, recarregue a conversa e reabra com justificativa.
6. Como atendente, resolva novamente; como solicitante, confirme e feche.
7. Mostre o histórico e a conversa somente leitura; demonstre o acesso de ADMIN e os bloqueios de terceiros.
8. Mostre a evidência de concorrência `200/409` e de rollback, sem dados parciais.

Não mostre `.env`, senha em texto claro, JWT nem o arquivo de fixtures durante a gravação.

## Verificações e evidências

| Camada | Evidência |
| --- | --- |
| Angular | Testes e build; fila, carteira, atualização, ações por estado, conversa, recuperação de erro e largura 360 px |
| API | Cadastro/login, participação, permissões dos três perfis, entradas inválidas e ciclo completo |
| PostgreSQL | Migrations desde vazio, um vencedor na atribuição, histórico único e rollback de falha real de persistência |
| Dados fictícios | Quatro estados preparados por endpoints reais; não por atualização direta de status |
| Documentação | README, [API](api-ac2.md), [modelo relacional](modelo-entidade-relacionamento.md) e [notas da release](release-ac2.md) |

O PostgreSQL arredonda timestamps para microssegundos; verificações de não alteração comparam o estado **persistido**, não a precisão de nanossegundos de um objeto Java recém-criado.

### Resultado técnico de 13/10/2026

- `mvn -o clean verify`: 71 testes, zero falhas/erros, JAR gerado. A reconstrução limpa eliminou artefatos antigos do diretório `target`.
- `npm test -- --watch=false`: 84 testes em 19 arquivos; `npm run build` aprovado.
- PostgreSQL 17 vazio: V1–V6 aplicadas e Hibernate iniciado com validação.
- Roteiro API: 55 requisições reais aprovadas, quatro estados preparados, disputa de atribuição `200/409`, um responsável/versão/evento, falha real de histórico com rollback do chamado e de ambos os históricos.
- Roteiro navegador: 14 checkpoints aprovados, ciclo completo e três perfis, persistência de comentários, recuperação de falhas controladas, prevenção de envio duplicado, texto HTML não executado e detalhe em 360 px.
- Vídeo silencioso e capturas gerados localmente, revisados no ambiente de execução. Não contêm fixtures nem tokens; a publicação continua sendo uma etapa independente.
- Modo `--manter` conferido após o encerramento do roteiro: API e Angular permaneceram disponíveis e o login funcionou no navegador sem reescrita de requisições.

Nenhum código de produção precisou ser alterado nesta consolidação. O incremento acrescenta roteiro executável, dados fictícios seguros, verificações e documentação de entrega. O ambiente de validação foi removido ao terminar; o PostgreSQL habitual permaneceu intacto.

## Trello e publicação

Quadro: [ResolveAí | Sistema de Gestão de Chamados](https://trello.com/b/yDtytoca/resolvea%C3%AD-sistema-de-gest%C3%A3o-de-chamados).

| Cartão | Commit funcional |
| --- | --- |
| [Fila e atribuição](https://trello.com/c/GW4vcGSa) | `e7cc691` |
| [Prioridade e ciclo de vida](https://trello.com/c/eNGZqxyJ) | `b92d369` |
| [Comentários](https://trello.com/c/u1M2iEOj) | `a065330` |
| [Consolidação e publicação](https://trello.com/c/8EFsu8tr) | Commit final desta consolidação |

Em 13/10/2026, as consultas anônimas confirmaram GitHub e Trello públicos. A branch pública ainda estava na AC1 antes do push autorizado; portanto, SHA local ou vídeo local isoladamente não comprova entrega pública.

Checklist final:

- [ ] Commit de consolidação publicado, preservando os três commits funcionais.
- [ ] Release pública aponta para esse commit e contém notas coerentes.
- [ ] Vídeo público corresponde à versão validada e não expõe credenciais.
- [ ] Links dos commits vinculados aos cartões funcionais.
- [ ] Links de commit, release e vídeo registrados no cartão final.
- [ ] Marcar Concluído somente após conferir todos os links sem autenticação.
