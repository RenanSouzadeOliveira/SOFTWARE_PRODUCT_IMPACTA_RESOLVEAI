package br.edu.impacta.resolveai.chamado;

import java.util.Optional;

import org.springframework.data.repository.Repository;

public interface ChamadoRepository extends Repository<Chamado, Long> {

    Chamado save(Chamado chamado);

    Chamado saveAndFlush(Chamado chamado);

    Optional<Chamado> findById(Long id);

    Optional<Chamado> findByProtocolo(String protocolo);
}
