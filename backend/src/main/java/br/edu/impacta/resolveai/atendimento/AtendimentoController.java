package br.edu.impacta.resolveai.atendimento;

import java.util.Map;
import java.util.List;
import br.edu.impacta.resolveai.atendimento.dto.CategoriaAtendimentoResponse;

import br.edu.impacta.resolveai.atendimento.dto.PaginaAtendimentoResponse;
import br.edu.impacta.resolveai.chamado.PrioridadeChamado;
import br.edu.impacta.resolveai.chamado.StatusChamado;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import io.swagger.v3.oas.annotations.Parameter;

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
                    + "Filtros combinaveis, paginacao no servidor e ordem criadoEm DESC, id DESC. "
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
    public PaginaAtendimentoResponse fila(@AuthenticationPrincipal UsuarioPrincipal principal,
            @Parameter(description = "Indice da pagina a partir de zero", schema = @Schema(minimum = "0"))
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Itens por pagina (1 a 50)", schema = @Schema(minimum = "1", maximum = "50"))
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) PrioridadeChamado prioridade,
            @Parameter(description = "Identificador positivo da categoria")
            @RequestParam(required = false) Long categoriaId) {
        return atendimentoService.fila(principal, page, size, prioridade, categoriaId);
    }

    @GetMapping("/me")
    @Operation(summary = "Consultar minha carteira de atendimento",
            description = "Somente chamados atribuidos ao ATENDENTE ou ADMIN autenticado. "
                    + "Permite combinar status, prioridade e categoriaId; ordem criadoEm DESC, id DESC.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina da carteira, inclusive vazia"),
            @ApiResponse(responseCode = "400", description = "Pagina, tamanho ou filtros invalidos",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, invalido ou conta inativa",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissao",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaginaAtendimentoResponse carteira(@AuthenticationPrincipal UsuarioPrincipal principal,
            @Parameter(description = "Indice da pagina a partir de zero", schema = @Schema(minimum = "0"))
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Itens por pagina (1 a 50)", schema = @Schema(minimum = "1", maximum = "50"))
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) PrioridadeChamado prioridade,
            @Parameter(description = "Identificador positivo da categoria")
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) StatusChamado status) {
        return atendimentoService.carteira(principal, page, size, prioridade, categoriaId, status);
    }

    @GetMapping("/todos")
    @Operation(summary = "Consultar todos os chamados como administrador",
            description = "Exclusivo de ADMIN ativo. Inclui todos os solicitantes, responsaveis e estados, "
                    + "com filtros combinaveis de status, prioridade e categoriaId. Ordem criadoEm DESC, id DESC.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina de todos os chamados, inclusive vazia"),
            @ApiResponse(responseCode = "400", description = "Pagina, tamanho ou filtros invalidos",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, invalido ou conta inativa",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil diferente de ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaginaAtendimentoResponse todos(@AuthenticationPrincipal UsuarioPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) PrioridadeChamado prioridade,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) StatusChamado status) {
        return atendimentoService.todos(principal, page, size, prioridade, categoriaId, status);
    }
}
