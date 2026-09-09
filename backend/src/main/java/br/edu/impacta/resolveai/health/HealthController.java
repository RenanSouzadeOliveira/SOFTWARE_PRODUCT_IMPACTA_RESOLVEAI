package br.edu.impacta.resolveai.health;

import java.time.Clock;
import java.time.Instant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/health", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Health", description = "Verificacao basica de disponibilidade da API")
public class HealthController {

    private final Clock clock;

    public HealthController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping
    @Operation(summary = "Consulta a disponibilidade da API")
    @ApiResponse(responseCode = "200", description = "API disponivel")
    public HealthResponse health() {
        return new HealthResponse("UP", "resolveai-api", Instant.now(clock));
    }
}
