package br.edu.impacta.resolveai.chamado.dto;

import java.time.Instant;

import br.edu.impacta.resolveai.categoria.dto.CategoriaResponse;
import br.edu.impacta.resolveai.chamado.Chamado;
import br.edu.impacta.resolveai.chamado.PrioridadeChamado;
import br.edu.impacta.resolveai.chamado.StatusChamado;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Chamado criado, sem expor entidades persistentes")
public record ChamadoResponse(
        Long id,
        String protocolo,
        String titulo,
        String descricao,
        StatusChamado status,
        PrioridadeChamado prioridade,
        UsuarioChamadoResponse solicitante,
        UsuarioChamadoResponse atendente,
        CategoriaResponse categoria,
        String solucao,
        Instant criadoEm,
        Instant atualizadoEm,
        Instant resolvidoEm,
        Instant fechadoEm) {

    public static ChamadoResponse from(Chamado chamado) {
        return new ChamadoResponse(
                chamado.id(),
                chamado.protocolo(),
                chamado.titulo(),
                chamado.descricao(),
                chamado.status(),
                chamado.prioridade(),
                UsuarioChamadoResponse.from(chamado.solicitante()),
                chamado.atendente() == null ? null : UsuarioChamadoResponse.from(chamado.atendente()),
                CategoriaResponse.from(chamado.categoria()),
                chamado.solucao(),
                chamado.criadoEm(),
                chamado.atualizadoEm(),
                chamado.resolvidoEm(),
                chamado.fechadoEm());
    }
}
