package br.edu.impacta.resolveai.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import br.edu.impacta.resolveai.usuario.Perfil;
import br.edu.impacta.resolveai.usuario.Usuario;
import br.edu.impacta.resolveai.usuario.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/** N-02 — falha de banco no filtro JWT responde no contrato ApiErrorResponse, sem detalhes internos. */
class JwtAuthenticationFilterTest {
    private static final String SEGREDO = "chave-jwt-exclusiva-para-testes-com-mais-de-32-bytes";
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Clock clock = Clock.systemUTC();
    private final JwtService jwt = new JwtService(SEGREDO, Duration.ofHours(2), "resolveai-api", clock);
    private final ApiSecurityErrorWriter writer = new ApiSecurityErrorWriter(json, clock);

    @AfterEach void limpar() { SecurityContextHolder.clearContext(); }

    @Test void deveResponder503JsonSeguroQuandoBancoFalhaNaAutenticacao() throws Exception {
        // Consulta falha (DataAccessException) ou conexao nao obtida ao abrir a transacao do repositorio.
        List<RuntimeException> falhas = List.of(
                new DataAccessResourceFailureException("Connection to jdbc:postgresql://db-interno:5432/resolveai refused; senha=segredo-db"),
                new org.springframework.transaction.CannotCreateTransactionException(
                        "Could not open JPA EntityManager; jdbc:postgresql://db-interno:5432 senha=segredo-db"));
        String token = jwt.generate(usuario(1L, Perfil.SOLICITANTE));
        for (RuntimeException falha : falhas) {
        UsuarioRepository indisponivel = repositorio(() -> { throw falha; });
        for (String accept : List.of("application/xml", "text/csv", "application/json")) {
            AtomicBoolean seguiu = new AtomicBoolean();
            MockHttpServletResponse resposta = executar(indisponivel, token, accept, seguiu);
            assertThat(resposta.getStatus()).as(accept).isEqualTo(503);
            assertThat(resposta.getContentType()).as(accept).startsWith("application/json");
            String corpo = resposta.getContentAsString(StandardCharsets.UTF_8);
            JsonNode erro = json.readTree(corpo);
            assertThat(erro.get("status").asInt()).isEqualTo(503);
            assertThat(erro.get("error").asText()).isEqualTo("Service Unavailable");
            assertThat(erro.get("message").asText()).isEqualTo("Servico temporariamente indisponivel");
            assertThat(erro.get("path").asText()).isEqualTo("/api/usuarios/me");
            assertThat(erro.has("violations")).as("violations vazia omitida (NON_EMPTY)").isFalse();
            assertThat(erro.has("timestamp")).isTrue();
            for (String termo : List.of("jdbc", "postgresql", "senha", "segredo", "Exception", "db-interno", token))
                assertThat(corpo).as(termo).doesNotContain(termo);
            assertThat(seguiu).as("cadeia interrompida").isFalse();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
        }
    }

    @Test void naoDeveMascararDefeitosDeProgramacaoComo503() {
        UsuarioRepository defeituoso = repositorio(() -> { throw new IllegalStateException("defeito"); });
        String token = jwt.generate(usuario(1L, Perfil.SOLICITANTE));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> executar(defeituoso, token, "application/json", new AtomicBoolean()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void devePreservar401ParaFalhasDeAutenticacaoEAutenticarTokenValido() throws Exception {
        Usuario ativo = usuario(7L, Perfil.ATENDENTE);
        UsuarioRepository repositorio = repositorio(() -> Optional.of(ativo));
        String valido = jwt.generate(ativo);
        String expirado = new JwtService(SEGREDO, Duration.ofSeconds(1), "resolveai-api",
                Clock.fixed(Instant.now().minusSeconds(3600), java.time.ZoneOffset.UTC)).generate(ativo);
        for (String invalido : List.of("Bearer nao-e-jwt", "Bearer " + expirado, "Basic abc")) {
            MockHttpServletResponse resposta = executar(repositorio, invalido, "application/json", new AtomicBoolean(), false);
            assertThat(resposta.getStatus()).as(invalido).isEqualTo(401);
        }
        ReflectionTestUtils.setField(ativo, "versaoSeguranca", 1L);
        assertThat(executar(repositorio, valido, "application/json", new AtomicBoolean()).getStatus()).as("revogado").isEqualTo(401);
        ReflectionTestUtils.setField(ativo, "versaoSeguranca", 0L);
        ReflectionTestUtils.setField(ativo, "ativo", false);
        assertThat(executar(repositorio, valido, "application/json", new AtomicBoolean()).getStatus()).as("inativo").isEqualTo(401);
        ReflectionTestUtils.setField(ativo, "ativo", true);
        AtomicBoolean seguiu = new AtomicBoolean();
        assertThat(executar(repositorio, valido, "application/json", seguiu).getStatus()).isEqualTo(200);
        assertThat(seguiu).isTrue();
    }

    private MockHttpServletResponse executar(UsuarioRepository repositorio, String token, String accept, AtomicBoolean seguiu)
            throws Exception {
        return executar(repositorio, "Bearer " + token, accept, seguiu, false);
    }

    private MockHttpServletResponse executar(UsuarioRepository repositorio, String authorization, String accept,
            AtomicBoolean seguiu, boolean ignorado) throws Exception {
        SecurityContextHolder.clearContext();
        var filtro = new JwtAuthenticationFilter(jwt, repositorio, new RestAuthenticationEntryPoint(writer), writer);
        var requisicao = new MockHttpServletRequest("GET", "/api/usuarios/me");
        requisicao.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        requisicao.addHeader(HttpHeaders.ACCEPT, accept);
        var resposta = new MockHttpServletResponse();
        filtro.doFilter(requisicao, resposta, (req, res) -> seguiu.set(true));
        return resposta;
    }

    private static Usuario usuario(Long id, Perfil perfil) {
        Usuario usuario = Usuario.novo("Pessoa ficticia", "pessoa" + id + "@example.com", "hash-ficticio", perfil, Instant.now());
        ReflectionTestUtils.setField(usuario, "id", id);
        return usuario;
    }

    private interface Busca { Optional<Usuario> buscar(); }

    private static UsuarioRepository repositorio(Busca busca) {
        return (UsuarioRepository) Proxy.newProxyInstance(UsuarioRepository.class.getClassLoader(),
                new Class<?>[] {UsuarioRepository.class}, (proxy, metodo, argumentos) -> {
                    if (metodo.getName().equals("findById")) return busca.buscar();
                    if (metodo.getName().equals("toString")) return "repositorio-de-teste";
                    throw new UnsupportedOperationException(metodo.getName());
                });
    }
}
