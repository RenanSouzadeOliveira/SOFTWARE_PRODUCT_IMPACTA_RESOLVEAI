package br.edu.impacta.resolveai.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import br.edu.impacta.resolveai.categoria.Categoria;
import br.edu.impacta.resolveai.categoria.CategoriaRepository;
import br.edu.impacta.resolveai.chamado.*;
import br.edu.impacta.resolveai.security.JwtService;
import br.edu.impacta.resolveai.usuario.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.hibernate.resource.jdbc.spi.StatementInspector;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PaginacaoIntegrationTest.SqlConfiguration.class)
class PaginacaoIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired CategoriaRepository categorias;
    @Autowired ChamadoRepository chamados;
    @Autowired JwtService jwt;
    @Autowired SqlCapture sql;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    Usuario solicitante, outro, atendente, outroAtendente, admin;
    Categoria categoria;
    final Instant created = Instant.parse("2026-01-01T12:00:00Z");

    @BeforeEach void setup() {
        solicitante = usuario(Perfil.SOLICITANTE);
        outro = usuario(Perfil.SOLICITANTE);
        atendente = usuario(Perfil.ATENDENTE);
        outroAtendente = usuario(Perfil.ATENDENTE);
        admin = usuario(Perfil.ADMIN);
        categoria = categorias.save(Categoria.nova("AC3 " + UUID.randomUUID(), null, created));
    }

    @Test void shouldPageStableDatasetWithoutGapsOrDuplicatesAndLimitInSql() throws Exception {
        List<Long> expected = new ArrayList<>();
        for (int i = 0; i < 45; i++) expected.add(abrir(solicitante, null).id());
        abrir(outro, null);
        // Vários comentários no mesmo chamado não podem multiplicar conteúdo nem totais.
        for (int i = 0; i < 3; i++) jdbc.update("INSERT INTO comentarios (chamado_id, autor_id, conteudo, criado_em) VALUES (?, ?, ?, ?)",
                expected.getFirst(), solicitante.id(), "Comentário fictício AC3", java.sql.Timestamp.from(created));
        expected = expected.reversed();
        List<Long> actual = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            sql.statements.clear();
            JsonNode result = request("/api/chamados/me?page=" + page, solicitante);
            assertThat(result.get("page").asInt()).isEqualTo(page);
            assertThat(result.get("size").asInt()).isEqualTo(20);
            assertThat(result.get("totalElements").asInt()).isEqualTo(45);
            assertThat(result.get("totalPages").asInt()).isEqualTo(3);
            assertThat(result.get("content").size()).isEqualTo(page == 2 ? 5 : 20);
            result.get("content").forEach(c -> actual.add(c.get("id").asLong()));
            assertThat(sql.statements).anyMatch(s -> s.toLowerCase().contains("fetch first"));
            if (page < 2) assertThat(sql.statements).anyMatch(s -> s.toLowerCase().contains("count("));
            assertThat(sql.statements).filteredOn(s -> s.toLowerCase().contains("count(")).allMatch(s -> s.contains("solicitante_id"));
        }
        assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
        assertThat(ids(request("/api/chamados/me?page=1", solicitante)))
                .containsExactlyElementsOf(expected.subList(20, 40));
        JsonNode outside = request("/api/chamados/me?page=9", solicitante);
        assertThat(outside.get("content").isEmpty()).isTrue();
        assertThat(outside.get("totalElements").asInt()).isEqualTo(45);
        assertThat(request("/api/chamados/me?size=100", solicitante).get("content").size()).isEqualTo(45);
        assertThat(ids(request("/api/chamados/me?size=2&sort=criadoEm,asc", solicitante)))
                .containsExactlyElementsOf(expected.subList(0, 2)); // equal timestamps always use id DESC
        for (String field : List.of("criadoEm", "atualizadoEm", "titulo", "status", "prioridade", "id")) {
            request("/api/chamados/me?size=2&sort=" + field + ",asc", solicitante);
            request("/api/chamados/me?size=2&sort=" + field + ",desc", solicitante);
        }
        assertThat(ids(request("/api/chamados/me?size=2&sort=id,asc", solicitante)))
                .containsExactlyElementsOf(expected.reversed().subList(0, 2));
    }

    @Test void shouldApplySelectedOrderingAndStableTieBreaker() throws Exception {
        Chamado z = chamados.saveAndFlush(Chamado.abrir("AC3-" + UUID.randomUUID().toString().substring(0, 24),
                "ZZZZZ", "Descrição fictícia para ordenação", solicitante, categoria, created));
        Chamado a1 = chamados.saveAndFlush(Chamado.abrir("AC3-" + UUID.randomUUID().toString().substring(0, 24),
                "AAAAA", "Descrição fictícia para ordenação", solicitante, categoria, created.plusSeconds(1)));
        Chamado a2 = chamados.saveAndFlush(Chamado.abrir("AC3-" + UUID.randomUUID().toString().substring(0, 24),
                "AAAAA", "Descrição fictícia para ordenação", solicitante, categoria, created.plusSeconds(1)));
        assertThat(ids(request("/api/chamados/me?sort=titulo,asc", solicitante))).containsExactly(a2.id(), a1.id(), z.id());
        assertThat(ids(request("/api/chamados/me?sort=titulo,desc", solicitante))).containsExactly(z.id(), a2.id(), a1.id());
        assertThat(ids(request("/api/chamados/me?sort=criadoEm,asc", solicitante))).containsExactly(z.id(), a2.id(), a1.id());
    }

    @Test void shouldScopeQueueWalletAndCountsForEveryRoleAndPreserveCombinedFilters() throws Exception {
        List<Long> queue = new ArrayList<>(), wallet = new ArrayList<>();
        for (int i = 0; i < 25; i++) queue.add(abrir(solicitante, null).id());
        for (int i = 0; i < 23; i++) wallet.add(abrir(solicitante, atendente).id());
        abrir(outro, outroAtendente);
        Chamado ownAdmin = abrir(outro, admin);
        String filters = "?categoriaId=" + categoria.id() + "&prioridade=MEDIA&size=20&sort=criadoEm,desc";
        for (Usuario actor : List.of(atendente, admin)) {
            JsonNode first = request("/api/atendimento/fila" + filters, actor);
            assertThat(first.get("totalElements").asInt()).isEqualTo(25);
            assertThat(first.get("totalPages").asInt()).isEqualTo(2);
            assertThat(ids(first)).containsExactlyElementsOf(queue.reversed().subList(0, 20));
            assertThat(ids(request("/api/atendimento/fila" + filters + "&page=1", actor)))
                    .containsExactlyElementsOf(queue.reversed().subList(20, 25));
        }
        String carteira = "/api/atendimento/me" + filters + "&status=EM_ATENDIMENTO";
        assertThat(request(carteira, atendente).get("totalElements").asInt()).isEqualTo(23);
        assertThat(ids(request(carteira + "&page=1", atendente))).containsExactlyElementsOf(wallet.reversed().subList(20, 23));
        assertThat(request(carteira, outroAtendente).get("totalElements").asInt()).isEqualTo(1);
        assertThat(ids(request(carteira, admin))).containsExactly(ownAdmin.id());
        assertThat(request(carteira.replace("EM_ATENDIMENTO", "FECHADO"), atendente).get("totalElements").asInt()).isZero();
        for (String url : List.of("/api/atendimento/fila", "/api/atendimento/me")) {
            mvc.perform(get(url).header("Authorization", token(solicitante))).andExpect(status().isForbidden());
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
        }
        for (Usuario actor : List.of(atendente, admin)) {
            mvc.perform(get("/api/chamados/me").header("Authorization", token(actor))).andExpect(status().isForbidden());
        }
    }

    @Test void shouldRejectMalformedParametersOnAllListingsAndHandleEmptyResults() throws Exception {
        for (String url : List.of("/api/chamados/me", "/api/atendimento/fila", "/api/atendimento/me")) {
            Usuario actor = url.equals("/api/chamados/me") ? solicitante : atendente;
            for (String param : List.of("page=-1", "page=", "page=1.5", "page=x", "page=2147483648", "page=2147483647&size=100",
                    "size=", "size=0", "size=101", "size=1.5", "size=9999999999999", "size=abc", "sort=",
                    "sort=senhaHash,asc", "sort=categoria.nome,asc", "sort=criadoEm", "sort=criadoEm,invalid", "sort=id,asc,desc",
                    "page=0&page=1", "size=20&size=30", "sort=id,asc&sort=id,desc", "sort=id&sort=asc", "sort=criadoEm&sort=desc")) {
                mvc.perform(get(url + "?" + param).header("Authorization", token(actor))).andExpect(status().isBadRequest());
            }
            String scoped = url + (url.equals("/api/chamados/me") ? "" : "?categoriaId=" + categoria.id());
            JsonNode empty = request(scoped, actor);
            assertThat(empty.get("totalElements").asInt()).isZero();
            assertThat(empty.get("totalPages").asInt()).isZero();
            assertThat(empty.get("page").asInt()).isZero();
            assertThat(empty.get("size").asInt()).isEqualTo(20);
            assertThat(empty.get("content").isEmpty()).isTrue();
            String separator = scoped.contains("?") ? "&" : "?";
            assertThat(request(scoped + separator + "size=100", actor).get("size").asInt()).isEqualTo(100);
        }
    }

    @Test void shouldPublishExplicitPaginationContractWithoutInternalParameterMap() throws Exception {
        JsonNode spec = json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        for (String path : List.of("/api/chamados/me", "/api/atendimento/fila", "/api/atendimento/me")) {
            JsonNode params = spec.path("paths").path(path).path("get").path("parameters");
            List<String> names = new ArrayList<>();
            params.forEach(p -> names.add(p.path("name").asText()));
            assertThat(names).contains("page", "size", "sort").doesNotContain("parameters");
            for (JsonNode param : params) if (param.path("name").asText().equals("size")) {
                assertThat(param.path("schema").path("default").asInt()).isEqualTo(20);
                assertThat(param.path("schema").path("maximum").asInt()).isEqualTo(100);
            }
        }
    }

    private Usuario usuario(Perfil perfil) {
        return usuarios.save(Usuario.novo("Pessoa AC3", UUID.randomUUID() + "@example.invalid", "hash-ficticio", perfil, created));
    }
    private Chamado abrir(Usuario requester, Usuario assignee) {
        Chamado ticket = Chamado.abrir("AC3-" + UUID.randomUUID().toString().substring(0, 24), "Título AC3",
                "Descrição fictícia para paginação AC3", requester, categoria, created);
        if (assignee != null) ticket.assumir(assignee, created.plusSeconds(1));
        return chamados.saveAndFlush(ticket);
    }
    private String token(Usuario actor) { return "Bearer " + jwt.generate(actor); }
    private JsonNode request(String url, Usuario actor) throws Exception {
        return json.readTree(mvc.perform(get(url).header("Authorization", token(actor)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }
    private List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("content").forEach(c -> ids.add(c.get("id").asLong()));
        return ids;
    }
    @TestConfiguration static class SqlConfiguration {
        @Bean SqlCapture sqlCapture() { return new SqlCapture(); }
        @Bean HibernatePropertiesCustomizer sqlInspector(SqlCapture capture) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", capture);
        }
    }
    static class SqlCapture implements StatementInspector {
        final List<String> statements = new CopyOnWriteArrayList<>();
        @Override public String inspect(String statement) { statements.add(statement); return statement; }
    }
}
