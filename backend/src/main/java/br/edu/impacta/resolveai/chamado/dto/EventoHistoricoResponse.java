package br.edu.impacta.resolveai.chamado.dto;

import java.time.Instant;
import br.edu.impacta.resolveai.chamado.TipoEventoHistorico;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Evento imutável. Autor e responsáveis incluem somente ID e nome, mesmo após desativação.")
public record EventoHistoricoResponse(
        @Schema(description = "Identificador estável com origem: STATUS-id ou CHAMADO-id") String id,
        Long chamadoId, TipoEventoHistorico tipoEvento, UsuarioChamadoResponse autor, Instant criadoEm,
        Valor valorAnterior, Valor valorNovo, String observacao) {
    @Schema(description = "Código do enum ou ID do responsável; nome preenchido apenas para responsável")
    public record Valor(String codigo, String nome) { }
}
