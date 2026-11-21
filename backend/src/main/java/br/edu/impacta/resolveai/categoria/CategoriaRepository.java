package br.edu.impacta.resolveai.categoria;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface CategoriaRepository extends Repository<Categoria, Long> {

    Categoria save(Categoria categoria);

    Categoria saveAndFlush(Categoria categoria);

    Optional<Categoria> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Categoria c where c.id = :id")
    Optional<Categoria> findByIdParaAtualizacao(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<Categoria> findByIdAndAtivaTrue(Long id);

    List<Categoria> findAllByAtivaTrueOrderByNomeAsc();

    List<Categoria> findAllByOrderByNomeAscIdAsc();
}
