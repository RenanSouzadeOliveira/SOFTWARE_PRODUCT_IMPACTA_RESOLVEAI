package br.edu.impacta.resolveai.auth.dto;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "email e obrigatorio")
        @Email(message = "email deve ser valido")
        @Size(max = 254, message = "email deve ter no maximo 254 caracteres")
        String email,
        @NotBlank(message = "senha e obrigatoria")
        @Size(max = 72, message = "senha deve ter no maximo 72 caracteres")
        String senha) {

    public LoginRequest {
        if (email != null) {
            email = email.trim().toLowerCase(Locale.ROOT);
        }
    }
}
