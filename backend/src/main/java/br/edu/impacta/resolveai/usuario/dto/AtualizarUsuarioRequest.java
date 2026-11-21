package br.edu.impacta.resolveai.usuario.dto;

import br.edu.impacta.resolveai.usuario.Perfil;
import br.edu.impacta.resolveai.usuario.Usuario;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AtualizarUsuarioRequest(
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 120, message = "Nome deve ter no máximo 120 caracteres") String nome,
        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail deve ser válido")
        @Size(max = 254, message = "E-mail deve ter no máximo 254 caracteres") String email,
        @NotNull(message = "Perfil é obrigatório")
        @JsonDeserialize(using = PerfilUsuarioDeserializer.class) Perfil perfil) {
    public AtualizarUsuarioRequest {
        email = Usuario.normalizarEmail(email);
    }
}
