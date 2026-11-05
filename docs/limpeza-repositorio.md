# Limpeza do repositório e reprodução dos builds — 05/11/2026

**READY FOR COMMIT.** Os comandos padrão passaram no checkout real, sem cópia de fontes, exclusões de compilação, seleção customizada de migrations ou override de esbuild. A implementação AC3 não foi reescrita.

## Inventário e preservação

| Área | Candidatos realocados | Análise |
| --- | ---: | --- |
| Fontes, configurações e documentação | 209 | 184 idênticos; 23 iguais às versões anteriores no Git; 2 vazios. Nenhuma mudança semântica nova a integrar. |
| Instalação de dependências frontend | 22.639 | Cópias idênticas, incluindo duas licenças cuja contraparte usa extensão em maiúsculas. Instalação anterior inteira preservada antes de `npm ci`. |
| Artefatos de build/cache | 194 | 75 idênticos, 38 diferentes e 81 sem contraparte de mesmo nome. Diretórios antigos preservados integralmente, incluindo esses estados gerados distintos. |
| **Total** | **23.042** | **22.898 idênticos; 63 com conteúdo diferente; 81 artefatos sem contraparte. Zero casos de código pendentes.** |

A auditoria anterior de 245 arquivos incluía 36 itens de cache; este inventário separou projeto, dependências e artefatos e ampliou a busca para os diretórios ignorados. Todos os candidatos estavam fora do índice Git: nenhum rastreado ou staged; entre os 209 do projeto, 205 eram untracked e 4 ignorados. O manifesto registra separadamente o estado da cópia e da contraparte canônica, SHA-256, caminho original, destino, classificação, tamanho e modo.

Backup **persistente e privado**, fora do repositório: pasta irmã `../RESOLVEAI_RECOVERY/2026-11-05_ac3_cleanup_01/`. `manifest.json` e `summary.json` descrevem a recuperação; `files/` guarda as cópias de projeto e `trees/` conserva integralmente `frontend/node_modules`, `backend/target`, `frontend/.angular` e `frontend/dist` anteriores. `RECUPERAR.md` orienta restauração sem sobrescrever trabalho atual. Nenhum backup ou segredo foi adicionado ao Git. Cada candidato foi verificado no destino por SHA-256; nada foi descartado.

A pasta de recuperação é protegida por modo 0700 porque inclui ambientes locais e instalações antigas. A recuperação deve copiar primeiro para uma pasta de revisão e conferir o hash; só devolver ao caminho original se ele não existir. Não restaurar árvores antigas por cima de builds/instalações novos sem preservá-los.

## Causas e correção

- **Maven:** fontes `(2).java` continham as mesmas classes públicas e eram descobertas normalmente. A realocação eliminou os conflitos; `backend/pom.xml` permaneceu intacto.
- **Angular:** `*.spec (2).ts` escapavam da exclusão normal de `*.spec.ts` no build da aplicação. As cópias foram realocadas; strict TypeScript, arquivos de teste, `tsconfig.app.json`, `tsconfig.spec.json` e `angular.json` permaneceram intactos.
- **Flyway:** as seis cópias SQL V1–V6 repetiam versões. Todas foram arquivadas, mantendo sete arquivos canônicos. O JAR foi inspecionado: cada versão 1–7 aparece exatamente uma vez. Nenhuma migration nem checksum canônico mudou; não houve `repair`.
- **esbuild:** binário e entradas `.bin` tinham perdido os metadados executáveis; o binário nativo estava em modo 0664 e `.bin/esbuild` era arquivo regular. Após arquivar a instalação completa, `npm ci` restaurou **o mesmo SHA-256 nativo**, modo 0775 e link simbólico normal em `.bin`. Sem chmod manual, sudo, permissões globais ou variável `ESBUILD_BINARY_PATH`.

O padrão `(2)` junto à perda de permissões e links é compatível com uma cópia/restauração de arquivos. Não foi possível atribuir sua geração a um programa específico. O Git não rastreava esses arquivos e não há evidência de geração pelos builds.

## Toolchain e comandos executados

Java 21, Maven 3.9.5, Node 24.22.1, npm **10.9.2** (versão de `packageManager`) e PostgreSQL 18. O ambiente desta sessão não fornecia node/npm/mvn no PATH; os binários instalados foram expostos no PATH, e npm 10.9.2 foi obtido do registro oficial com integridade SHA-512 verificada, instalado apenas em `tools/` da pasta privada de recuperação. Não houve mudança de versões/dependências do projeto, `package.json` ou `package-lock.json`.

No backend real:

```bash
cd backend
mvn -B -ntp clean verify
```

**PASS — 77 testes, zero falhas, erros ou skips; JAR gerado.** Inclui regressões AC1/AC2 de autenticação, abertura, consulta, atendimento, concorrência, status/prioridade e comentários, além da AC3.

No frontend real:

```bash
cd frontend
npm ci --no-audit --no-fund
npm run build
npm test -- --watch=false
```

**PASS — build de produção; 90 testes em 20 arquivos.** Todos os testes canônicos foram mantidos. Nenhum executável ou configuração foi substituído para rodar esses comandos.

No backend real, contra banco PostgreSQL descartável inicialmente vazio:

```bash
mvn -B -ntp -Dtest=PaginacaoIntegrationTest \
  -Dspring.datasource.url=jdbc:postgresql://127.0.0.1:55473/ac3_cleanup_fresh \
  -Dspring.datasource.driver-class-name=org.postgresql.Driver \
  -Dspring.datasource.username=usuario_de_teste \
  -Dspring.datasource.password=senha_de_teste test
```

**PASS — cinco testes**, com Flyway habilitado e descoberta padrão. Abrange os três perfis, conteúdo/total escopados, 45 registros em três páginas, datas iguais, sort, validação, limites, vazio, página fora do limite, comentários sem multiplicar counts e contrato OpenAPI.

## PostgreSQL e evidência acadêmica

- Instalação limpa pelo Maven/Spring Boot: V1→V7 aplicada normalmente, sem recursos duplicados.
- Upgrade pelo **mesmo JAR gerado no checkout real**: primeiro startup em banco isolado com `SPRING_FLYWAY_TARGET=6`; após inserir usuário, categoria e chamado fictícios, encerrou-se a instância. Segundo startup, sem target, validou as migrations e aplicou somente V7.
- Confirmados: sete entradas de histórico com sucesso, registro V6 preservado, três índices finais esperados e ausência dos dois índices substituídos. Checksums das sete versões iguais entre instalação limpa e upgrade. Nenhum banco de produção foi consultado/alterado.
- `node scripts/ac3-demonstracao.mjs`, usando `AC3_API_URL` do JAR isolado: **PASS**, 45 chamados fictícios em páginas 20/20/5, sem duplicatas/omissões no dataset estável, retorno à página anterior, tamanho 100, ordenação e sort inválido 400.

Os logs e comparações de checksums ficam em `logs/` do backup privado. Não houve nova gravação de navegador nesta etapa: as mudanças foram somente realocação/reinstalação e documentação; não há alteração de componentes ou regras AC1/AC2/AC3.

## Estado final e limites

- Zero candidatos `(2)` remanescentes no repositório, incluindo dependências e artefatos recriados.
- Fontes/configurações canônicas e migrations preservadas byte a byte. Índice staged e HEAD preservados.
- `git diff --check` aprovado; Git mostra as mudanças AC3 já existentes e a documentação desta limpeza, sem backups ou instalações.
- Nesta etapa, somente `README.md`, `docs/paginacao-chamados.md` e este documento tiveram conteúdo alterado/criado. As realocações detalhadas estão no manifesto fora do Git.
- Sem blockers confirmados. READY FOR COMMIT e demonstração acadêmica pelos comandos normais, com toolchain/dependências e PostgreSQL local disponíveis.

A paginação continua por offset: estabilidade foi verificada para dataset estável. Alterações concorrentes podem deslocar itens e totais entre requests; não há snapshot compartilhado entre páginas. Não houve implementação de cursor, commits, push, deploy ou alteração no Trello.
