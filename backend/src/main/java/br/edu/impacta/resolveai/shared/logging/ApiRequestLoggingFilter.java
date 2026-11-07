package br.edu.impacta.resolveai.shared.logging;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiRequestLoggingFilter.class);

    private final boolean textoDemo;

    public ApiRequestLoggingFilter(Environment environment,
            @Value("${app.logging.chamados.texto-ficticio:false}") boolean textoDemo) {
        this.textoDemo = textoDemo && environment.acceptsProfiles(Profiles.of("dev & !prod"));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        String anterior = MDC.get("requisicaoId");
        MDC.put("requisicaoId", UUID.randomUUID().toString());
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (textoDemo && response.getStatus() == 200 && "GET".equals(request.getMethod())) {
                String caminho = request.getRequestURI();
                if (caminho.equals("/api/chamados/exportacao"))
                    OperacoesChamadosLog.textoDemo("CHAMADOS_EXPORTACAO", request.getParameter("search"));
                else if (java.util.Set.of("/api/chamados", "/api/chamados/me", "/api/atendimento/fila",
                        "/api/atendimento/me", "/api/atendimento/todos").contains(caminho))
                    OperacoesChamadosLog.textoDemo("CHAMADOS_CONSULTA", request.getParameter("search"));
            }
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            LOGGER.info(
                    "HTTP requisicaoId={} metodo={} caminho={} status={} duracaoMs={}",
                    MDC.get("requisicaoId"),
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMs);
            if (anterior == null) MDC.remove("requisicaoId");
            else MDC.put("requisicaoId", anterior);
        }
    }
}
