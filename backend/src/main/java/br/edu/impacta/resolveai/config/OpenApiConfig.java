package br.edu.impacta.resolveai.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI resolveAiOpenApi() {
        return new OpenAPI().info(new Info()
                .title("ResolveAi API")
                .description("API REST para gestao academica de chamados")
                .version("v1"));
    }
}
