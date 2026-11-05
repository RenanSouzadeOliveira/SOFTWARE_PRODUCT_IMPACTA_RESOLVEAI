package br.edu.impacta.resolveai.chamado;

import java.net.URI;
import br.edu.impacta.resolveai.chamado.dto.PaginaChamadosResponse;
import br.edu.impacta.resolveai.shared.pagination.PaginacaoChamados;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springframework.util.MultiValueMap;

import br.edu.impacta.resolveai.chamado.dto.AbrirChamadoRequest;
import br.edu.impacta.resolveai.chamado.dto.AlterarStatusRequest;
import br.edu.impacta.resolveai.chamado.dto.AlterarPrioridadeRequest;
import br.edu.impacta.resolveai.chamado.dto.ChamadoDetalheResponse;
import br.edu.impacta.resolveai.chamado.dto.ChamadoResponse;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.shared.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/chamados")
@SecurityRequirement(name = "bearerAuth")
public class ChamadoController {

    private final ChamadoService chamadoService;
    private final CicloChamadoService ciclo;
    private final OpcoesFiltrosChamados opcoes;

    public ChamadoController(ChamadoService chamadoService, CicloChamadoService ciclo, OpcoesFiltrosChamados opcoes) {
        this.chamadoService = chamadoService;
        this.ciclo = ciclo;
        this.opcoes = opcoes;
    }

    @PostMapping
    @Operation(
            summary = "Abrir chamado",
            description = "Cria um chamado ABERTO para o solicitante ativo autenticado. "
                    + "Status, protocolo, responsavel, solucao e datas sao controlados pelo servidor.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Chamado criado"),
            @ApiResponse(responseCode = "400", description = "Dados invalidos ou categoria inexistente/inativa",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, invalido ou de conta inativa",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil autenticado sem permissao",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Nao foi possivel reservar um protocolo unico",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ChamadoResponse> abrir(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @Valid @RequestBody AbrirChamadoRequest request) {
        ChamadoResponse response = chamadoService.abrir(principal, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    @Operation(summary = "Pesquisar chamados com filtros combinados",
            description = "Filtros AND: search (protocolo OU título OU descrição, parcial literal sem distinguir maiúsculas), status, prioridade, categoriaId (alias categoria), responsavel, dataInicial e dataFinal (AAAA-MM-DD, dias inteiros UTC). Solicitante: próprios; atendente: atribuídos; ADMIN: todos. Paginação padrão page=0,size=20,sort=criadoEm,desc; máximo 100 e desempate id DESC.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Página filtrada e autorizada"),
            @ApiResponse(responseCode = "400", description = "Filtros, paginação ou ordenação inválidos"),
            @ApiResponse(responseCode = "401", description = "Autenticação necessária")})
    @Parameters({
            @Parameter(name = "search", in = ParameterIn.QUERY, description = "Texto literal; espaços externos ignorados"),
            @Parameter(name = "status", in = ParameterIn.QUERY, schema = @Schema(implementation = StatusChamado.class)),
            @Parameter(name = "prioridade", in = ParameterIn.QUERY, schema = @Schema(implementation = PrioridadeChamado.class)),
            @Parameter(name = "categoriaId", in = ParameterIn.QUERY, description = "ID positivo; alias categoria"),
            @Parameter(name = "responsavel", in = ParameterIn.QUERY, description = "ID positivo do atendente"),
            @Parameter(name = "dataInicial", in = ParameterIn.QUERY, description = "AAAA-MM-DD, inclusiva UTC"),
            @Parameter(name = "dataFinal", in = ParameterIn.QUERY, description = "AAAA-MM-DD, inclusiva UTC"),
            @Parameter(name = "page", in = ParameterIn.QUERY, schema = @Schema(defaultValue = "0")),
            @Parameter(name = "size", in = ParameterIn.QUERY, schema = @Schema(defaultValue = "20", maximum = "100")),
            @Parameter(name = "sort", in = ParameterIn.QUERY, schema = @Schema(defaultValue = "criadoEm,desc"))
    })
    public PaginaChamadosResponse listar(@AuthenticationPrincipal UsuarioPrincipal principal,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> parameters) {
        return chamadoService.listar(principal, PaginacaoChamados.parse(parameters), FiltrosChamados.parse(parameters));
    }

    @GetMapping("/filtros")
    @Operation(summary = "Opções dos filtros de chamados", description = "Categorias e responsáveis (somente id e nome) dos chamados visíveis ao usuário, incluindo referências inativas. Não revela usuários sem chamados visíveis.")
    public OpcoesFiltrosChamados.Response filtros(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return opcoes.listar(principal);
    }

    @GetMapping("/me")
    @Operation(
            summary = "Listar meus chamados",
            description = "Página dos chamados do solicitante autenticado. page começa em 0; size padrão 20, máximo 100. Sort: criadoEm, atualizadoEm, titulo, status, prioridade ou id, seguido de asc ou desc. Padrão criadoEm,desc; desempate id DESC.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página do solicitante, inclusive vazia"),
            @ApiResponse(responseCode = "400", description = "Paginação ou ordenação inválida",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, invalido ou de conta inativa",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil autenticado sem permissao",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @Parameters({
            @Parameter(name = "page", in = ParameterIn.QUERY, description = "Índice a partir de zero", schema = @Schema(type = "integer", minimum = "0", defaultValue = "0")),
            @Parameter(name = "size", in = ParameterIn.QUERY, description = "Itens por página", schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20")),
            @Parameter(name = "sort", in = ParameterIn.QUERY, description = "campo,asc|desc; campos: criadoEm, atualizadoEm, titulo, status, prioridade, id. Desempate id DESC", schema = @Schema(defaultValue = "criadoEm,desc"))
    })
    public PaginaChamadosResponse listarMeus(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> parameters) {
        return chamadoService.listar(principal, PaginacaoChamados.parse(parameters), FiltrosChamados.parse(parameters));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Consultar detalhe do chamado",
            description = "Solicitante consulta o proprio chamado, atendente consulta o atribuido e ADMIN consulta qualquer chamado. "
                    + "Sem visibilidade retorna 404. Inclui acoes autorizadas e historico de status ordenado por data e id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalhe seguro do chamado"),
            @ApiResponse(responseCode = "401", description = "Token ausente, invalido ou de conta inativa",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil autenticado sem permissao",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Chamado inexistente ou sem visibilidade para o usuario",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ChamadoDetalheResponse detalharMeu(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ciclo.detalhar(id, principal);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Alterar status do chamado", description = "Responsavel ativo ou ADMIN resolve EM_ATENDIMENTO; "
            + "solicitante proprietario ou ADMIN reabre/fecha RESOLVIDO. Resolver e reabrir exigem justificativa 10..500 apos trim. "
            + "FECHADO e terminal. ABERTO->EM_ATENDIMENTO exige /assumir. Alteracao e historico sao atomicos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalhe atualizado com acoes e historico"),
            @ApiResponse(responseCode = "400", description = "Status ou justificativa invalidos", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Autenticacao ausente ou inativa", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao para a acao", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Chamado inexistente", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Transicao invalida ou alteracao concorrente", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ChamadoDetalheResponse alterarStatus(@PathVariable Long id,
            @AuthenticationPrincipal UsuarioPrincipal principal, @Valid @RequestBody AlterarStatusRequest request) {
        return ciclo.alterarStatus(id, principal, request);
    }

    @PatchMapping("/{id}/prioridade")
    @Operation(summary = "Alterar prioridade do chamado", description = "Responsavel ativo altera somente EM_ATENDIMENTO; "
            + "ADMIN altera qualquer estado nao terminal. Mesma prioridade retorna 409. Evento PRIORIDADE e atomico com a alteracao.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalhe atualizado"),
            @ApiResponse(responseCode = "400", description = "Prioridade invalida", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Autenticacao ausente ou inativa", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Chamado inexistente", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Fechado, mesma prioridade ou conflito concorrente", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ChamadoDetalheResponse alterarPrioridade(@PathVariable Long id,
            @AuthenticationPrincipal UsuarioPrincipal principal, @Valid @RequestBody AlterarPrioridadeRequest request) {
        return ciclo.alterarPrioridade(id, principal, request);
    }
}
