package br.edu.impacta.resolveai.categoria;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Lock;

public interface CategoriaRepository extends Repository<Categoria, Long> {

    Categoria save(Categoria categoria);

    Optional<Categoria> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<Categoria> findByIdAndAtivaTrue(Long id);

    List<Categoria> findAllByAtivaTrueOrderByNomeAsc();
}
