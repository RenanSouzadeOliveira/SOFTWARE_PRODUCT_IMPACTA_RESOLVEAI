package br.edu.impacta.resolveai.categoria;

import java.util.List;

import br.edu.impacta.resolveai.categoria.dto.CategoriaResponse;
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
@RequestMapping("/api/categorias")
@SecurityRequirement(name = "bearerAuth")
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping
    @Operation(
            summary = "Listar categorias ativas",
            description = "Retorna somente id e nome das categorias ativas, ordenadas por nome.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categorias ativas"),
            @ApiResponse(responseCode = "401", description = "Token ausente, invalido ou de conta inativa",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<CategoriaResponse> listarAtivas() {
        return categoriaService.listarAtivas();
    }
}
