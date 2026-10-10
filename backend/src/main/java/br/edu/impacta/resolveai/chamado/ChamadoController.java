package br.edu.impacta.resolveai.chamado;

import java.net.URI;
import java.util.List;

import br.edu.impacta.resolveai.chamado.dto.AbrirChamadoRequest;
import br.edu.impacta.resolveai.chamado.dto.AlterarStatusRequest;
import br.edu.impacta.resolveai.chamado.dto.AlterarPrioridadeRequest;
import br.edu.impacta.resolveai.chamado.dto.ChamadoDetalheResponse;
import br.edu.impacta.resolveai.chamado.dto.ChamadoResponse;
import br.edu.impacta.resolveai.chamado.dto.ChamadoResumoResponse;
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

    public ChamadoController(ChamadoService chamadoService, CicloChamadoService ciclo) {
        this.chamadoService = chamadoService;
        this.ciclo = ciclo;
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

    @GetMapping("/me")
    @Operation(
            summary = "Listar meus chamados",
            description = "Lista apenas os chamados do solicitante autenticado, do mais recente para o mais antigo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chamados do solicitante, ou lista vazia"),
            @ApiResponse(responseCode = "401", description = "Token ausente, invalido ou de conta inativa",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil autenticado sem permissao",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<ChamadoResumoResponse> listarMeus(
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return chamadoService.listarMeus(principal);
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
