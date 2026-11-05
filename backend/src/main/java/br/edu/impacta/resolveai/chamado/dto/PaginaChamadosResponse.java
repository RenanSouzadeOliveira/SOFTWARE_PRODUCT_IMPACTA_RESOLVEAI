package br.edu.impacta.resolveai.chamado.dto;

import java.util.List;
import br.edu.impacta.resolveai.chamado.Chamado;
import org.springframework.data.domain.Page;

public record PaginaChamadosResponse(List<ChamadoResumoResponse> content,
        int page, int size, long totalElements, int totalPages) {
    public static PaginaChamadosResponse from(Page<Chamado> result) {
        return new PaginaChamadosResponse(result.map(ChamadoResumoResponse::from).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
}
