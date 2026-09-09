package br.edu.impacta.resolveai.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import br.edu.impacta.resolveai.usuario.Perfil;
import br.edu.impacta.resolveai.usuario.Usuario;
import br.edu.impacta.resolveai.usuario.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {

    private static final String PASSWORD = "senha-segura-123";
    private static final String TEST_SECRET = "chave-jwt-exclusiva-para-testes-com-mais-de-32-bytes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldRegisterLoginAndReadCurrentUserWithoutExposingPassword() throws Exception {
        MvcResult registration = register("Pessoa Teste", "  Pessoa@Example.COM  ", PASSWORD, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("pessoa@example.com"))
                .andExpect(jsonPath("$.perfil").value("SOLICITANTE"))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist())
                .andReturn();

        JsonNode registered = json(registration);
        Usuario persisted = usuarioRepository.findByEmail("pessoa@example.com").orElseThrow();
        assertThat(persisted.senhaHashParaAutenticacao()).startsWith("$2");
        assertThat(persisted.senhaHashParaAutenticacao()).doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, persisted.senhaHashParaAutenticacao())).isTrue();

        MvcResult login = login(" PESSOA@example.com ", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(7200))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.usuario.id").value(registered.get("id").asLong()))
                .andExpect(jsonPath("$.usuario.senhaHash").doesNotExist())
                .andReturn();

        mockMvc.perform(get("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, bearer(token(login))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(registered.get("id").asLong()))
                .andExpect(jsonPath("$.email").value("pessoa@example.com"));
    }

    @Test
    void publicRegistrationShouldIgnoreAdminProfileAndRejectCaseInsensitiveDuplicate() throws Exception {
        register("Sem privilegio", "perfil@example.com", PASSWORD, "ADMIN")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.perfil").value("SOLICITANTE"));

        register("Duplicado", "  PERFIL@EXAMPLE.COM ", PASSWORD, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("E-mail ja cadastrado"));
    }

    @Test
    void shouldReturnSameGenericUnauthorizedErrorForWrongPasswordAndInactiveUser() throws Exception {
        login("inexistente@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha invalidos"));

        register("Credencial", "credencial@example.com", PASSWORD, null).andExpect(status().isCreated());
        login("credencial@example.com", "senha-incorreta")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha invalidos"));

        Usuario usuario = usuarioRepository.findByEmail("credencial@example.com").orElseThrow();
        usuario.desativar(Instant.now());
        usuarioRepository.save(usuario);

        login("credencial@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha invalidos"));
    }

    @Test
    void shouldRequireValidActiveBearerToken() throws Exception {
        mockMvc.perform(get("/api/usuarios/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(get("/api/usuarios/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.path").value("/api/usuarios/me"));

        Usuario active = usuarioRepository.save(Usuario.novo(
                "Token", "token@example.com", passwordEncoder.encode(PASSWORD),
                Perfil.SOLICITANTE, Instant.now()));
        mockMvc.perform(get("/api/usuarios/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken(active))))
                .andExpect(status().isUnauthorized());

        MvcResult validLogin = login("token@example.com", PASSWORD).andExpect(status().isOk()).andReturn();
        active.desativar(Instant.now());
        usuarioRepository.save(active);
        mockMvc.perform(get("/api/usuarios/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token(validLogin))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAuthorizeOnlyAttendantEndpoint() throws Exception {
        register("Solicitante", "solicitante-auth@example.com", PASSWORD, null)
                .andExpect(status().isCreated());
        String requesterToken = token(login("solicitante-auth@example.com", PASSWORD)
                .andExpect(status().isOk()).andReturn());
        mockMvc.perform(get("/api/atendimento/acesso")
                        .header(HttpHeaders.AUTHORIZATION, bearer(requesterToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Acesso negado"));

        usuarioRepository.save(Usuario.novo(
                "Atendente", "atendente@example.com", passwordEncoder.encode(PASSWORD),
                Perfil.ATENDENTE, Instant.now()));
        String attendantToken = token(login("atendente@example.com", PASSWORD)
                .andExpect(status().isOk()).andReturn());
        mockMvc.perform(get("/api/atendimento/acesso")
                        .header(HttpHeaders.AUTHORIZATION, bearer(attendantToken)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldPersistNameUpdateAndReturnItOnNewLogin() throws Exception {
        register("Nome Antigo", "alterar@example.com", PASSWORD, null).andExpect(status().isCreated());
        String firstToken = token(login("alterar@example.com", PASSWORD)
                .andExpect(status().isOk()).andReturn());

        mockMvc.perform(patch("/api/usuarios/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"  Nome Atualizado  \",\"perfil\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Nome Atualizado"))
                .andExpect(jsonPath("$.perfil").value("SOLICITANTE"));

        login("alterar@example.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.nome").value("Nome Atualizado"));
    }

    @Test
    void shouldDocumentAuthenticationPathsErrorsAndBearerScheme() throws Exception {
        JsonNode specification = json(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(specification.at("/components/securitySchemes/bearerAuth").isMissingNode()).isFalse();
        assertThat(specification.at("/paths/~1api~1auth~1register/post/responses/409").isMissingNode()).isFalse();
        assertThat(specification.at("/paths/~1api~1auth~1login/post/responses/401").isMissingNode()).isFalse();
        assertThat(specification.at("/paths/~1api~1usuarios~1me/get/responses/401").isMissingNode()).isFalse();
        assertThat(specification.at("/paths/~1api~1atendimento~1acesso/get/responses/403").isMissingNode()).isFalse();
        assertThat(specification.at("/paths/~1api~1usuarios~1me/get/security/0/bearerAuth").isMissingNode())
                .isFalse();
        assertThat(specification.at("/paths/~1api~1atendimento~1acesso/get/security/0/bearerAuth").isMissingNode())
                .isFalse();
    }

    private org.springframework.test.web.servlet.ResultActions register(
            String nome, String email, String password, String perfil) throws Exception {
        String profileProperty = perfil == null ? "" : ",\"perfil\":\"" + perfil + "\"";
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"" + nome + "\",\"email\":\"" + email
                        + "\",\"senha\":\"" + password + "\"" + profileProperty + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + password + "\"}"));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String token(MvcResult result) throws Exception {
        return json(result).get("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String expiredToken(Usuario usuario) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Instant expiration = Instant.now().minusSeconds(60);
        return Jwts.builder()
                .subject(usuario.id().toString())
                .issuer("resolveai-api")
                .claim("perfil", usuario.perfil().name())
                .issuedAt(Date.from(expiration.minusSeconds(60)))
                .expiration(Date.from(expiration))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
