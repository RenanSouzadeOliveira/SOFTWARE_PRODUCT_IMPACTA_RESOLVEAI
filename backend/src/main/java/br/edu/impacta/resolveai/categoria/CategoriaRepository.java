package br.edu.impacta.resolveai.categoria;

import java.util.Optional;

import org.springframework.data.repository.Repository;

public interface CategoriaRepository extends Repository<Categoria, Long> {

    Categoria save(Categoria categoria);

    Optional<Categoria> findById(Long id);
}
