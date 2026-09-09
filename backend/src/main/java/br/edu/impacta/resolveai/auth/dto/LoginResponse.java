package br.edu.impacta.resolveai.auth.dto;

import br.edu.impacta.resolveai.usuario.dto.UsuarioResponse;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UsuarioResponse usuario) {
}
