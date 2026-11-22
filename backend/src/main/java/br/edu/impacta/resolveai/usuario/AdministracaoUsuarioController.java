package br.edu.impacta.resolveai.usuario;

import java.util.List;

import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.shared.error.ApiErrorResponse;
import br.edu.impacta.resolveai.usuario.dto.AlterarSituacaoUsuarioRequest;
import br.edu.impacta.resolveai.usuario.dto.AtualizarUsuarioRequest;
import br.edu.impacta.resolveai.usuario.dto.CriarUsuarioRequest;
import br.edu.impacta.resolveai.usuario.dto.UsuarioResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/usuarios")
@SecurityRequirement(name = "bearerAuth")
public class AdministracaoUsuarioController {
    private final AdministracaoUsuarioService service;

    public AdministracaoUsuarioController(AdministracaoUsuarioService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Listar usuários", description = "Exige ADMIN; inclui contas inativas e ordena por nome e identificador.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Operação concluída"),
            @ApiResponse(responseCode = "401", description = "Autenticação necessária ou conta inativa", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissão", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<UsuarioResponse> listar(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return service.listar(principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar usuário", description = "Exige ADMIN; cria conta ativa com o perfil informado e senha protegida por BCrypt.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Operação concluída"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Autenticação necessária ou conta inativa", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissão", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "E-mail já cadastrado", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public UsuarioResponse criar(@AuthenticationPrincipal UsuarioPrincipal principal,
            @Valid @RequestBody CriarUsuarioRequest request) {
        return service.criar(principal, request);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Atualizar usuário", description = "Exige ADMIN; altera nome e e-mail. Para alterar o perfil, informe perfil e versaoEsperada. Impede remover o próprio perfil ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Operação concluída"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Autenticação necessária ou conta inativa", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissão", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "E-mail já cadastrado, versão divergente ou alteração concorrente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public UsuarioResponse atualizar(@PathVariable Long id, @AuthenticationPrincipal UsuarioPrincipal principal,
            @Valid @RequestBody AtualizarUsuarioRequest request) {
        return service.atualizar(id, principal, request);
    }

    @PatchMapping("/{id}/situacao")
    @Operation(summary = "Ativar ou desativar usuário", description = "Exige ADMIN; preserva referências e impede a própria desativação.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Operação concluída"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Autenticação necessária ou conta inativa", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissão", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Alteração concorrente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public UsuarioResponse alterarSituacao(@PathVariable Long id, @AuthenticationPrincipal UsuarioPrincipal principal,
            @Valid @RequestBody AlterarSituacaoUsuarioRequest request) {
        return service.alterarSituacao(id, principal, request);
    }
}
