package br.edu.impacta.resolveai.security;

import br.edu.impacta.resolveai.usuario.Perfil;

public record UsuarioPrincipal(Long id, String email, Perfil perfil, long versaoSeguranca) {

    public UsuarioPrincipal(Long id, String email, Perfil perfil) {
        this(id, email, perfil, 0L);
    }
}
