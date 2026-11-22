package br.edu.impacta.resolveai.atendimento;

import java.util.Map;
import java.util.List;
import br.edu.impacta.resolveai.atendimento.dto.CategoriaAtendimentoResponse;

import br.edu.impacta.resolveai.atendimento.dto.PaginaAtendimentoResponse;
import br.edu.impacta.resolveai.chamado.PrioridadeChamado;
import br.edu.impacta.resolveai.chamado.StatusChamado;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springframework.util.MultiValueMap;
import br.edu.impacta.resolveai.shared.pagination.PaginacaoChamados;

import br.edu.impacta.resolveai.shared.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/atendimento")
@SecurityRequirement(name = "bearerAuth")
public class AtendimentoController {

    private final AtendimentoService atendimentoService;

    public AtendimentoController(AtendimentoService atendimentoService) {
        this.atendimentoService = atendimentoService;
    }

    @GetMapping("/acesso")
    @Operation(
            summary = "Validar acesso ao atendimento",
            description = "Disponivel aos perfis ATENDENTE e ADMIN ativos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil de atendente autorizado"),
            @ApiResponse(responseCode = "401", description = "Token ausente, invalido ou de conta inativa",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil autenticado sem permissao",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Map<String, String> access() {
        return Map.of("message", "Acesso de atendente autorizado");
    }

    @GetMapping("/categorias")
    @Operation(summary = "Consultar categorias para filtros do atendimento",
            description = "ATENDENTE e ADMIN ativos consultam categorias ativas e inativas, "
                    + "ordenadas por nome e id, para localizar inclusive chamados de categorias desativadas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categorias com id, nome e indicador ativa"),
            @ApiResponse(responseCode = "401", description = "Token ausente, invalido ou conta inativa",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissao",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<CategoriaAtendimentoResponse> categorias(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return atendimentoService.categorias(principal);
    }

    @GetMapping("/fila")
    @Operation(summary = "Consultar fila de chamados disponiveis",
            description = "ATENDENTE e ADMIN ativos consultam somente ABERTO sem responsavel. "
                    + "Filtros AND search, status, prioridade, categoriaId (alias categoria), responsavel e datas UTC, sempre restritos a ABERTO sem responsável; filtros incompatíveis produzem página vazia. Paginacao no servidor e ordem padrão criadoEm DESC, id DESC; sort configurável. "
                    + "Categorias inativas permanecem visiveis nos chamados existentes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina da fila, inclusive vazia"),
            @ApiResponse(responseCode = "400", description = "Pagina, tamanho ou filtros invalidos",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, invalido ou conta inativa",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissao",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @Parameters({
            @Parameter(name = "search", in = ParameterIn.QUERY, description = "Texto parcial literal em protocolo, título ou descrição; espaços externos ignorados", schema = @Schema(type = "string")),
            @Parameter(name = "status", in = ParameterIn.QUERY, description = "Filtro AND de status", schema = @Schema(implementation = br.edu.impacta.resolveai.chamado.StatusChamado.class)),
            @Parameter(name = "prioridade", in = ParameterIn.QUERY, description = "Filtro AND de prioridade", schema = @Schema(implementation = br.edu.impacta.resolveai.chamado.PrioridadeChamado.class)),
            @Parameter(name = "categoriaId", in = ParameterIn.QUERY, description = "ID positivo da categoria; não combinar com alias conflitante", schema = @Schema(type = "integer", format = "int64", minimum = "1")),
            @Parameter(name = "categoria", in = ParameterIn.QUERY, description = "Alias de categoriaId; não combinar com valor conflitante", schema = @Schema(type = "integer", format = "int64", minimum = "1")),
            @Parameter(name = "responsavel", in = ParameterIn.QUERY, description = "ID positivo do atendente; aplicado dentro da visibilidade da rota", schema = @Schema(type = "integer", format = "int64", minimum = "1")),
            @Parameter(name = "dataInicial", in = ParameterIn.QUERY, description = "Criação a partir deste dia UTC (inclusivo); não posterior a dataFinal", schema = @Schema(type = "string", format = "date")),
            @Parameter(name = "dataFinal", in = ParameterIn.QUERY, description = "Criação até o final deste dia UTC (inclusivo)", schema = @Schema(type = "string", format = "date")),
            @Parameter(name = "page", in = ParameterIn.QUERY, description = "Índice a partir de zero; deslocamento page*size até 2147483647", schema = @Schema(type = "integer", minimum = "0", defaultValue = "0")),
            @Parameter(name = "size", in = ParameterIn.QUERY, description = "Itens por página", schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20")),
            @Parameter(name = "sort", in = ParameterIn.QUERY, description = "campo,asc|desc; campos: criadoEm, atualizadoEm, titulo, status, prioridade, id. Desempate id DESC", schema = @Schema(type = "string", defaultValue = "criadoEm,desc"))
    })
    public PaginaAtendimentoResponse fila(@AuthenticationPrincipal UsuarioPrincipal principal,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> parameters,
            @RequestParam(required = false) PrioridadeChamado prioridade,
            @Parameter(description = "Identificador positivo da categoria")
            @RequestParam(required = false) Long categoriaId) {
        return atendimentoService.listarFiltrados(principal, PaginacaoChamados.parse(parameters), br.edu.impacta.resolveai.chamado.FiltrosChamados.parse(parameters), false);
    }

    @GetMapping("/me")
    @Operation(summary = "Consultar minha carteira de atendimento",
            description = "Somente chamados atribuidos ao ATENDENTE ou ADMIN autenticado. "
                    + "Permite combinar search, status, prioridade, categoriaId (alias categoria), responsavel e datas UTC; ordem padrão criadoEm DESC, id DESC; sort configurável.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina da carteira, inclusive vazia"),
            @ApiResponse(responseCode = "400", description = "Pagina, tamanho ou filtros invalidos",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, invalido ou conta inativa",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissao",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @Parameters({
            @Parameter(name = "search", in = ParameterIn.QUERY, description = "Texto parcial literal em protocolo, título ou descrição; espaços externos ignorados", schema = @Schema(type = "string")),
            @Parameter(name = "status", in = ParameterIn.QUERY, description = "Filtro AND de status", schema = @Schema(implementation = br.edu.impacta.resolveai.chamado.StatusChamado.class)),
            @Parameter(name = "prioridade", in = ParameterIn.QUERY, description = "Filtro AND de prioridade", schema = @Schema(implementation = br.edu.impacta.resolveai.chamado.PrioridadeChamado.class)),
            @Parameter(name = "categoriaId", in = ParameterIn.QUERY, description = "ID positivo da categoria; não combinar com alias conflitante", schema = @Schema(type = "integer", format = "int64", minimum = "1")),
            @Parameter(name = "categoria", in = ParameterIn.QUERY, description = "Alias de categoriaId; não combinar com valor conflitante", schema = @Schema(type = "integer", format = "int64", minimum = "1")),
            @Parameter(name = "responsavel", in = ParameterIn.QUERY, description = "ID positivo do atendente; aplicado dentro da visibilidade da rota", schema = @Schema(type = "integer", format = "int64", minimum = "1")),
            @Parameter(name = "dataInicial", in = ParameterIn.QUERY, description = "Criação a partir deste dia UTC (inclusivo); não posterior a dataFinal", schema = @Schema(type = "string", format = "date")),
            @Parameter(name = "dataFinal", in = ParameterIn.QUERY, description = "Criação até o final deste dia UTC (inclusivo)", schema = @Schema(type = "string", format = "date")),
            @Parameter(name = "page", in = ParameterIn.QUERY, description = "Índice a partir de zero; deslocamento page*size até 2147483647", schema = @Schema(type = "integer", minimum = "0", defaultValue = "0")),
            @Parameter(name = "size", in = ParameterIn.QUERY, description = "Itens por página", schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20")),
            @Parameter(name = "sort", in = ParameterIn.QUERY, description = "campo,asc|desc; campos: criadoEm, atualizadoEm, titulo, status, prioridade, id. Desempate id DESC", schema = @Schema(type = "string", defaultValue = "criadoEm,desc"))
    })
    public PaginaAtendimentoResponse carteira(@AuthenticationPrincipal UsuarioPrincipal principal,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> parameters,
            @RequestParam(required = false) PrioridadeChamado prioridade,
            @Parameter(description = "Identificador positivo da categoria")
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) StatusChamado status) {
        return atendimentoService.listarFiltrados(principal, PaginacaoChamados.parse(parameters), br.edu.impacta.resolveai.chamado.FiltrosChamados.parse(parameters), true);
    }

    @GetMapping("/todos")
    @Operation(summary = "Consultar todos os chamados como administrador",
            description = "Exclusivo de ADMIN ativo. Inclui todos os solicitantes, responsaveis e estados, "
                    + "com busca e filtros AND de status, prioridade, categoriaId, responsavel e datas UTC. Paginação AC3: page=0, size=20, máximo 100; sort=criadoEm,desc com desempate id DESC.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina de todos os chamados, inclusive vazia"),
            @ApiResponse(responseCode = "400", description = "Pagina, tamanho ou filtros invalidos",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, invalido ou conta inativa",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil diferente de ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @Parameters({
            @Parameter(name = "search", in = ParameterIn.QUERY, description = "Texto parcial literal em protocolo, título ou descrição; espaços externos ignorados", schema = @Schema(type = "string")),
            @Parameter(name = "status", in = ParameterIn.QUERY, description = "Filtro AND de status", schema = @Schema(implementation = br.edu.impacta.resolveai.chamado.StatusChamado.class)),
            @Parameter(name = "prioridade", in = ParameterIn.QUERY, description = "Filtro AND de prioridade", schema = @Schema(implementation = br.edu.impacta.resolveai.chamado.PrioridadeChamado.class)),
            @Parameter(name = "categoriaId", in = ParameterIn.QUERY, description = "ID positivo da categoria; não combinar com alias conflitante", schema = @Schema(type = "integer", format = "int64", minimum = "1")),
            @Parameter(name = "categoria", in = ParameterIn.QUERY, description = "Alias de categoriaId; não combinar com valor conflitante", schema = @Schema(type = "integer", format = "int64", minimum = "1")),
            @Parameter(name = "responsavel", in = ParameterIn.QUERY, description = "ID positivo do atendente; aplicado dentro da visibilidade da rota", schema = @Schema(type = "integer", format = "int64", minimum = "1")),
            @Parameter(name = "dataInicial", in = ParameterIn.QUERY, description = "Criação a partir deste dia UTC (inclusivo); não posterior a dataFinal", schema = @Schema(type = "string", format = "date")),
            @Parameter(name = "dataFinal", in = ParameterIn.QUERY, description = "Criação até o final deste dia UTC (inclusivo)", schema = @Schema(type = "string", format = "date")),
            @Parameter(name = "page", in = ParameterIn.QUERY, description = "Índice a partir de zero; deslocamento page*size até 2147483647", schema = @Schema(type = "integer", minimum = "0", defaultValue = "0")),
            @Parameter(name = "size", in = ParameterIn.QUERY, description = "Itens por página", schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20")),
            @Parameter(name = "sort", in = ParameterIn.QUERY, description = "campo,asc|desc; campos: criadoEm, atualizadoEm, titulo, status, prioridade, id. Desempate id DESC", schema = @Schema(type = "string", defaultValue = "criadoEm,desc"))
    })
    public PaginaAtendimentoResponse todos(@AuthenticationPrincipal UsuarioPrincipal principal,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> parameters,
            @RequestParam(required = false) PrioridadeChamado prioridade,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) StatusChamado status) {
        return atendimentoService.todos(principal, PaginacaoChamados.parse(parameters),
                br.edu.impacta.resolveai.chamado.FiltrosChamados.parse(parameters));
    }
}
