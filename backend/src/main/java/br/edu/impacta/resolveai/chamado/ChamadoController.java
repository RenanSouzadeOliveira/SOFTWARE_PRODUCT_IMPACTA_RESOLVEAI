package br.edu.impacta.resolveai.chamado;

import java.net.URI;

import br.edu.impacta.resolveai.chamado.dto.AbrirChamadoRequest;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/chamados")
@SecurityRequirement(name = "bearerAuth")
public class ChamadoController {

    private final ChamadoService chamadoService;

    public ChamadoController(ChamadoService chamadoService) {
        this.chamadoService = chamadoService;
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
}
