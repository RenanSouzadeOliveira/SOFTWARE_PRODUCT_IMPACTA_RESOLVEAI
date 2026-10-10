package br.edu.impacta.resolveai.comentario.dto;

import java.time.Instant;
import br.edu.impacta.resolveai.comentario.Comentario;

public record ComentarioResponse(Long id, String conteudo, AutorComentarioResponse autor, Instant criadoEm) {
    public static ComentarioResponse from(Comentario comentario) {
        return new ComentarioResponse(comentario.id(), comentario.conteudo(),
                AutorComentarioResponse.from(comentario.autor()), comentario.criadoEm());
    }
}
