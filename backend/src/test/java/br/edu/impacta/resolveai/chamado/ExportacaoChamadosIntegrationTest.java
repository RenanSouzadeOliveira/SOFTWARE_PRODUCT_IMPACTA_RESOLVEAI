package br.edu.impacta.resolveai.chamado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import br.edu.impacta.resolveai.categoria.*;
import br.edu.impacta.resolveai.security.JwtService;
import br.edu.impacta.resolveai.usuario.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletResponse;

@SpringBootTest(properties = "app.chamados.exportacao.max-registros=105")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExportacaoChamadosIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired CategoriaRepository categorias;
    @Autowired ChamadoRepository chamados;
    @Autowired JwtService jwt;
    Usuario own, other, agent, agent2, admin;
    Categoria cat;
    Chamado first, second, hidden;
    final Instant start = Instant.parse("2026-10-01T00:00:00Z");

    @BeforeEach void setup() {
        own = user(Perfil.SOLICITANTE); other = user(Perfil.SOLICITANTE);
        agent = user(Perfil.ATENDENTE); agent2 = user(Perfil.ATENDENTE); admin = user(Perfil.ADMIN);
        cat = categorias.save(Categoria.nova("Exportação " + UUID.randomUUID(), null, start));
        first = ticket(own, agent, "Ação; \"rede\"\nsegunda linha", start);
        second = ticket(own, null, "Outro chamado", start.plusSeconds(86400));
        hidden = ticket(other, agent2, "Chamado de terceiro", start);
    }

    @Test void headersEmptyAndNoSensitiveFields() throws Exception {
        var response = export(own, Map.of());
        assertThat(response.getContentType()).isEqualTo("text/csv; charset=UTF-8");
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"chamados.csv\"");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        byte[] bytes = response.getContentAsByteArray();
        assertThat(Arrays.copyOf(bytes, 3)).containsExactly((byte)0xef, (byte)0xbb, (byte)0xbf);
        var rows = parse(response);
        assertThat(rows.getFirst()).containsExactly("Protocolo", "Título", "Status", "Prioridade", "Categoria", "Atendente responsável", "Data de criação (UTC)");
        assertThat(rows).hasSize(3);
        assertThat(rows.get(2)).containsExactly(first.protocolo(), first.titulo(), "EM_ATENDIMENTO", "ALTA", cat.nome(), agent.nome(), "2026-10-01T00:00:00Z");
        assertThat(rows.get(1).get(5)).isEmpty();
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).doesNotContain("hash-de-teste", "@example.com", "Descrição privada", "solicitante", "solucao", "token");
        assertThat(parse(export(own, Map.of("search", "inexistente-" + UUID.randomUUID())))).hasSize(1);
    }

    @Test void visibilityUsesAuthenticatedActorAndCannotBeOverridden() throws Exception {
        assertProtocols(own, Map.of("solicitanteId", other.id().toString()), second, first);
        assertProtocols(agent, Map.of(), first);
        assertProtocols(agent2, Map.of(), hidden);
        assertProtocols(admin, Map.of(), second, hidden, first);
        assertProtocols(own, Map.of("responsavel", agent2.id().toString()));
        assertProtocols(agent, Map.of("responsavel", agent2.id().toString()));
        mvc.perform(get("/api/chamados/exportacao")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/chamados/exportacao").header("Authorization", "Bearer invalido")).andExpect(status().isUnauthorized());
        String token = jwt.generate(own);
        own.desativar(start); usuarios.save(own);
        mvc.perform(get("/api/chamados/exportacao").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test void matchesExistingSearchFiltersAndSorting() throws Exception {
        var filters = List.of(Map.of("status", "EM_ATENDIMENTO"), Map.of("prioridade", "ALTA"),
                Map.of("categoria", cat.id().toString()), Map.of("responsavel", agent.id().toString()),
                Map.of("dataInicial", "2026-10-02"), Map.of("dataFinal", "2026-10-01"),
                Map.of("search", "AÇÃO"), Map.of("search", "Descrição privada"),
                Map.of("search", first.protocolo()), Map.of("search", "' OR 1=1 --"),
                Map.of("search", "ação", "status", "EM_ATENDIMENTO", "prioridade", "ALTA",
                        "categoriaId", cat.id().toString(), "responsavel", agent.id().toString(),
                        "dataInicial", "2026-10-01", "dataFinal", "2026-10-01"));
        for (var filter : filters) {
            for (String sort : List.of("criadoEm,desc", "titulo,asc", "id,asc", "prioridade,asc")) {
                var params = new HashMap<>(filter); params.put("sort", sort);
                var listRequest = get("/api/chamados").header("Authorization", "Bearer " + jwt.generate(own));
                params.forEach(listRequest::param);
                var list = json.readTree(mvc.perform(listRequest).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
                var expected = new ArrayList<String>(); list.get("content").forEach(c -> expected.add(c.get("protocolo").asText()));
                assertThat(parse(export(own, params)).stream().skip(1).map(r -> r.getFirst()).toList()).containsExactlyElementsOf(expected);
            }
        }
    }

    @Test void includesMultipleBatchesPreservesIdTieBreakAndEnforcesLimitBeforeCsv() throws Exception {
        var expected = new ArrayList<String>();
        for (int i=0; i<103; i++) expected.add(ticket(own, null, "Lote exportação", start).protocolo());
        Collections.reverse(expected);
        // 105 registros no limite, mesmo que o cliente esteja na página 2 com size=1.
        var response = export(own, Map.of("page", "2", "size", "1"));
        var rows = parse(response);
        assertThat(rows).hasSize(106);
        assertThat(rows.stream().skip(2).limit(103).map(List::getFirst).toList()).containsExactlyElementsOf(expected);
        var more = ticket(own, null, "Excede limite", start);
        var error = mvc.perform(get("/api/chamados/exportacao").header("Authorization", "Bearer " + jwt.generate(own)))
                .andExpect(status().isBadRequest()).andReturn().getResponse();
        assertThat(error.getContentType()).startsWith("application/json");
        assertThat(error.getHeader("Content-Disposition")).isNull();
        assertThat(json.readTree(error.getContentAsString()).get("message").asText()).contains("105", "Refine os filtros");
        assertProtocols(own, Map.of("search", more.protocolo()), more);
    }

    @Test void validatesAllParametersAndDuplicates() throws Exception {
        var invalid = List.of(Map.of("status", "INVALIDO"), Map.of("prioridade", "URGENTE"),
                Map.of("categoriaId", "0"), Map.of("responsavel", "1 OR 1=1"), Map.of("categoria", "-1"),
                Map.of("dataInicial", "2026-02-30"), Map.of("dataFinal", "05/11/2026"),
                Map.of("dataInicial", "2026-10-03", "dataFinal", "2026-10-01"),
                Map.of("categoriaId", "1", "categoria", "2"), Map.of("page", "-1"), Map.of("size", "101"),
                Map.of("sort", "senha,asc"), Map.of("sort", "titulo,invalid"));
        for (var filter : invalid) {
            var req = get("/api/chamados/exportacao").header("Authorization", "Bearer " + jwt.generate(own)); filter.forEach(req::param);
            mvc.perform(req).andExpect(status().isBadRequest());
        }
        for (String key : List.of("search", "status", "prioridade", "responsavel", "categoria", "categoriaId", "dataInicial", "dataFinal", "page", "size", "sort"))
            mvc.perform(get("/api/chamados/exportacao").header("Authorization", "Bearer " + jwt.generate(own)).param(key, "1", "1"))
                    .andExpect(status().isBadRequest());
    }

    @Test void protectsEveryUntrustedExportColumn() throws Exception {
        ReflectionTestUtils.setField(first, "protocolo", "=1+2");
        ReflectionTestUtils.setField(first, "titulo", "\u00a0\uFEFF=1+2\";=3+4");
        ReflectionTestUtils.setField(cat, "nome", "\t@SUM(1)");
        ReflectionTestUtils.setField(agent, "nome", "\u0001-1+2");
        chamados.saveAndFlush(first); categorias.save(cat); usuarios.save(agent);
        var rows = parse(export(own, Map.of("status", "EM_ATENDIMENTO")));
        for (int column : List.of(0, 1, 4, 5)) assertThat(rows.get(1).get(column)).startsWith("Texto: ");
        assertThat(rows.get(1)).hasSize(7);
    }

    private MockHttpServletResponse export(Usuario u, Map<String,String> params) throws Exception {
        // Todos os testes ficam isolados dos dados persistidos por outras classes/casos.
        var req = get("/api/chamados/exportacao").header("Authorization", "Bearer " + jwt.generate(u));
        if (!params.containsKey("categoriaId") && !params.containsKey("categoria")) req.param("categoriaId", cat.id().toString());
        params.forEach(req::param);
        return mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse();
    }
    private void assertProtocols(Usuario u, Map<String,String> params, Chamado... expected) throws Exception {
        assertThat(parse(export(u, params)).stream().skip(1).map(List::getFirst).toList())
                .containsExactly(Arrays.stream(expected).map(Chamado::protocolo).toArray(String[]::new));
    }
    private Usuario user(Perfil perfil) { return usuarios.save(Usuario.novo("Pessoa fictícia", UUID.randomUUID()+"@example.com", "hash-de-teste", perfil, start)); }
    private Chamado ticket(Usuario requester, Usuario assignee, String title, Instant date) {
        var c = Chamado.abrir("E-"+UUID.randomUUID().toString().substring(0,24), title, "Descrição privada fictícia de teste", PrioridadeChamado.ALTA, requester, cat, date);
        if (assignee != null) c.assumir(assignee, date);
        return chamados.saveAndFlush(c);
    }
    /** Leitor de teste que preserva separadores e quebras de linha dentro das aspas. */
    private List<List<String>> parse(MockHttpServletResponse response) {
        String csv = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8).substring(1);
        var rows = new ArrayList<List<String>>(); var row = new ArrayList<String>(); var cell = new StringBuilder(); boolean quoted = false;
        for (int i=0; i<csv.length(); i++) {
            char c = csv.charAt(i);
            if (c == '"') {
                if (quoted && i+1 < csv.length() && csv.charAt(i+1) == '"') { cell.append('"'); i++; }
                else quoted = !quoted;
            } else if (!quoted && (c == ';' || c == '\r')) {
                row.add(cell.toString()); cell.setLength(0);
                if (c == '\r') { rows.add(List.copyOf(row)); row.clear(); i++; }
            } else cell.append(c);
        }
        return rows;
    }
}
