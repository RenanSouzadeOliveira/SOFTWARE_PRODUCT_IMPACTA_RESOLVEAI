package br.edu.impacta.resolveai.security;

import br.edu.impacta.resolveai.usuario.Perfil;

public record JwtIdentity(Long usuarioId, Perfil perfil) {
}
