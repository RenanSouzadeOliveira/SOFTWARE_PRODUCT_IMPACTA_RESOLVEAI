package br.edu.impacta.resolveai.config;

import java.net.URI;
import java.net.URISyntaxException;
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
        if (origins.isEmpty() || !origins.stream().allMatch(WebConfig::origemValida)) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS deve listar origens explicitas (esquema://host[:porta]), sem curingas");
        }
        this.allowedOrigins = origins;
    }

    /** Origem no formato enviado pelo navegador: esquema http(s), host e porta opcional; nada alem disso. */
    static boolean origemValida(String origem) {
        if (origem.isEmpty() || origem.contains("*")) return false;
        try {
            URI uri = new URI(origem);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && uri.getHost() != null && uri.getRawUserInfo() == null && uri.getRawPath().isEmpty()
                    && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && origem.equals(uri.getScheme() + "://" + uri.getRawAuthority());
        } catch (URISyntaxException exception) {
            return false;
        }
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
