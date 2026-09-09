package br.edu.impacta.resolveai.usuario;

import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.shared.error.ApiErrorResponse;
import br.edu.impacta.resolveai.usuario.dto.AtualizarNomeRequest;
import br.edu.impacta.resolveai.usuario.dto.UsuarioResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
@SecurityRequirement(name = "bearerAuth")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/me")
    @Operation(summary = "Consultar usuario autenticado", description = "Retorna apenas dados seguros da conta ativa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conta autenticada"),
            @ApiResponse(responseCode = "401", description = "Token ausente, invalido ou de conta inativa",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public UsuarioResponse me(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return usuarioService.me(principal);
    }

    @PatchMapping("/me")
    @Operation(
            summary = "Alterar nome do usuario autenticado",
            description = "Altera somente o nome; perfil, e-mail e situacao nao sao editaveis por esta operacao.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nome atualizado"),
            @ApiResponse(responseCode = "400", description = "Nome invalido",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, invalido ou de conta inativa",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public UsuarioResponse updateName(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @Valid @RequestBody AtualizarNomeRequest request) {
        return usuarioService.updateName(principal, request);
    }
}
