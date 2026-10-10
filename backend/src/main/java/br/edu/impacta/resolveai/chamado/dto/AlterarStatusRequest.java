package br.edu.impacta.resolveai.chamado.dto;

import br.edu.impacta.resolveai.chamado.StatusChamado;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

public record AlterarStatusRequest(
        @NotNull @Schema(description = "Destino permitido pelo estado atual e perfil. ABERTO->EM_ATENDIMENTO exige /assumir")
        StatusChamado status,
        @Schema(description = "Obrigatoria para resolver/reabrir: 10..500 caracteres apos trim; opcional para fechar, maximo 500")
        String justificativa) { }
