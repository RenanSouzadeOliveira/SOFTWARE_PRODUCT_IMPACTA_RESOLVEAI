package br.edu.impacta.resolveai.comentario;

import br.edu.impacta.resolveai.comentario.dto.ComentarioResponse;
import br.edu.impacta.resolveai.comentario.dto.ComentariosResponse;
import br.edu.impacta.resolveai.comentario.dto.CriarComentarioRequest;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.shared.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chamados/{id}/comentarios")
@SecurityRequirement(name = "bearerAuth")
public class ComentarioController {
    private final ComentarioService service;
    public ComentarioController(ComentarioService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Consultar comentarios do chamado", description = "Solicitante proprietario, atendente atribuido e ADMIN. "
            + "Antes de atribuir somente proprietario/ADMIN. Sem visibilidade retorna 404 igual ao detalhe. "
            + "Ordem criadoEm ASC/id ASC; autor usa nome/perfil atuais. FECHADO continua legivel com podeComentar=false.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comentarios ordenados e permissao atual"),
            @ApiResponse(responseCode = "400", description = "Identificador invalido", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Autenticacao ausente ou conta inativa", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Chamado inexistente ou sem visibilidade", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ComentariosResponse listar(@PathVariable Long id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return service.listar(id, principal);
    }

    @PostMapping
    @Operation(summary = "Criar comentario imutavel", description = "Participantes ativos e ADMIN podem comentar em qualquer estado exceto FECHADO. "
            + "Conteudo trim1..2000; autor e data sao definidos pelo servidor. Nao altera status, versao, datas ou historicos do chamado. "
            + "Bloqueio da linha do chamado serializa insercao e fechamento. Comentarios nao possuem edicao/exclusao.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Comentario criado"),
            @ApiResponse(responseCode = "400", description = "Conteudo/identificador invalido", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Autenticacao ausente ou conta inativa", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuario nao participa do chamado", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Chamado inexistente", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Chamado fechado", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ComentarioResponse> criar(@PathVariable Long id,
            @AuthenticationPrincipal UsuarioPrincipal principal, @RequestBody CriarComentarioRequest request) {
        return ResponseEntity.status(201).body(service.criar(id, principal, request));
    }
}
