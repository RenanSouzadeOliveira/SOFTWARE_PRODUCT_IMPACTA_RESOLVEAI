package br.edu.impacta.resolveai.chamado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import br.edu.impacta.resolveai.categoria.Categoria;
import br.edu.impacta.resolveai.categoria.CategoriaRepository;
import br.edu.impacta.resolveai.usuario.Perfil;
import br.edu.impacta.resolveai.usuario.Usuario;
import br.edu.impacta.resolveai.usuario.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ChamadoIntegrationTest.ProtocolTestConfiguration.class)
class ChamadoIntegrationTest {

    private static final String PASSWORD = "senha-segura-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ChamadoRepository chamadoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ControlledProtocolGenerator protocolGenerator;

    private Categoria categoriaAtiva;
    private Categoria categoriaInativa;
    private Usuario solicitante;
    private String tokenSolicitante;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String solicitanteEmail = "solicitante-" + suffix + "@example.com";
        tokenSolicitante = registerAndLogin(solicitanteEmail);
        solicitante = usuarioRepository.findByEmail(solicitanteEmail).orElseThrow();
        protocolGenerator.clear();
        categoriaAtiva = categoriaRepository.save(Categoria.nova(
                "Ativa " + suffix, "Categoria ativa de teste", Instant.now()));
        categoriaInativa = Categoria.nova("Inativa " + suffix, null, Instant.now());
        categoriaInativa.desativar(Instant.now());
        categoriaInativa = categoriaRepository.save(categoriaInativa);
    }

    @Test
    void shouldRequireRequesterAuthenticationAndRole() throws Exception {
        mockMvc.perform(post("/api/chamados")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload(categoriaAtiva.id())))
                .andExpect(status().isUnauthorized());

        String suffix = UUID.randomUUID().toString();
        Usuario attendant = usuarioRepository.save(Usuario.novo(
                "Atendente", "atendente-" + suffix + "@example.com",
                passwordEncoder.encode(PASSWORD), Perfil.ATENDENTE, Instant.now()));
        String attendantToken = login(attendant.email());

        mockMvc.perform(post("/api/chamados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(attendantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload(categoriaAtiva.id())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void shouldCreateTwoTicketsWithDistinctProtocolsAndServerControlledFields() throws Exception {
        MvcResult firstResult = mockMvc.perform(post("/api/chamados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenSolicitante))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "titulo": "  Portal indisponivel  ",
                                  "descricao": "  O portal apresenta erro ao autenticar o usuario.  ",
                                  "categoriaId": %d,
                                  "prioridade": "ALTA",
                                  "solicitanteId": 999999,
                                  "atendenteId": 999999,
                                  "status": "FECHADO",
                                  "protocolo": "FORJADO",
                                  "solucao": "forjada",
                                  "resolvidoEm": "2026-01-01T00:00:00Z"
                                }
                                """.formatted(categoriaAtiva.id())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("http://localhost/api/chamados/\\d+")))
                .andExpect(jsonPath("$.titulo").value("Portal indisponivel"))
                .andExpect(jsonPath("$.descricao").value("O portal apresenta erro ao autenticar o usuario."))
                .andExpect(jsonPath("$.status").value("ABERTO"))
                .andExpect(jsonPath("$.prioridade").value("ALTA"))
                .andExpect(jsonPath("$.categoria.id").value(categoriaAtiva.id()))
                .andExpect(jsonPath("$.protocolo", matchesPattern("^RA-[A-Za-z0-9_-]{22}$")))
                .andReturn();

        MvcResult secondResult = mockMvc.perform(post("/api/chamados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenSolicitante))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload(categoriaAtiva.id())))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode first = json(firstResult);
        JsonNode second = json(secondResult);
        assertThat(first.get("protocolo").asText()).isNotEqualTo(second.get("protocolo").asText());

        Chamado persisted = chamadoRepository.findById(first.get("id").asLong()).orElseThrow();
        assertThat(persisted.protocolo()).isEqualTo(first.get("protocolo").asText());
        assertThat(persisted.titulo()).isEqualTo("Portal indisponivel");
        assertThat(persisted.descricao()).isEqualTo("O portal apresenta erro ao autenticar o usuario.");
        assertThat(persisted.status()).isEqualTo(StatusChamado.ABERTO);
        assertThat(persisted.prioridade()).isEqualTo(PrioridadeChamado.ALTA);
        Long persistedRequesterId = jdbcTemplate.queryForObject(
                "SELECT solicitante_id FROM chamados WHERE id = ?", Long.class, persisted.id());
        assertThat(persistedRequesterId).isEqualTo(first.at("/solicitante/id").asLong()).isNotEqualTo(999_999L);
        assertThat(persisted.atendente()).isNull();
        assertThat(persisted.solucao()).isNull();
        assertThat(persisted.resolvidoEm()).isNull();
        assertThat(persisted.fechadoEm()).isNull();
    }

    @Test
    void shouldRetryAfterARealUniqueProtocolViolation() throws Exception {
        String collidingProtocol = "RA-protocolo-ja-existente";
        String uniqueProtocol = "RA-protocolo-novo-unico";
        chamadoRepository.saveAndFlush(Chamado.abrir(
                collidingProtocol,
                "Chamado ja existente",
                "Descricao valida para o chamado previamente persistido.",
                solicitante,
                categoriaAtiva,
                Instant.now()));
        protocolGenerator.enqueue(collidingProtocol, uniqueProtocol);

        mockMvc.perform(post("/api/chamados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenSolicitante))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload(categoriaAtiva.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.protocolo").value(uniqueProtocol));

        assertThat(chamadoRepository.findByProtocolo(collidingProtocol)).isPresent();
        assertThat(chamadoRepository.findByProtocolo(uniqueProtocol)).isPresent();
    }

    @Test
    void shouldReturnFieldViolationsForInvalidInput() throws Exception {
        mockMvc.perform(post("/api/chamados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenSolicitante))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo":" x ","descricao":" curta ","categoriaId":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dados invalidos"))
                .andExpect(jsonPath("$.violations[*].field",
                        hasItems("titulo", "descricao", "categoriaId", "prioridade")));

        mockMvc.perform(post("/api/chamados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenSolicitante))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "titulo":"Falha ao acessar recurso",
                                  "descricao":"Nao consigo acessar o recurso solicitado.",
                                  "categoriaId":%d,
                                  "prioridade":"URGENTE"
                                }
                                """.formatted(categoriaAtiva.id())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Corpo da requisicao invalido"))
                .andExpect(jsonPath("$.violations[0].field").value("prioridade"));
    }

    @Test
    void shouldRejectMissingAndInactiveCategoryConsistently() throws Exception {
        for (long categoryId : List.of(999_999_999L, categoriaInativa.id())) {
            mockMvc.perform(post("/api/chamados")
                            .header(HttpHeaders.AUTHORIZATION, bearer(tokenSolicitante))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validPayload(categoryId)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Categoria inexistente ou inativa"));
        }
    }

    @Test
    void shouldListOnlyActiveCategoriesOrderedWithMinimalDtoAndPublishOpenApi() throws Exception {
        mockMvc.perform(get("/api/categorias"))
                .andExpect(status().isUnauthorized());

        MvcResult categoriesResult = mockMvc.perform(get("/api/categorias")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenSolicitante)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode categories = json(categoriesResult);
        List<String> names = new ArrayList<>();
        for (JsonNode category : categories) {
            names.add(category.get("nome").asText());
            assertThat(category.size()).isEqualTo(2);
        }
        assertThat(names).isSorted();
        assertThat(categories.findValuesAsText("id")).contains(categoriaAtiva.id().toString());
        assertThat(categories.findValuesAsText("id")).doesNotContain(categoriaInativa.id().toString());

        JsonNode specification = json(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(specification.at("/paths/~1api~1chamados/post/responses/201").isMissingNode()).isFalse();
        assertThat(specification.at("/paths/~1api~1chamados/post/responses/403").isMissingNode()).isFalse();
        assertThat(specification.at("/paths/~1api~1chamados/post/security/0/bearerAuth").isMissingNode()).isFalse();
        assertThat(specification.at("/paths/~1api~1categorias/get/responses/200").isMissingNode()).isFalse();
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Solicitante Teste","email":"%s","senha":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","senha":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("accessToken").asText();
    }

    private String validPayload(long categoryId) {
        return """
                {
                  "titulo":"Falha ao acessar recurso",
                  "descricao":"Nao consigo acessar o recurso solicitado.",
                  "categoriaId":%d,
                  "prioridade":"MEDIA"
                }
                """.formatted(categoryId);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProtocolTestConfiguration {

        @Bean
        @Primary
        ControlledProtocolGenerator controlledProtocolGenerator() {
            return new ControlledProtocolGenerator();
        }
    }

    static class ControlledProtocolGenerator extends ProtocoloChamadoGenerator {

        private final Queue<String> values = new ConcurrentLinkedQueue<>();

        void enqueue(String... protocols) {
            values.addAll(List.of(protocols));
        }

        void clear() {
            values.clear();
        }

        @Override
        public String gerar() {
            String protocol = values.poll();
            return protocol == null ? super.gerar() : protocol;
        }
    }
}
