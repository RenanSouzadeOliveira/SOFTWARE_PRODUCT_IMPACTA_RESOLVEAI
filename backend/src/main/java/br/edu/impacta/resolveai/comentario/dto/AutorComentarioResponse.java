package br.edu.impacta.resolveai.comentario.dto;

import br.edu.impacta.resolveai.usuario.Perfil;
import br.edu.impacta.resolveai.usuario.Usuario;

public record AutorComentarioResponse(Long id, String nome, Perfil perfil) {
    public static AutorComentarioResponse from(Usuario usuario) {
        return new AutorComentarioResponse(usuario.id(), usuario.nome(), usuario.perfil());
    }
}
