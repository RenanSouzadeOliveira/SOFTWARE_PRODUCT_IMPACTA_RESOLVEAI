package br.edu.impacta.resolveai.comentario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import br.edu.impacta.resolveai.comentario.TextoComentario;

public record CriarComentarioRequest(
        @Schema(description = "Texto obrigatorio, entre 1 e 2000 caracteres apos trim", requiredMode = Schema.RequiredMode.REQUIRED)
        String conteudo) {
    public CriarComentarioRequest {
        conteudo = TextoComentario.normalizar(conteudo);
    }
}
