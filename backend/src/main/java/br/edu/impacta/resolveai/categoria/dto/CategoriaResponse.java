package br.edu.impacta.resolveai.categoria.dto;

import br.edu.impacta.resolveai.categoria.Categoria;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Identificacao segura de categoria")
public record CategoriaResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "Acesso") String nome) {

    public static CategoriaResponse from(Categoria categoria) {
        return new CategoriaResponse(categoria.id(), categoria.nome());
    }
}
