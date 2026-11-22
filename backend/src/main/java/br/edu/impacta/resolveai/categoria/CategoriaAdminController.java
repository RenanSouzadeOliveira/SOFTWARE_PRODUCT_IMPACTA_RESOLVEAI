package br.edu.impacta.resolveai.categoria;

import java.util.List;

import br.edu.impacta.resolveai.categoria.dto.CategoriaAdminResponse;
import br.edu.impacta.resolveai.categoria.dto.CategoriaRequest;
import br.edu.impacta.resolveai.categoria.dto.SituacaoCategoriaRequest;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import br.edu.impacta.resolveai.shared.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/admin/categorias")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Administração de categorias", description = "Operações exclusivas do perfil ADMIN.")
public class CategoriaAdminController {

    private final CategoriaService categorias;

    public CategoriaAdminController(CategoriaService categorias) {
        this.categorias = categorias;
    }

    @GetMapping
    @Operation(summary = "Listar categorias ativas e inativas")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Operação concluída"),
            @ApiResponse(responseCode = "401", description = "Autenticação necessária ou conta inativa", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissão", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<CategoriaAdminResponse> listar(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return categorias.listarTodas(principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar categoria ativa")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Operação concluída"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Autenticação necessária ou conta inativa", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissão", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Nome de categoria equivalente já cadastrado", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public CategoriaAdminResponse criar(@AuthenticationPrincipal UsuarioPrincipal principal,
            @Valid @RequestBody CategoriaRequest request) {
        return categorias.criar(principal, request);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Editar nome e descrição da categoria")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Operação concluída"),
            @ApiResponse(responseCode = "400", description = "Dados ou identificador inválidos", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Autenticação necessária ou conta inativa", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissão", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Nome de categoria equivalente já cadastrado", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public CategoriaAdminResponse editar(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id, @Valid @RequestBody CategoriaRequest request) {
        return categorias.editar(principal, id, request);
    }

    @PatchMapping("/{id}/situacao")
    @Operation(summary = "Ativar ou desativar categoria", description = "Preserva os vínculos com chamados existentes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Operação concluída"),
            @ApiResponse(responseCode = "400", description = "Dados ou identificador inválidos", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Autenticação necessária ou conta inativa", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissão", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public CategoriaAdminResponse alterarSituacao(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id, @Valid @RequestBody SituacaoCategoriaRequest request) {
        return categorias.alterarSituacao(principal, id, request.ativa());
    }
}
