package br.edu.impacta.resolveai.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public WebConfig(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        List<String> origins = allowedOrigins.stream().map(String::trim).toList();
        if (origins.isEmpty() || origins.stream().anyMatch(origin -> origin.isEmpty() || origin.contains("*")
                || !origin.matches("https?://[^/\\s]+"))) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS deve listar origens explicitas (esquema://host[:porta]), sem curingas");
        }
        this.allowedOrigins = origins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept")
                // JWT trafega no cabecalho Authorization; cookies/credenciais do navegador nao sao necessarios.
                .allowCredentials(false)
                .maxAge(3600);
    }
}
