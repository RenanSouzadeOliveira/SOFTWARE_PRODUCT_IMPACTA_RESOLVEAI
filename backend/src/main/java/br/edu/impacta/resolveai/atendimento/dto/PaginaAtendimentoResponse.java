package br.edu.impacta.resolveai.atendimento.dto;

import java.util.List;

import br.edu.impacta.resolveai.chamado.Chamado;
import org.springframework.data.domain.Page;

public record PaginaAtendimentoResponse(List<ChamadoAtendimentoResponse> content,
        int page, int size, long totalElements, int totalPages) {

    public static PaginaAtendimentoResponse from(Page<Chamado> result) {
        return new PaginaAtendimentoResponse(result.getContent().stream()
                .map(ChamadoAtendimentoResponse::from).toList(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}
