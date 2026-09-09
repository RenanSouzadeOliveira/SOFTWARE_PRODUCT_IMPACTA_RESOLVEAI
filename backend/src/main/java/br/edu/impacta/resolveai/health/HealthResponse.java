package br.edu.impacta.resolveai.health;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Estado basico da API")
public record HealthResponse(
        @Schema(example = "UP") String status,
        @Schema(example = "resolveai-api") String service,
        Instant timestamp) {
}
