package br.edu.impacta.resolveai.config;

import java.util.List;
import java.util.Set;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Falha fechada antes de qualquer bean (inclusive Flyway): sem perfil explicito a API nao inicia,
 * evitando subir producao com configuracao de desenvolvimento por engano.
 */
public class PerfilAtivoEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final Set<String> PERFIS_SUPORTADOS = Set.of("dev", "test", "prod");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        validar(List.of(environment.getActiveProfiles()));
    }

    static void validar(List<String> ativos) {
        List<String> conhecidos = ativos.stream().filter(PERFIS_SUPORTADOS::contains).toList();
        if (conhecidos.size() != 1) {
            throw new IllegalStateException(
                    "Defina exatamente um perfil entre dev, test e prod (SPRING_PROFILES_ACTIVE); ativos: " + ativos);
        }
    }

    @Override
    public int getOrder() {
        // Depois do ConfigDataEnvironmentPostProcessor, que resolve os perfis ativos.
        return Ordered.LOWEST_PRECEDENCE;
    }
}
