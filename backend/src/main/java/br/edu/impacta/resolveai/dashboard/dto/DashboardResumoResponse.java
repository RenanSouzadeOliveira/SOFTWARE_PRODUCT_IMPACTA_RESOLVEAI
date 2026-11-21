package br.edu.impacta.resolveai.dashboard.dto;

import java.util.List;

import br.edu.impacta.resolveai.chamado.PrioridadeChamado;
import br.edu.impacta.resolveai.chamado.StatusChamado;
import io.swagger.v3.oas.annotations.media.Schema;

public record DashboardResumoResponse(
        long total,
        long abertos,
        long emAtendimento,
        @Schema(description = "Soma dos chamados resolvidos e fechados") long concluidos,
        List<GrupoStatus> porStatus,
        List<GrupoPrioridade> porPrioridade,
        List<GrupoCategoria> porCategoria) {

    public record GrupoStatus(StatusChamado status, long quantidade) { }

    public record GrupoPrioridade(PrioridadeChamado prioridade, long quantidade) { }

    public record GrupoCategoria(Long id, String nome, long quantidade) { }
}
