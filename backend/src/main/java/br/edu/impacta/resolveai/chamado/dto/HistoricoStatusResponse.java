package br.edu.impacta.resolveai.chamado.dto;

import java.time.Instant;
import br.edu.impacta.resolveai.chamado.HistoricoStatus;
import br.edu.impacta.resolveai.chamado.StatusChamado;

public record HistoricoStatusResponse(Long id, StatusChamado statusAnterior, StatusChamado statusNovo,
        UsuarioChamadoResponse autor, Instant criadoEm, String observacao) {
    public static HistoricoStatusResponse from(HistoricoStatus evento) {
        return new HistoricoStatusResponse(evento.id(), evento.statusAnterior(), evento.statusNovo(),
                UsuarioChamadoResponse.from(evento.autor()), evento.criadoEm(), evento.observacao());
    }
}
