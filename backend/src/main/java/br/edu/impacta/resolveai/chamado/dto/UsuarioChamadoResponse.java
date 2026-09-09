package br.edu.impacta.resolveai.chamado.dto;

import br.edu.impacta.resolveai.usuario.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Identificacao segura de um usuario relacionado ao chamado")
public record UsuarioChamadoResponse(Long id, String nome) {

    public static UsuarioChamadoResponse from(Usuario usuario) {
        return new UsuarioChamadoResponse(usuario.id(), usuario.nome());
    }
}
