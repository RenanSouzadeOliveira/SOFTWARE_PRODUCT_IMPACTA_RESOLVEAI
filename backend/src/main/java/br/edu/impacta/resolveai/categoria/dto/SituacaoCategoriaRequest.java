package br.edu.impacta.resolveai.categoria.dto;

import jakarta.validation.constraints.NotNull;

public record SituacaoCategoriaRequest(
        @NotNull(message = "Informe se a categoria está ativa")
        Boolean ativa) {
}
