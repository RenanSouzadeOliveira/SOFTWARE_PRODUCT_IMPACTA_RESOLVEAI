package br.edu.impacta.resolveai.chamado.dto;

import br.edu.impacta.resolveai.chamado.PrioridadeChamado;
import jakarta.validation.constraints.NotNull;

public record AlterarPrioridadeRequest(@NotNull PrioridadeChamado prioridade) { }
