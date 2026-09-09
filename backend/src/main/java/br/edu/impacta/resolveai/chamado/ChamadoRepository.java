package br.edu.impacta.resolveai.chamado;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

public interface ChamadoRepository extends Repository<Chamado, Long> {

    Chamado save(Chamado chamado);

    Chamado saveAndFlush(Chamado chamado);

    Optional<Chamado> findById(Long id);

    Optional<Chamado> findByProtocolo(String protocolo);

    @EntityGraph(attributePaths = "categoria")
    List<Chamado> findAllBySolicitanteIdOrderByCriadoEmDesc(Long solicitanteId);

    @EntityGraph(attributePaths = {"categoria", "solicitante", "atendente"})
    Optional<Chamado> findByIdAndSolicitanteId(Long id, Long solicitanteId);
}
