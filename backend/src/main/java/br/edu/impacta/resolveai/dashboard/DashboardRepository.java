package br.edu.impacta.resolveai.dashboard;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import br.edu.impacta.resolveai.chamado.Chamado;
import br.edu.impacta.resolveai.dashboard.dto.DashboardResumoResponse.GrupoCategoria;
import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
class DashboardRepository {
    private final EntityManager em;

    DashboardRepository(EntityManager em) {
        this.em = em;
    }

    <E extends Enum<E>> Map<E, Long> contarPorEnum(
            Specification<Chamado> visibilidade, String atributo, Class<E> tipo) {
        var cb = em.getCriteriaBuilder();
        var query = cb.createTupleQuery();
        var chamado = query.from(Chamado.class);
        var grupo = chamado.<E>get(atributo);
        var quantidade = cb.count(chamado);
        query.multiselect(grupo, quantidade)
                .where(visibilidade.toPredicate(chamado, query, cb))
                .groupBy(grupo);

        var contagens = new EnumMap<E, Long>(tipo);
        em.createQuery(query).getResultList()
                .forEach(linha -> contagens.put(linha.get(grupo), linha.get(quantidade)));
        return contagens;
    }

    List<GrupoCategoria> contarPorCategoria(Specification<Chamado> visibilidade) {
        var cb = em.getCriteriaBuilder();
        var query = cb.createQuery(GrupoCategoria.class);
        var chamado = query.from(Chamado.class);
        var categoria = chamado.join("categoria");
        var id = categoria.get("id");
        var nome = categoria.get("nome");
        query.select(cb.construct(GrupoCategoria.class, id, nome, cb.count(chamado)))
                .where(visibilidade.toPredicate(chamado, query, cb))
                .groupBy(id, nome);
        return em.createQuery(query).getResultList();
    }
}
