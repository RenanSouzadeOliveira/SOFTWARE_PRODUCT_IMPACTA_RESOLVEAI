package br.edu.impacta.resolveai.categoria.dto;

import br.edu.impacta.resolveai.categoria.Categoria;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoriaRequest(
        @NotBlank(message = "Informe o nome da categoria")
        @Size(max = 100, message = "O nome deve ter até 100 caracteres")
        String nome,

        @Size(max = 255, message = "A descrição deve ter até 255 caracteres")
        String descricao) {

    public CategoriaRequest {
        nome = Categoria.prepararNome(nome);
        descricao = descricao == null || descricao.isBlank() ? null : descricao.strip();
    }
}
