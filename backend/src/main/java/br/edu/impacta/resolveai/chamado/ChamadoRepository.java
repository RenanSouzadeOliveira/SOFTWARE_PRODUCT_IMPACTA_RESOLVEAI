package br.edu.impacta.resolveai.chamado;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChamadoRepository extends Repository<Chamado, Long> {

    Chamado save(Chamado chamado);

    Chamado saveAndFlush(Chamado chamado);

    Optional<Chamado> findById(Long id);

    // No fetch joins: PostgreSQL cannot FOR UPDATE the nullable side of an outer join.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Chamado c where c.id = :id")
    Optional<Chamado> findByIdForComment(@Param("id") Long id);

    Optional<Chamado> findByProtocolo(String protocolo);

    @EntityGraph(attributePaths = "categoria")
    List<Chamado> findAllBySolicitanteIdOrderByCriadoEmDesc(Long solicitanteId);

    @EntityGraph(attributePaths = {"categoria", "solicitante", "atendente"})
    Optional<Chamado> findByIdAndSolicitanteId(Long id, Long solicitanteId);

    @EntityGraph(attributePaths = {"categoria", "atendente"})
    @Query("""
            select c from Chamado c where c.status = br.edu.impacta.resolveai.chamado.StatusChamado.ABERTO
            and c.atendente is null
            and (:prioridade is null or c.prioridade = :prioridade)
            and (:categoriaId is null or c.categoria.id = :categoriaId)
            """)
    Page<Chamado> findFila(@Param("prioridade") PrioridadeChamado prioridade,
            @Param("categoriaId") Long categoriaId, Pageable pageable);

    @EntityGraph(attributePaths = {"categoria", "atendente"})
    @Query("""
            select c from Chamado c where c.atendente.id = :atendenteId
            and (:status is null or c.status = :status)
            and (:prioridade is null or c.prioridade = :prioridade)
            and (:categoriaId is null or c.categoria.id = :categoriaId)
            """)
    Page<Chamado> findCarteira(@Param("atendenteId") Long atendenteId,
            @Param("status") StatusChamado status, @Param("prioridade") PrioridadeChamado prioridade,
            @Param("categoriaId") Long categoriaId, Pageable pageable);
}
