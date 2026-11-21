package br.edu.impacta.resolveai.usuario;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends Repository<Usuario, Long> {

    Usuario save(Usuario usuario);

    Usuario saveAndFlush(Usuario usuario);

    List<Usuario> findAllByOrderByNomeAscIdAsc();

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select u from Usuario u where u.id = :id")
    Optional<Usuario> findByIdParaLeitura(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.id = :id")
    Optional<Usuario> findByIdParaAtualizacao(@Param("id") Long id);

    Optional<Usuario> findById(Long id);

    Optional<Usuario> findByEmail(String email);
}
