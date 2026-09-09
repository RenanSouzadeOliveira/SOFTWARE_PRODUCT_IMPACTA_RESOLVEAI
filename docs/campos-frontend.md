# Catálogo de campos para o front-end

Este é um catálogo de contratos consumidos ou planejados pelo Angular. Eles não são entidades JPA nem reproduzem nomes internos do Spring. Os contratos de autenticação, Minha conta, categorias ativas, abertura e consulta dos próprios chamados já estão implementados; os contratos dos cartões futuros continuam identificados como planejados.

## Convenções de tipos

```ts
type Id = number;                 // BIGINT serializado como número JSON neste contrato
type Versao = number;             // BIGINT de controle otimista
type IsoUtc = string;             // ISO 8601 UTC, por exemplo 2026-09-08T12:00:00Z

type Perfil = 'SOLICITANTE' | 'ATENDENTE' | 'ADMIN';
type StatusChamado = 'ABERTO' | 'EM_ATENDIMENTO' | 'RESOLVIDO' | 'FECHADO';
type PrioridadeChamado = 'BAIXA' | 'MEDIA' | 'ALTA';
type TipoEventoHistorico = 'CRIACAO' | 'ATRIBUICAO' | 'STATUS' | 'PRIORIDADE';
```

Campos `id` e `versao` são números no contrato JSON planejado, embora sejam `BIGINT` no PostgreSQL. Se o volume de identificadores puder ultrapassar o limite seguro de `number` do JavaScript, o contrato deverá migrar de forma coordenada para strings. Datas nunca devem ser interpretadas como horário local: o valor deve conter o sufixo UTC (`Z`).

## Usuário

Os contratos implementados neste cartão são `CadastroInput`, `LoginInput`, `LoginResponse`, `Usuario` e `AtualizacaoMinhaContaInput`. O cadastro aceita apenas `nome`, `email` e `senha`; Minha conta aceita apenas `nome`. Campos adicionais como `perfil`, `ativo` ou identificadores não concedem permissão nem alteram a conta.

### Entradas

| DTO | Campo | Tipo JSON/TypeScript | Uso e classificação |
| --- | --- | --- | --- |
| `CadastroUsuarioInput` | `nome` | `string` | Campo de formulário; obrigatório, até 120 caracteres e não vazio. |
| `CadastroUsuarioInput` | `email` | `string` | Campo de formulário; obrigatório, até 254 caracteres e único. |
| `CadastroUsuarioInput` | `senha` | `string` | Campo de formulário sensível; trafega somente na entrada protegida e nunca é devolvido. |
| `AtualizacaoUsuarioAdminInput` | `nome` | `string` | Campo de formulário administrativo; obrigatório e não vazio. |
| `AtualizacaoUsuarioAdminInput` | `email` | `string` | Campo de formulário administrativo; obrigatório e único. |
| `AtualizacaoUsuarioAdminInput` | `perfil` | `Perfil` | Campo de formulário administrativo; alteração autorizada somente ao administrador. |
| `AtualizacaoUsuarioAdminInput` | `ativo` | `boolean` | Campo de formulário administrativo para exclusão lógica/reativação. |
| `LoginInput` | `email` | `string` | Campo de formulário de autenticação. |
| `LoginInput` | `senha` | `string` | Campo de formulário sensível de autenticação; nunca é campo de saída. |

O cadastro público não recebe `perfil`: o back-end deve criar sempre `SOLICITANTE`. Nenhum formulário de usuário deve enviar identificadores de auditoria, timestamps ou `versao`; esses valores não são editáveis pela tela.

### Saídas

| DTO planejado | Campo | Tipo JSON/TypeScript | Uso e classificação |
| --- | --- | --- | --- |
| `UsuarioResumoDto`/`UsuarioDto` | `id` | `Id` | Somente leitura; identificador para referências e rotas. |
| `UsuarioResumoDto`/`UsuarioDto` | `nome` | `string` | Somente leitura em listas, chamados, comentários e histórico. |
| `UsuarioResumoDto`/`UsuarioDto` | `email` | `string` | Somente leitura conforme a permissão da tela. |
| `UsuarioResumoDto`/`UsuarioDto` | `perfil` | `Perfil` | Somente leitura para autorização de experiência; a segurança continua no back-end. |
| `UsuarioResumoDto`/`UsuarioDto` | `ativo` | `boolean` | Somente leitura para telas de administração e seleção. |
| `UsuarioDto` | `criadoEm` | `IsoUtc` | Somente leitura. |
| `UsuarioDto` | `atualizadoEm` | `IsoUtc` | Somente leitura. |
| `UsuarioDto` | `versao` | `Versao` | Campo interno de concorrência; não é exibido nem editado. |

O objeto de saída não expõe a credencial persistida. Respostas de autenticação devem seguir DTO próprio e não reutilizar uma entidade de usuário.

## Categoria

### Entradas

| DTO planejado | Campo | Tipo JSON/TypeScript | Uso e classificação |
| --- | --- | --- | --- |
| `CriacaoCategoriaInput` | `nome` | `string` | Campo de formulário administrativo; obrigatório, até 100 caracteres, não vazio e único. |
| `CriacaoCategoriaInput` | `descricao` | `string \| null` | Campo de formulário administrativo opcional, até 255 caracteres. Ausência e `null` devem ser normalizados conforme o contrato da API. |
| `AtualizacaoCategoriaInput` | `nome` | `string` | Campo de formulário administrativo; obrigatório, não vazio e único. |
| `AtualizacaoCategoriaInput` | `descricao` | `string \| null` | Campo de formulário administrativo opcional. |
| `AlteracaoAtivaCategoriaInput` | `ativa` | `boolean` | Campo de formulário/ação administrativa para exclusão lógica ou reativação. |

### Saídas

| DTO planejado | Campo | Tipo JSON/TypeScript | Uso e classificação |
| --- | --- | --- | --- |
| `CategoriaResumoDto`/`CategoriaDto` | `id` | `Id` | Somente leitura; valor usado como referência no formulário de chamado. |
| `CategoriaResumoDto`/`CategoriaDto` | `nome` | `string` | Somente leitura em seletores e chamados. |
| `CategoriaDto` | `descricao` | `string \| null` | Somente leitura ou edição apenas na tela administrativa. |
| `CategoriaResumoDto`/`CategoriaDto` | `ativa` | `boolean` | Somente leitura no seletor; somente categorias ativas devem ser oferecidas para abertura. |
| `CategoriaDto` | `criadoEm` | `IsoUtc` | Somente leitura. |
| `CategoriaDto` | `atualizadoEm` | `IsoUtc` | Somente leitura. |
| `CategoriaDto` | `versao` | `Versao` | Campo interno de concorrência; não é exibido nem editado. |

Mesmo que uma categoria apareça ativa no Angular, o serviço deve revalidar existência e `ativa = TRUE` no momento da abertura. A FK obrigatória da V1 não substitui essa validação.

## Chamado

### Entradas

| DTO planejado | Campo | Tipo JSON/TypeScript | Uso e classificação |
| --- | --- | --- | --- |
| `CriacaoChamadoInput` | `titulo` | `string` | Campo implementado; obrigatório, de 5 a 120 caracteres após `trim`. |
| `CriacaoChamadoInput` | `descricao` | `string` | Campo implementado; obrigatório, de 20 a 2.000 caracteres após `trim`. |
| `CriacaoChamadoInput` | `categoriaId` | `Id` | Campo de formulário (seletor); obrigatório e deve referenciar categoria ativa. |
| `CriacaoChamadoInput` | `prioridade` | `PrioridadeChamado` | Campo implementado; obrigatório e limitado a `BAIXA`, `MEDIA` ou `ALTA`. |
| `AtribuicaoChamadoInput` | `atendenteId` | `Id` | Campo de ação da operação de atendimento; o serviço valida perfil, atividade e concorrência. |
| `AlteracaoPrioridadeChamadoInput` | `prioridade` | `PrioridadeChamado` | Campo de ação/formulário de atendente autorizado. |
| `ResolucaoChamadoInput` | `solucao` | `string` | Campo de formulário ao resolver; o serviço deve preencher `resolvidoEm` e registrar histórico. |
| `ReaberturaChamadoInput` | `justificativa` | `string` | Campo de formulário ao solicitar reabertura; o serviço valida o estado e registra o evento aplicável. |
| `ComentarioCriacaoInput` | `conteudo` | `string` | Campo de formulário; obrigatório e não vazio. Comentários são imutáveis nesta versão. |

`CriacaoChamadoInput` deliberadamente não contém `solicitanteId`: o solicitante é obtido da sessão autenticada pelo back-end, nunca de um valor digitado ou oculto no Angular. Também não contém protocolo, status, atendente, solução ou timestamps. A abertura implementada aceita a prioridade informada, sempre usa `status = 'ABERTO'` e persiste `atendente = null`.

As entradas de transição não autorizam qualquer mudança arbitrária só por conter um enum. O serviço deve aplicar a máquina de estados, permissões e regras de preenchimento de datas. O fechamento por confirmação e demais ações podem receber DTOs específicos quando o endpoint for definido.

### Saída principal

| DTO | Campo | Tipo JSON/TypeScript | Uso e classificação |
| --- | --- | --- | --- |
| `ChamadoResumoDto`/`ChamadoDto` | `id` | `Id` | Somente leitura; identificador interno/rota. |
| `ChamadoResumoDto`/`ChamadoDto` | `protocolo` | `string` | Somente leitura; valor público único para consulta. |
| `ChamadoResumoDto`/`ChamadoDto` | `titulo` | `string` | Somente leitura em lista; editável apenas se houver caso de uso futuro explícito. |
| `ChamadoDto` | `descricao` | `string` | Somente leitura no detalhe após a criação. Não integra o resumo da listagem. |
| `ChamadoResumoDto`/`ChamadoDto` | `status` | `StatusChamado` | Somente leitura/estado; alterações passam por ações autorizadas. |
| `ChamadoResumoDto`/`ChamadoDto` | `prioridade` | `PrioridadeChamado` | Somente leitura em lista; alteração por ação autorizada. |
| `ChamadoDto` | `solicitante` | `UsuarioResumoDto` | Somente leitura no detalhe; projeção DTO da FK `solicitante_id`. |
| `ChamadoDto` | `atendente` | `UsuarioResumoDto \| null` | Somente leitura no detalhe; projeção DTO da FK opcional `atendente_id`. |
| `ChamadoResumoDto`/`ChamadoDto` | `categoria` | `CategoriaResumoDto` | Somente leitura; projeção DTO da FK obrigatória `categoria_id`. |
| `ChamadoDto` | `solucao` | `string \| null` | Somente leitura após resolução; exibida quando disponível. |
| `ChamadoDto` | `criadoEm` | `IsoUtc` | Somente leitura. |
| `ChamadoDto` | `atualizadoEm` | `IsoUtc` | Somente leitura. |
| `ChamadoDto` | `resolvidoEm` | `IsoUtc \| null` | Somente leitura; obrigatório no banco para status `RESOLVIDO`/`FECHADO`. |
| `ChamadoDto` | `fechadoEm` | `IsoUtc \| null` | Somente leitura; obrigatório no banco para status `FECHADO`. |
| `ChamadoDto` | `versao` | `Versao` | Campo interno de concorrência; não é editado pelo usuário. |

Na resposta de detalhe, `solicitante`, `atendente` e `categoria` são projeções pequenas para apresentação, nunca entidades JPA. O resumo omite descrição, solução e usuários relacionados, retornando apenas os dados necessários à lista. `atendente` é `null` na abertura.

`GET /api/chamados/me` não recebe identificador de solicitante e retorna `ChamadoResumoDto[]` em ordem decrescente de `criadoEm`. `GET /api/chamados/{id}` retorna `ChamadoDto` somente quando o chamado pertence ao solicitante autenticado; chamado inexistente ou alheio usa o mesmo `404`.

### Comentários e histórico exibidos no detalhe

Embora sejam registros próprios no schema, comentários e histórico são normalmente consumidos junto do detalhe do chamado. São somente saída (exceto o conteúdo de novo comentário):

```ts
interface ComentarioDto {
  id: Id;
  chamadoId: Id;
  autor: UsuarioResumoDto;
  conteudo: string;
  criadoEm: IsoUtc;
}

interface HistoricoChamadoDto {
  id: Id;
  chamadoId: Id;
  autor: UsuarioResumoDto;
  tipoEvento: TipoEventoHistorico;
  statusAnterior: StatusChamado | null;
  statusNovo: StatusChamado | null;
  prioridadeAnterior: PrioridadeChamado | null;
  prioridadeNova: PrioridadeChamado | null;
  atendenteAnterior: UsuarioResumoDto | null;
  atendenteNovo: UsuarioResumoDto | null;
  justificativa: string | null;
  criadoEm: IsoUtc;
}
```

`chamadoId` é somente leitura nessas respostas. Os campos de atendente no histórico são opcionais exatamente como as FKs da V1; a API deve preservar `null` quando não houver valor. O histórico não tem entrada de edição no Angular.

## Regras de integração e escopo

- Os nomes camelCase acima são a convenção de JSON planejada para os nomes `snake_case` persistidos; o mapeamento deve ficar nos DTOs/serialização do back-end.
- Campos de formulário são apenas os marcados como entrada. IDs, estados, autores, referências, timestamps e versões são somente leitura ou internos, conforme a tabela.
- O Angular pode esconder ações por perfil, mas não é a autoridade de autorização. O Spring Boot deve obter o usuário autenticado da sessão/token e validar cada caso de uso.
- Listagens futuras devem acrescentar metadados de paginação e filtros no contrato específico, sem misturar esses parâmetros aos DTOs de entidade.
- Este documento não cria rotas, controllers, serviços, entidades JPA ou migrations. Os DTOs e campos só se tornam contrato efetivo depois de implementação, testes e atualização do OpenAPI.
