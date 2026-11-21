package br.edu.impacta.resolveai.dashboard;

import br.edu.impacta.resolveai.dashboard.dto.DashboardResumoResponse;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.shared.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/resumo")
    @Operation(summary = "Resumir chamados visíveis",
            description = "Usa o escopo da listagem de chamados. Inclui categorias ativas e categorias "
                    + "inativas com chamados visíveis.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Indicadores e distribuições de chamados"),
            @ApiResponse(responseCode = "401", description = "Autenticação necessária",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil sem acesso",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public DashboardResumoResponse resumir(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return dashboardService.resumir(principal);
    }
}
