package br.edu.impacta.resolveai.usuario.dto;

import java.time.Instant;

import br.edu.impacta.resolveai.usuario.Perfil;
import br.edu.impacta.resolveai.usuario.Usuario;

public record UsuarioResponse(
        Long id,
        String nome,
        String email,
        Perfil perfil,
        boolean ativo,
        Instant criadoEm,
        Instant atualizadoEm) {

    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(
                usuario.id(),
                usuario.nome(),
                usuario.email(),
                usuario.perfil(),
                usuario.ativo(),
                usuario.criadoEm(),
                usuario.atualizadoEm());
    }
}
