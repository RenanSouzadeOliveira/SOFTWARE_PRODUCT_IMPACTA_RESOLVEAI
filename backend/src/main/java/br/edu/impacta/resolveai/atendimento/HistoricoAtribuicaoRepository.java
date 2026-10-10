package br.edu.impacta.resolveai.atendimento;

import org.springframework.data.repository.Repository;

public interface HistoricoAtribuicaoRepository extends Repository<HistoricoAtribuicao, Long> {
    HistoricoAtribuicao save(HistoricoAtribuicao evento);
}
