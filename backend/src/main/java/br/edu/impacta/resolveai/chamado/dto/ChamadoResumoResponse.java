package br.edu.impacta.resolveai.chamado.dto;

import java.time.Instant;

import br.edu.impacta.resolveai.categoria.dto.CategoriaResponse;
import br.edu.impacta.resolveai.chamado.Chamado;
import br.edu.impacta.resolveai.chamado.PrioridadeChamado;
import br.edu.impacta.resolveai.chamado.StatusChamado;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resumo seguro de um chamado do solicitante autenticado")
public record ChamadoResumoResponse(
        Long id,
        String protocolo,
        String titulo,
        CategoriaResponse categoria,
        PrioridadeChamado prioridade,
        StatusChamado status,
        Instant criadoEm,
        Instant atualizadoEm,
        Instant resolvidoEm,
        Instant fechadoEm) {

    public static ChamadoResumoResponse from(Chamado chamado) {
        return new ChamadoResumoResponse(
                chamado.id(),
                chamado.protocolo(),
                chamado.titulo(),
                CategoriaResponse.from(chamado.categoria()),
                chamado.prioridade(),
                chamado.status(),
                chamado.criadoEm(),
                chamado.atualizadoEm(),
                chamado.resolvidoEm(),
                chamado.fechadoEm());
    }
}
