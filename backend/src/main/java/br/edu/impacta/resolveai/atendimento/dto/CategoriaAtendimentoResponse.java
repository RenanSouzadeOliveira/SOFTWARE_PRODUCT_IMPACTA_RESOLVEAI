package br.edu.impacta.resolveai.atendimento.dto;

import br.edu.impacta.resolveai.categoria.Categoria;

public record CategoriaAtendimentoResponse(Long id, String nome, boolean ativa) {
    public static CategoriaAtendimentoResponse from(Categoria categoria) {
        return new CategoriaAtendimentoResponse(categoria.id(), categoria.nome(), categoria.ativa());
    }
}
