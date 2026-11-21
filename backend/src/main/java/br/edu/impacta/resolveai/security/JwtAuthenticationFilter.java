package br.edu.impacta.resolveai.security;

import java.io.IOException;
import java.util.List;

import br.edu.impacta.resolveai.usuario.Usuario;
import br.edu.impacta.resolveai.usuario.UsuarioRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final ApiSecurityErrorWriter errorWriter;

    public JwtAuthenticationFilter(JwtService jwtService, UsuarioRepository usuarioRepository,
            RestAuthenticationEntryPoint authenticationEntryPoint, ApiSecurityErrorWriter errorWriter) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!authorization.startsWith(BEARER_PREFIX) || authorization.length() == BEARER_PREFIX.length()) {
            authenticationEntryPoint.commence(request, response, null);
            return;
        }

        try {
            JwtIdentity identity = jwtService.parse(authorization.substring(BEARER_PREFIX.length()));
            Usuario usuario = usuarioRepository.findById(identity.usuarioId())
                    .filter(Usuario::ativo)
                    .filter(u -> u.versaoSeguranca() == identity.versaoSeguranca() && u.perfil() == identity.perfil())
                    .orElseThrow(InvalidTokenException::new);
            UsuarioPrincipal principal = new UsuarioPrincipal(
                    usuario.id(), usuario.email(), usuario.perfil(), usuario.versaoSeguranca());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + usuario.perfil().name())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException | InvalidTokenException exception) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response, null);
            return;
        } catch (DataAccessException | CannotCreateTransactionException exception) {
            // Banco indisponivel nao e falha de credencial: 503 no contrato ApiErrorResponse.
            // Log sem token nem mensagem do driver (pode conter URL/credenciais); detalhe so em DEBUG.
            SecurityContextHolder.clearContext();
            LOGGER.error("Falha de acesso a dados na autenticacao JWT: {} {} ({})",
                    request.getMethod(), request.getRequestURI(), exception.getClass().getSimpleName());
            LOGGER.debug("Detalhe da falha de acesso a dados na autenticacao JWT", exception);
            errorWriter.write(response, HttpStatus.SERVICE_UNAVAILABLE, "Servico temporariamente indisponivel",
                    request.getRequestURI());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static final class InvalidTokenException extends RuntimeException {
    }
}
