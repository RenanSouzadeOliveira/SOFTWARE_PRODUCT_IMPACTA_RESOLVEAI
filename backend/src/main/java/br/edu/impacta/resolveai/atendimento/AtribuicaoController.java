package br.edu.impacta.resolveai.atendimento;

import br.edu.impacta.resolveai.atendimento.dto.ChamadoAtendimentoResponse;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.shared.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class AtribuicaoController {

    private final AtendimentoService atendimentoService;

    public AtribuicaoController(AtendimentoService atendimentoService) {
        this.atendimentoService = atendimentoService;
    }

    @PatchMapping("/api/chamados/{id}/assumir")
    @Operation(summary = "Assumir chamado disponivel",
            description = "Sem corpo de entrada. O ATENDENTE ou ADMIN ativo autenticado assume um chamado "
                    + "ABERTO sem responsavel, alterando-o para EM_ATENDIMENTO. Datas e historico "
                    + "sao registrados atomicamente; concorrencia retorna 409 sem sobrescrever o vencedor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chamado atribuido ao usuario autenticado"),
            @ApiResponse(responseCode = "400", description = "Identificador invalido",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, invalido ou conta inativa",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissao",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Chamado inexistente",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Chamado indisponivel ou alterado concorrentemente",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ChamadoAtendimentoResponse assumir(@PathVariable Long id,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return atendimentoService.assumir(id, principal);
    }
}
