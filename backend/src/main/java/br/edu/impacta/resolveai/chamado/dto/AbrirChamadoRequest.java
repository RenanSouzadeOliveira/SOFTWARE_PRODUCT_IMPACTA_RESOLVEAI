package br.edu.impacta.resolveai.chamado.dto;

import br.edu.impacta.resolveai.chamado.PrioridadeChamado;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Dados editaveis pelo solicitante na abertura do chamado")
public record AbrirChamadoRequest(
        @NotBlank(message = "titulo e obrigatorio")
        @Size(min = 5, max = 120, message = "titulo deve ter entre 5 e 120 caracteres")
        @Schema(example = "Erro ao acessar o portal", minLength = 5, maxLength = 120)
        String titulo,

        @NotBlank(message = "descricao e obrigatoria")
        @Size(min = 20, max = 2000, message = "descricao deve ter entre 20 e 2000 caracteres")
        @Schema(example = "Ao entrar no portal, a pagina exibe acesso negado.", minLength = 20, maxLength = 2000)
        String descricao,

        @NotNull(message = "categoriaId e obrigatorio")
        @Positive(message = "categoriaId deve ser positivo")
        @Schema(example = "1")
        Long categoriaId,

        @NotNull(message = "prioridade e obrigatoria")
        @Schema(example = "MEDIA", allowableValues = {"BAIXA", "MEDIA", "ALTA"})
        PrioridadeChamado prioridade) {

    public AbrirChamadoRequest {
        if (titulo != null) {
            titulo = titulo.trim();
        }
        if (descricao != null) {
            descricao = descricao.trim();
        }
    }
}
