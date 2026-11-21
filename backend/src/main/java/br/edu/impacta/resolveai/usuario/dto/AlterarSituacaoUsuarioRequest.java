package br.edu.impacta.resolveai.usuario.dto;

import jakarta.validation.constraints.NotNull;

public record AlterarSituacaoUsuarioRequest(
        @NotNull(message = "Situação é obrigatória") Boolean ativo) {
}
