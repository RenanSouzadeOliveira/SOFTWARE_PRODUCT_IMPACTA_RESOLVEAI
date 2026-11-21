package br.edu.impacta.resolveai.categoria.dto;

import br.edu.impacta.resolveai.categoria.Categoria;

public record CategoriaAdminResponse(Long id, String nome, String descricao, boolean ativa) {

    public static CategoriaAdminResponse from(Categoria categoria) {
        return new CategoriaAdminResponse(categoria.id(), categoria.nome(), categoria.descricao(), categoria.ativa());
    }
}
