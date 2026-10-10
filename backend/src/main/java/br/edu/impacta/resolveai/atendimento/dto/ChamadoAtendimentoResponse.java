package br.edu.impacta.resolveai.atendimento.dto;

import java.time.Instant;

import br.edu.impacta.resolveai.categoria.dto.CategoriaResponse;
import br.edu.impacta.resolveai.chamado.Chamado;
import br.edu.impacta.resolveai.chamado.PrioridadeChamado;
import br.edu.impacta.resolveai.chamado.StatusChamado;
import br.edu.impacta.resolveai.chamado.dto.UsuarioChamadoResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resumo seguro de chamado para fila e carteira; datas em UTC")
public record ChamadoAtendimentoResponse(Long id, String protocolo, String titulo,
        CategoriaResponse categoria, PrioridadeChamado prioridade, StatusChamado status,
        Instant criadoEm, Instant atualizadoEm,
        @Schema(description = "Data da atribuicao; nula na fila ou em legado sem evidencia") Instant atribuidoEm,
        UsuarioChamadoResponse atendente) {

    public static ChamadoAtendimentoResponse from(Chamado chamado) {
        return new ChamadoAtendimentoResponse(chamado.id(), chamado.protocolo(), chamado.titulo(),
                CategoriaResponse.from(chamado.categoria()), chamado.prioridade(), chamado.status(),
                chamado.criadoEm(), chamado.atualizadoEm(), chamado.atribuidoEm(),
                chamado.atendente() == null ? null : UsuarioChamadoResponse.from(chamado.atendente()));
    }
}
