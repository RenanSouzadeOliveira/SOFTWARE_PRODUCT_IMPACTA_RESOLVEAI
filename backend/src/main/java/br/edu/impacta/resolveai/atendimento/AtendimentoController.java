package br.edu.impacta.resolveai.atendimento;

import java.util.Map;

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

@RestController
@RequestMapping("/api/atendimento")
@SecurityRequirement(name = "bearerAuth")
public class AtendimentoController {

    @GetMapping("/acesso")
    @Operation(
            summary = "Validar acesso exclusivo de atendente",
            description = "Operacao minima protegida exclusivamente pelo perfil ATENDENTE.")
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
}
