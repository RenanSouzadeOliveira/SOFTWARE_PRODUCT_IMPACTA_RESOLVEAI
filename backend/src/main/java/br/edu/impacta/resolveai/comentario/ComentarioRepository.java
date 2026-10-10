package br.edu.impacta.resolveai.comentario;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

public interface ComentarioRepository extends Repository<Comentario, Long> {
    Comentario save(Comentario comentario);
    @EntityGraph(attributePaths = "autor")
    List<Comentario> findByChamadoIdOrderByCriadoEmAscIdAsc(Long chamadoId);
}
