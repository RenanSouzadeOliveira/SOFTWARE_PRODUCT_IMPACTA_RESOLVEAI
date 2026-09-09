package br.edu.impacta.resolveai.usuario;

import java.util.Optional;

import org.springframework.data.repository.Repository;

public interface UsuarioRepository extends Repository<Usuario, Long> {

    Usuario save(Usuario usuario);

    Optional<Usuario> findById(Long id);

    Optional<Usuario> findByEmail(String email);
}
