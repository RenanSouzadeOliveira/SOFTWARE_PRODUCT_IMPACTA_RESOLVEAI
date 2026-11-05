package br.edu.impacta.resolveai.chamado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.time.Instant;
import java.util.*;
import br.edu.impacta.resolveai.categoria.*;
import br.edu.impacta.resolveai.security.JwtService;
import br.edu.impacta.resolveai.usuario.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FiltrosChamadosIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired CategoriaRepository categorias;
    @Autowired ChamadoRepository chamados;
    @Autowired JwtService jwt;
    Usuario own, other, agent, agent2, admin;
    Categoria cat, cat2;
    Chamado first, last, outside, hidden, assignedOther;
    final Instant start = Instant.parse("2026-10-01T00:00:00Z");

    @BeforeEach void setup() {
        own = user(Perfil.SOLICITANTE); other = user(Perfil.SOLICITANTE);
        agent = user(Perfil.ATENDENTE); agent2 = user(Perfil.ATENDENTE); admin = user(Perfil.ADMIN);
        cat = categorias.save(Categoria.nova("Filtros " + UUID.randomUUID(), null, start));
        cat2 = categorias.save(Categoria.nova("Outra " + UUID.randomUUID(), null, start));
        first = ticket(own, agent, cat, PrioridadeChamado.ALTA, start, "Portal 100%_! literal", "Descrição fictícia de acesso ao sistema");
        last = ticket(own, agent, cat, PrioridadeChamado.ALTA, Instant.parse("2026-10-02T23:59:59.999999Z"), "Falha impressora", "Descrição com palavraDescricao somente aqui");
        last.alterarStatus(agent, StatusChamado.RESOLVIDO, "Solução fictícia registrada", start.plusSeconds(1));
        chamados.saveAndFlush(last);
        outside = ticket(own, null, cat2, PrioridadeChamado.BAIXA, Instant.parse("2026-10-03T00:00:00Z"), "Portal comum", "Descrição fictícia de outro problema");
        hidden = ticket(other, agent2, cat, PrioridadeChamado.MEDIA, start.plusSeconds(1), "Portal oculto", "Descrição fictícia sem acesso para outro solicitante");
        assignedOther = ticket(other, agent, cat, PrioridadeChamado.ALTA, start.plusSeconds(2), "Portal de outro", "Descrição fictícia sob responsabilidade do atendente");
    }
    @Test void eachFilterAndTextFields() throws Exception {
        expectIds(own, Map.of("status", "RESOLVIDO"), last.id());
        expectIds(own, Map.of("prioridade", "BAIXA"), outside.id());
        expectIds(own, Map.of("categoriaId", cat2.id().toString()), outside.id());
        expectIds(own, Map.of("categoria", cat.id().toString()), last.id(), first.id());
        expectIds(own, Map.of("responsavel", agent.id().toString()), last.id(), first.id());
        expectIds(own, Map.of("search", "  pOrTaL  "), outside.id(), first.id());
        expectIds(own, Map.of("search", first.protocolo().substring(4).toLowerCase(Locale.ROOT)), first.id());
        expectIds(own, Map.of("search", "PALAVRADESCRICAO"), last.id());
        expectIds(own, Map.of("search", "DESCRIÇÃO FICTÍCIA DE ACESSO"), first.id());
        expectIds(own, Map.of("categoria", cat.id().toString(), "categoriaId", cat.id().toString()), last.id(), first.id());
        expectIds(own, Map.of("dataInicial", "2026-10-03"), outside.id());
        expectIds(own, Map.of("dataFinal", "2026-10-01"), first.id());
        expectIds(own, Map.of("dataFinal", "9999-12-31"), outside.id(), last.id(), first.id());
    }
    @Test void literalWildcardsEmptyAndMissingSearch() throws Exception {
        for (String value : List.of("%", "_", "!", "100%_!")) expectIds(own, Map.of("search", value), first.id());
        expectIds(own, Map.of("search", ""), outside.id(), last.id(), first.id());
        expectIds(own, Map.of("search", "   "), outside.id(), last.id(), first.id());
        expectIds(own, Map.of("search", "inexistente-" + UUID.randomUUID()));
        expectIds(own, Map.of("search", "' OR 1=1 --"));
    }
    @Test void andCombinationAndInclusiveUtcBoundaries() throws Exception {
        var filters = new HashMap<>(Map.of("status", "EM_ATENDIMENTO", "prioridade", "ALTA",
                "categoriaId", cat.id().toString(), "responsavel", agent.id().toString(),
                "dataInicial", "2026-10-01", "dataFinal", "2026-10-02", "search", "portal"));
        expectIds(own, filters, first.id());
        expectIds(own, Map.of("dataInicial", "2026-10-01", "dataFinal", "2026-10-02"), last.id(), first.id());
        expectIds(own, Map.of("dataInicial", "2026-10-02", "dataFinal", "2026-10-02"), last.id());
        filters.put("status", "FECHADO"); expectIds(own, filters);
    }
    @Test void visibilityCountsAndOptionsForAllRoles() throws Exception {
        var filter = Map.of("categoriaId", cat.id().toString(), "size", "1");
        assertThat(request(own, filter).get("totalElements").asInt()).isEqualTo(2);
        assertThat(request(agent, filter).get("totalElements").asInt()).isEqualTo(3);
        assertThat(request(agent2, filter).get("totalElements").asInt()).isEqualTo(1);
        assertThat(request(admin, filter).get("totalElements").asInt()).isEqualTo(4);
        expectIds(own, Map.of("responsavel", agent2.id().toString()));
        expectIds(agent2, Map.of("responsavel", agent.id().toString()));
        for (Usuario u : List.of(own, agent, agent2, admin)) {
            JsonNode options = json.readTree(mvc.perform(get("/api/chamados/filtros").header("Authorization", "Bearer " + jwt.generate(u)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(options.toString()).doesNotContain("email", "senha", "perfil");
            if (u == own) {
                assertThat(options.get("responsaveis").size()).isEqualTo(1);
                assertThat(options.get("responsaveis").get(0).get("id").asLong()).isEqualTo(agent.id());
            }
        }
        Usuario empty = user(Perfil.SOLICITANTE);
        JsonNode emptyOptions = json.readTree(mvc.perform(get("/api/chamados/filtros").header("Authorization", "Bearer " + jwt.generate(empty)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(emptyOptions.get("categorias").isEmpty()).isTrue();
        assertThat(emptyOptions.get("responsaveis").isEmpty()).isTrue();
        for (Usuario u : List.of(agent, agent2)) {
            JsonNode options = json.readTree(mvc.perform(get("/api/chamados/filtros").header("Authorization", "Bearer " + jwt.generate(u)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(options.get("categorias").size()).isEqualTo(1);
            assertThat(options.get("categorias").get(0).get("id").asLong()).isEqualTo(cat.id());
            assertThat(options.get("responsaveis").size()).isEqualTo(1);
            assertThat(options.get("responsaveis").get(0).get("id").asLong()).isEqualTo(u.id());
        }
        cat.desativar(start); categorias.save(cat);
        agent.desativar(start); usuarios.save(agent);
        JsonNode inactiveOptions = json.readTree(mvc.perform(get("/api/chamados/filtros").header("Authorization", "Bearer " + jwt.generate(own)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(inactiveOptions.get("responsaveis").get(0).get("id").asLong()).isEqualTo(agent.id());
        assertThat(inactiveOptions.get("categorias").toString()).contains(cat.id().toString());
        mvc.perform(get("/api/chamados")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/chamados/filtros")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/atendimento/me").header("Authorization", "Bearer " + jwt.generate(own))).andExpect(status().isForbidden());
        mvc.perform(get("/api/chamados/me").header("Authorization", "Bearer " + jwt.generate(agent2))).andExpect(status().isForbidden());
    }
    @Test void validatesFiltersConsistently() throws Exception {
        var invalid = List.of(Map.of("status", "INVALIDO"), Map.of("prioridade", "URGENTE"), Map.of("status", ""),
                Map.of("categoriaId", "0"), Map.of("categoria", "-1"), Map.of("responsavel", "1.5"),
                Map.of("responsavel", "abc"), Map.of("categoriaId", "999999999999999999999999"),
                Map.of("dataInicial", "2026-02-30"), Map.of("dataFinal", "05/11/2026"), Map.of("dataFinal", ""),
                Map.of("dataInicial", "2026-10-03", "dataFinal", "2026-10-01"),
                Map.of("categoriaId", "1", "categoria", "2"), Map.of("dataFinal", "0000-01-01"));
        for (String path : List.of("/api/chamados", "/api/chamados/me", "/api/atendimento/me", "/api/atendimento/fila")) {
            Usuario u = path.contains("atendimento") ? agent : own;
            for (var filters : invalid) {
                var req = get(path).header("Authorization", "Bearer " + jwt.generate(u)); filters.forEach(req::param);
                mvc.perform(req).andExpect(status().isBadRequest());
            }
            for (String key : List.of("search", "status", "prioridade", "responsavel", "categoria", "categoriaId", "dataInicial", "dataFinal"))
                mvc.perform(get(path).header("Authorization", "Bearer " + jwt.generate(u)).param(key, "1", "1")).andExpect(status().isBadRequest());
        }
    }
    @Test void filteredPaginationStableAndCompatibleListings() throws Exception {
        List<Long> expected = new ArrayList<>();
        for (int i=0; i<25; i++) expected.add(ticket(own, null, cat, PrioridadeChamado.MEDIA, start, "Lote busca", "Descrição fictícia para paginação combinada").id());
        Collections.reverse(expected);
        var actual = new ArrayList<Long>();
        for (int page=0; page<3; page++) {
            var result = request(own, Map.of("search", "lote", "categoriaId", cat.id().toString(), "size", "10", "page", ""+page));
            assertThat(result.get("totalElements").asInt()).isEqualTo(25);
            assertThat(result.get("totalPages").asInt()).isEqualTo(3);
            result.get("content").forEach(c -> actual.add(c.get("id").asLong()));
        }
        assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
        var sorted = request(own, Map.of("search", "lote", "sort", "id,asc", "size", "100"));
        assertThat(sorted.get("content").get(0).get("id").asLong()).isEqualTo(expected.getLast());
        for (String path : List.of("/api/chamados/me", "/api/atendimento/fila")) {
            Usuario u = path.contains("atendimento") ? agent : own;
            var result = mvc.perform(get(path).header("Authorization", "Bearer " + jwt.generate(u)).param("search", "lote").param("categoriaId", cat.id().toString()))
                    .andExpect(status().isOk()).andReturn();
            assertThat(json.readTree(result.getResponse().getContentAsString()).get("totalElements").asInt()).isEqualTo(25);
        }
        var wallet = mvc.perform(get("/api/atendimento/me").header("Authorization", "Bearer " + jwt.generate(agent)).param("search", "impressora"))
                .andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(wallet.getResponse().getContentAsString()).get("totalElements").asInt()).isEqualTo(1);
    }
    @Test void documentsPublicContract() throws Exception {
        JsonNode docs = json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var names = new ArrayList<String>();
        docs.at("/paths/~1api~1chamados/get/parameters").forEach(p -> names.add(p.get("name").asText()));
        assertThat(names).contains("search", "status", "prioridade", "categoriaId", "responsavel", "dataInicial", "dataFinal", "page", "size", "sort");
        assertThat(docs.at("/paths/~1api~1chamados~1filtros/get").isMissingNode()).isFalse();
    }

    private JsonNode request(Usuario u, Map<String,String> filters) throws Exception {
        MockHttpServletRequestBuilder req = get("/api/chamados").header("Authorization", "Bearer " + jwt.generate(u));
        filters.forEach(req::param);
        return json.readTree(mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private void expectIds(Usuario u, Map<String,String> filters, Long... ids) throws Exception {
        var values = new ArrayList<Long>(); request(u, filters).get("content").forEach(c -> values.add(c.get("id").asLong()));
        assertThat(values).containsExactly(ids);
    }
    private Usuario user(Perfil perfil) { return usuarios.save(Usuario.novo("Pessoa fictícia", UUID.randomUUID()+"@example.com", "hash-de-teste", perfil, start)); }
    private Chamado ticket(Usuario requester, Usuario assignee, Categoria category, PrioridadeChamado priority, Instant date, String title, String description) {
        var c = Chamado.abrir("F-"+UUID.randomUUID().toString().substring(0,24), title, description, priority, requester, category, date);
        if (assignee != null) c.assumir(assignee, date);
        return chamados.saveAndFlush(c);
    }
}
