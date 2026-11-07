# AC3 P1 — Exportar chamados filtrados para CSV

[Cartão AC3 P1 no Trello](https://trello.com/c/nYQWu9Ga)

## Auditoria e caminho escolhido

A listagem já usa `PaginacaoChamados.parse`, `FiltrosChamados.parse` e a composição de `VisibilidadeChamados.doUsuario(principal)` com `FiltrosChamados.specification()`. A exportação reutiliza essas implementações, o `ChamadoRepository.findAll(spec, pageable)` existente e seu entity graph de categoria/atendente. Não houve mudança na consulta paginada ou no seu contrato, nem nova dependência.

O Angular usa `consultaDaUrl` e `parametrosConsulta`; a exportação reutiliza o mesmo estado e serializador. O novo método binário do `ApiService` continua passando pelo interceptor de autenticação. Os erros JSON retornados como Blob são decodificados pelo serviço de chamados antes de chegar à interface.

Os índices existentes V1, V4, V7 e V8 cobrem as consultas por solicitante/responsável/status/categoria e a ordem data/ID. Nenhuma alteração de esquema, índice ou migration foi necessária. A busca parcial literal continua com o custo do mecanismo existente. Não foi encontrado grafo Graphify no repositório; não foi executada descoberta ampla.

## Contrato HTTP

```http
GET /api/chamados/exportacao?search=AC3-ACADEMICO-V1&status=EM_ATENDIMENTO&prioridade=ALTA&dataInicial=2026-09-01&dataFinal=2026-09-30&sort=criadoEm,asc
Authorization: Bearer <token>
```

| Parâmetro | Regra reutilizada |
| --- | --- |
| `search` | Busca literal parcial por protocolo OU título OU descrição, sem distinguir maiúsculas; ignora espaços externos. |
| `status` | ABERTO, EM_ATENDIMENTO, RESOLVIDO, FECHADO. |
| `prioridade` | BAIXA, MEDIA, ALTA. |
| `categoriaId` / `categoria` | ID positivo; aliases sem valores conflitantes. |
| `responsavel` | ID positivo; somente filtro adicional, nunca concede acesso. |
| `dataInicial`, `dataFinal` | AAAA-MM-DD, dias inteiros inclusivos em UTC; início <= fim. |
| `sort` | campo,asc ou campo,desc; campos criadoEm, atualizadoEm, titulo, status, prioridade, id. Padrão criadoEm,desc; desempate id DESC quando outro campo é usado. |
| `page`, `size` | Aceitos e validados como na listagem (page >= 0, size 1..100), mas não restringem a exportação. |

Filtros e parâmetros conhecidos repetidos são rejeitados pelos validadores existentes. Os filtros combinam com AND. Não há interpolação SQL de entrada do usuário.

Sucesso: `200`, `Content-Type: text/csv; charset=UTF-8`, `Content-Disposition: attachment; filename="chamados.csv"` e `Cache-Control: no-store`. O nome é fixo e não incorpora entrada do usuário. Sem registros: `200` com BOM e cabeçalho, sem linhas de dados. Filtros inválidos ou limite excedido: `400` JSON com mensagem pt-BR, sem anexo. Ausência de autenticação, token inválido/expirado ou conta inativa: `401`. Falhas inesperadas usam o tratamento global sanitizado `500`, antes de entregar um arquivo bem-sucedido.

## Formato e colunas aprovadas

UTF-8 com BOM para reconhecimento de acentos, separador `;`, todos os campos entre aspas duplas, aspas internas duplicadas e linhas encerradas por CRLF. Separadores e quebras de linha dentro de campos continuam dentro das aspas. O arquivo não usa linhas auxiliares `sep=`.

| Coluna | Conteúdo |
| --- | --- |
| Protocolo | Protocolo público do chamado. |
| Título | Título, com neutralização quando necessário. |
| Status | Enum de domínio preservado. |
| Prioridade | Enum de domínio preservado. |
| Categoria | Nome da categoria do registro autorizado. |
| Atendente responsável | Somente nome do atendente do registro visível; vazio se não atribuído. A identificação já é permitida no detalhe e nas opções de filtros de chamados visíveis. |
| Data de criação (UTC) | ISO-8601 UTC, por exemplo 2026-10-01T00:00:00Z; precisão disponível preservada. |

Não são exportados descrição, solução, solicitante, email, IDs internos, histórico, versão, hashes, senhas ou tokens. O texto da descrição pode participar da busca existente sem aparecer no arquivo.

## Autorização e fórmulas

A consulta sempre acrescenta o escopo do principal autenticado: SOLICITANTE vê os próprios chamados; ATENDENTE vê os atribuídos a ele; ADMIN vê todos. Um `responsavel` conflitante só reduz o conjunto. `solicitanteId` fornecido pelo cliente não altera o principal nem a visibilidade.

Todo campo passa pelo escape de CSV. Antes disso, o serializador verifica o primeiro caractere significativo após espaços Unicode, controles C0/C1 e caracteres de formato/invisíveis. Valores iniciados por `=`, `+`, `-`, `@` e suas variantes de largura completa, ou com TAB/CR/LF iniciais, recebem **`Texto: ` antes de todo o conteúdo original**. Exemplo: `  =1+2` vira `Texto:   =1+2`. O prefixo permanece visível e deliberadamente altera a representação de valores perigosos para impedir execução como fórmula. O banco permanece intacto.

A escolha de um prefixo alfabético não depende do reconhecimento de apóstrofos como escape. Aspas de CSV, sozinhas, não neutralizam fórmulas. A [referência OWASP](https://owasp.org/www-community/attacks/CSV_Injection) descreve também limitações de escapes após salvar/reabrir no Excel; por isso, não foi adotada proteção baseada apenas em aspas/apóstrofo.

## Limite e recursos

`CHAMADOS_EXPORTACAO_MAX_REGISTROS` configura `app.chamados.exportacao.max-registros`, padrão **10.000**. Configurações entre 1 e 100.000 são aceitas; fora desse intervalo a aplicação falha na inicialização. Aumentar o padrão exige dimensionar memória e duração da transação.

A primeira página de até 100 entidades fornece a contagem autorizada. Se exceder o limite, retorna erro antes da serialização e dos cabeçalhos de sucesso. A exportação lê páginas de 100, libera o contexto de persistência por lote e reutiliza o entity graph existente para evitar N+1 de categoria/atendente. Não existe consulta sem paginação ou coleção com todas as entidades. O arquivo codificado fica em memória, incluindo buffer e cópia final; o tamanho é limitado pela quantidade configurada e pelos limites de comprimento do esquema.

A transação read-only com isolamento REPEATABLE_READ mantém o mesmo snapshot PostgreSQL durante os lotes e contagem. A ordenação validada com desempate evita duplicações/omissões por empate. A resposta só é construída quando toda a serialização termina, permitindo erros JSON em vez de arquivo parcial de sucesso. Não há fila ou job assíncrono.

## Interface

Botão **Exportar CSV** em `/chamados` e `/consulta-chamados`, inclusive no estado vazio. Mostra **Exportando…**, bloqueia requisições duplicadas e informa erro com região de alerta. Filtros, página e ordenação da URL permanecem preservados. Usa download Blob com nome fixo `chamados.csv`, remove o link temporário e revoga sua URL. Não redefine a busca nem pede todas as páginas ao navegador.

O endpoint também atende ATENDENTE com o escopo já existente. A fila operacional e as telas de carteira de `/atendimento` não receberam botões: a fila tem escopo diferente do GET /api/chamados; nenhuma exportação deve sugerir um escopo que não corresponde ao arquivo.

## Validação

Em 07/11/2026, a versão isolada da exportação passou em 104 testes do backend e 119 testes do Angular. O empacotamento Maven e o build Angular de produção passaram.

Com PostgreSQL e Chrome, os arquivos de ADMIN e de dois SOLICITANTES continham 10, 45 e 15 registros. A página exibia cinco. Compare os protocolos e sua ordem com a listagem completa e confira a separação entre as contas solicitantes.

O LibreOffice Calc importou os arquivos de 10 e 45 registros com as sete colunas e o conteúdo preservado. Um arquivo gerado pelo serializador com 42 entradas potencialmente maliciosas produziu zero fórmulas com avaliação habilitada. O Calc normalizou retornos de carro isolados para quebras de linha nos casos de teste. A validação direta no Microsoft Excel permanece pendente. Em configurações regionais com outro separador, importe como UTF-8 com delimitador ponto e vírgula.

Para reproduzir, use contas e chamados fictícios. Em **Consultar chamados**, combine status, prioridade e período. Escolha a ordenação e um tamanho de página menor que o total filtrado. Baixe o CSV e confira a contagem e a sequência no Calc. Repita em **Meus chamados** com dois solicitantes e compare os conjuntos autorizados. Teste também busca vazia e excesso de limite em ambiente de teste.
