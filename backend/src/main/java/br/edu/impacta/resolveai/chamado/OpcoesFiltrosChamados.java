package br.edu.impacta.resolveai.chamado;

import java.util.List;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.JoinType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Opções derivadas somente dos chamados visíveis; nenhum e-mail ou dado de conta. */
@Service
public class OpcoesFiltrosChamados {
    public record Opcao(Long id, String nome) { }
    public record Response(List<Opcao> categorias, List<Opcao> responsaveis) { }
    private final EntityManager em;
    public OpcoesFiltrosChamados(EntityManager em) { this.em = em; }

    @Transactional(readOnly = true)
    public Response listar(UsuarioPrincipal principal) {
        return new Response(opcoes(principal, "categoria"), opcoes(principal, "atendente"));
    }
    private List<Opcao> opcoes(UsuarioPrincipal principal, String atributo) {
        var cb = em.getCriteriaBuilder();
        var query = cb.createQuery(Opcao.class);
        var root = query.from(Chamado.class);
        var join = root.join(atributo, JoinType.INNER);
        query.select(cb.construct(Opcao.class, join.get("id"), join.get("nome"))).distinct(true);
        query.where(VisibilidadeChamados.doUsuario(principal).toPredicate(root, query, cb));
        query.orderBy(cb.asc(join.get("nome")), cb.asc(join.get("id")));
        return em.createQuery(query).getResultList();
    }
}
