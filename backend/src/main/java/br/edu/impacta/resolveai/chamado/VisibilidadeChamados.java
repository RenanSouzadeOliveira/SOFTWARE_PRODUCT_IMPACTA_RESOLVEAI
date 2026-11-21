package br.edu.impacta.resolveai.chamado;

import org.springframework.data.jpa.domain.Specification;
import br.edu.impacta.resolveai.usuario.Usuario;

public final class VisibilidadeChamados {
    private VisibilidadeChamados() { }
    public static Specification<Chamado> doUsuario(Usuario principal) {
        return switch (principal.perfil()) {
            case SOLICITANTE -> (root, query, cb) -> cb.equal(root.get("solicitante").get("id"), principal.id());
            case ATENDENTE -> carteira(principal.id());
            case ADMIN -> (root, query, cb) -> cb.conjunction();
        };
    }
    public static Specification<Chamado> carteira(Long id) {
        return (root, query, cb) -> cb.equal(root.get("atendente").get("id"), id);
    }
    public static Specification<Chamado> fila() {
        return (root, query, cb) -> cb.and(cb.equal(root.get("status"), StatusChamado.ABERTO), cb.isNull(root.get("atendente")));
    }
}
