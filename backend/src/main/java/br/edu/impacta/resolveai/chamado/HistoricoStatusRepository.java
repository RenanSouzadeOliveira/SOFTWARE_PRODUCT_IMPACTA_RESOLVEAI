package br.edu.impacta.resolveai.chamado;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

public interface HistoricoStatusRepository extends Repository<HistoricoStatus, Long> {
    HistoricoStatus save(HistoricoStatus evento);
    @EntityGraph(attributePaths = "autor")
    List<HistoricoStatus> findByChamadoIdOrderByCriadoEmAscIdAsc(Long chamadoId);
}
