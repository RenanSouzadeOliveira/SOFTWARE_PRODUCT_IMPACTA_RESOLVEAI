package br.edu.impacta.resolveai.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiContractTest {
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private JsonNode document() throws Exception {
        return mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
    }

    @Test
    void documentsSuccessErrorsAndBearerForEveryProtectedOperation() throws Exception {
        var doc = document();
        assertThat(doc.path("paths").size()).isEqualTo(27);
        int operations = 0;
        for (var paths = doc.path("paths").fields(); paths.hasNext();) {
            var path = paths.next();
            for (var methods = path.getValue().fields(); methods.hasNext();) {
                var method = methods.next();
                if (!METHODS.contains(method.getKey())) continue;
                operations++;
                var operation = method.getValue();
                var codes = new java.util.HashSet<String>();
                operation.path("responses").fieldNames().forEachRemaining(codes::add);
                assertThat(codes).as(path.getKey() + " " + method.getKey()).anyMatch(code -> code.matches("2\\d\\d"));
                boolean publicRoute = path.getKey().equals("/api/health") || path.getKey().startsWith("/api/auth/");
                if (publicRoute) assertThat(operation.path("security").isEmpty()).isTrue();
                else {
                    assertThat(operation.path("security").toString()).contains("bearerAuth");
                    assertThat(codes).contains("401");
                }
            }
        }
        assertThat(operations).isEqualTo(32);
        assertThat(doc.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
        for (String path : new String[]{"/api/admin/usuarios", "/api/admin/usuarios/{id}", "/api/admin/usuarios/{id}/situacao"}) {
            String method = path.equals("/api/admin/usuarios") ? "get" : "patch";
            assertThat(doc.path("paths").path(path).path(method).path("responses").path("200").path("content").isMissingNode()).isFalse();
        }
        for (String method : new String[]{"get", "post"})
            assertThat(doc.path("paths").path("/api/admin/categorias").path(method).path("responses").has("403")).isTrue();
        assertThat(doc.path("paths").path("/api/admin/categorias/{id}").path("patch").path("responses").has("409")).isTrue();
    }

    @Test
    void publishesAllSharedFiltersAndCsvPaginationSemantics() throws Exception {
        var paths = document().path("paths");
        for (String path : new String[]{"/api/chamados", "/api/chamados/me", "/api/chamados/exportacao",
                "/api/atendimento/fila", "/api/atendimento/me", "/api/atendimento/todos"}) {
            var parameters = new java.util.HashMap<String, JsonNode>();
            paths.path(path).path("get").path("parameters").forEach(p -> parameters.put(p.path("name").asText(), p));
            assertThat(parameters.keySet()).as(path).containsExactlyInAnyOrder("search", "status", "prioridade", "categoriaId", "categoria", "responsavel", "dataInicial", "dataFinal", "page", "size", "sort");
            assertThat(parameters.get("page").at("/schema/minimum").asInt()).isZero();
            assertThat(parameters.get("size").at("/schema/minimum").asInt()).isEqualTo(1);
            assertThat(parameters.get("size").at("/schema/maximum").asInt()).isEqualTo(100);
            assertThat(parameters.get("dataInicial").at("/schema/format").asText()).isEqualTo("date");
            if (path.endsWith("exportacao")) {
                assertThat(parameters.get("page").path("description").asText()).contains("não restringe o CSV");
                assertThat(parameters.get("size").path("description").asText()).contains("não restringe o CSV");
                assertThat(paths.path(path).path("get").path("responses").path("200").path("content").has("text/csv")).isTrue();
            }
        }
    }
}
