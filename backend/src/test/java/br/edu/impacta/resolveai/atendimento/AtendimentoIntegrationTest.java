package br.edu.impacta.resolveai.atendimento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import br.edu.impacta.resolveai.atendimento.dto.ChamadoAtendimentoResponse;
import br.edu.impacta.resolveai.categoria.Categoria;
import br.edu.impacta.resolveai.categoria.CategoriaRepository;
import br.edu.impacta.resolveai.chamado.Chamado;
import br.edu.impacta.resolveai.chamado.ChamadoRepository;
import br.edu.impacta.resolveai.chamado.PrioridadeChamado;
import br.edu.impacta.resolveai.chamado.StatusChamado;
import br.edu.impacta.resolveai.security.JwtService;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.shared.error.ConflitoException;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AtendimentoIntegrationTest.ConcurrencyConfiguration.class)
class AtendimentoIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private CategoriaRepository categorias;
    @Autowired private ChamadoRepository chamados;
    @Autowired private AtendimentoService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private AssignmentClock clock;
    @Autowired private br.edu.impacta.resolveai.chamado.CicloChamadoService ciclo;

    private Usuario solicitante;
    private Usuario atendente;
    private Usuario outroAtendente;
    private Usuario admin;
    private Categoria categoria;
    private Categoria outraCategoria;

    @BeforeEach
    void setup() {
        solicitante = usuario(Perfil.SOLICITANTE);
        atendente = usuario(Perfil.ATENDENTE);
        outroAtendente = usuario(Perfil.ATENDENTE);
        admin = usuario(Perfil.ADMIN);
        categoria = categorias.save(Categoria.nova("Categoria " + UUID.randomUUID(), null, Instant.now()));
        outraCategoria = categorias.save(Categoria.nova("Outra " + UUID.randomUUID(), null, Instant.now()));
    }

    @Test
    void shouldRequireActiveAttendantOrAdminForEveryEndpoint() throws Exception {
        Chamado chamado = abrir(categoria, PrioridadeChamado.MEDIA, Instant.now());
        for (String url : List.of("/api/atendimento/fila", "/api/atendimento/me", "/api/atendimento/acesso",
                "/api/atendimento/categorias")) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
            mvc.perform(get(url).header("Authorization", token(solicitante))).andExpect(status().isForbidden());
            mvc.perform(get(url).header("Authorization", token(admin))).andExpect(status().isOk());
        }
        mvc.perform(patch(assumirUrl(chamado))).andExpect(status().isUnauthorized());
        mvc.perform(patch(assumirUrl(chamado)).header("Authorization", token(solicitante)))
                .andExpect(status().isForbidden());
        String inactiveToken = token(atendente);
        atendente.desativar(Instant.now());
        usuarios.save(atendente);
        mvc.perform(get("/api/atendimento/fila").header("Authorization", inactiveToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/atendimento/me").header("Authorization", inactiveToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch(assumirUrl(chamado)).header("Authorization", inactiveToken))
                .andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> service.assumir(chamado.id(), principal(solicitante)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldRestrictGlobalListingToActiveAdminAndValidateFilters() throws Exception {
        String url = "/api/atendimento/todos";
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        for (Usuario user : List.of(solicitante, atendente, outroAtendente)) {
            mvc.perform(get(url).header("Authorization", token(user))).andExpect(status().isForbidden());
            assertThatThrownBy(() -> service.todos(principal(user), 0, 10, null, null, null))
                    .isInstanceOf(AccessDeniedException.class);
        }
        for (String filter : List.of("page=-1", "size=0", "size=51", "categoriaId=0",
                "status=INVALIDO", "prioridade=URGENTE"))
            mvc.perform(get(url + "?" + filter).header("Authorization", token(admin)))
                    .andExpect(status().isBadRequest());
        String token = token(admin);
        admin.desativar(Instant.now());
        usuarios.save(admin);
        mvc.perform(get(url).header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldListAllOwnersAndStatusesWithStablePaginationAndCombinedFilters() throws Exception {
        Instant date = Instant.parse("2026-10-01T12:00:00Z");
        Chamado aberto = abrir(categoria, PrioridadeChamado.BAIXA, date);
        Usuario outroSolicitante = usuario(Perfil.SOLICITANTE);
        Chamado emAtendimento = chamados.saveAndFlush(Chamado.abrir("RA-" + UUID.randomUUID().toString().substring(0, 22),
                "Outro solicitante", "Descricao do chamado de outro solicitante", PrioridadeChamado.ALTA,
                outroSolicitante, categoria, date));
        Chamado resolvido = abrir(categoria, PrioridadeChamado.MEDIA, date);
        Chamado fechado = abrir(categoria, PrioridadeChamado.ALTA, date);
        for (Chamado ticket : List.of(emAtendimento, resolvido, fechado)) service.assumir(ticket.id(), principal(atendente));
        for (Chamado ticket : List.of(resolvido, fechado)) ciclo.alterarStatus(ticket.id(), principal(atendente),
                new br.edu.impacta.resolveai.chamado.dto.AlterarStatusRequest(StatusChamado.RESOLVIDO, "Resolucao para demonstracao"));
        ciclo.alterarStatus(fechado.id(), principal(solicitante),
                new br.edu.impacta.resolveai.chamado.dto.AlterarStatusRequest(StatusChamado.FECHADO, null));
        String url = "/api/atendimento/todos?categoriaId=" + categoria.id();
        JsonNode result = json.readTree(mvc.perform(get(url).header("Authorization", token(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4))
                .andReturn().getResponse().getContentAsString());
        assertThat(result.get("content").findValuesAsText("status"))
                .containsExactly("FECHADO", "RESOLVIDO", "EM_ATENDIMENTO", "ABERTO");
        assertThat(result.get("content").toString()).doesNotContain("senha", "email", "hash-ficticio");
        mvc.perform(get(url + "&size=1&page=1").header("Authorization", token(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(resolvido.id()))
                .andExpect(jsonPath("$.totalPages").value(4));
        mvc.perform(get(url + "&status=EM_ATENDIMENTO&prioridade=ALTA").header("Authorization", token(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(emAtendimento.id()));
        mvc.perform(get(url + "&status=ABERTO&prioridade=ALTA").header("Authorization", token(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get("/api/atendimento/me?categoriaId=" + categoria.id()).header("Authorization", token(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get("/api/atendimento/fila?categoriaId=" + categoria.id()).header("Authorization", token(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(aberto.id()));
    }

    @Test
    void shouldPageFilterAndSortQueueWithoutAssignedTicketsOrPersonalData() throws Exception {
        Instant created = Instant.parse("2026-01-10T10:00:00Z");
        Chamado first = abrir(categoria, PrioridadeChamado.ALTA, created);
        Chamado second = abrir(categoria, PrioridadeChamado.ALTA, created);
        Chamado third = abrir(categoria, PrioridadeChamado.ALTA, created.plusSeconds(1));
        abrir(categoria, PrioridadeChamado.BAIXA, created.plusSeconds(5));
        abrir(outraCategoria, PrioridadeChamado.ALTA, created.plusSeconds(5));
        Chamado assigned = abrir(categoria, PrioridadeChamado.ALTA, created.plusSeconds(6));
        service.assumir(assigned.id(), principal(outroAtendente));
        // Categorias desativadas continuam disponiveis para atendimento de chamados existentes.
        categoria.desativar(Instant.now());
        categorias.save(categoria);
        JsonNode categoryList = body(mvc.perform(get("/api/atendimento/categorias")
                .header("Authorization", token(atendente))).andExpect(status().isOk()).andReturn());
        JsonNode inactiveCategory = java.util.stream.StreamSupport.stream(categoryList.spliterator(), false)
                .filter(item -> item.get("id").asLong() == categoria.id()).findFirst().orElseThrow();
        assertThat(inactiveCategory.size()).isEqualTo(3);
        assertThat(inactiveCategory.get("ativa").asBoolean()).isFalse();
        String url = "/api/atendimento/fila?categoriaId=" + categoria.id() + "&prioridade=ALTA&size=2";
        JsonNode firstPage = body(mvc.perform(get(url).header("Authorization", token(atendente)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2)).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2)).andReturn());
        assertThat(firstPage.at("/content/0/id").asLong()).isEqualTo(third.id());
        assertThat(firstPage.at("/content/1/id").asLong()).isEqualTo(second.id());
        assertThat(firstPage.at("/content/0").size()).isEqualTo(10);
        assertThat(firstPage.at("/content/0/atendente").isNull()).isTrue();
        assertThat(firstPage.at("/content/0/atribuidoEm").isNull()).isTrue();
        assertThat(firstPage.at("/content/0/categoria").size()).isEqualTo(2);
        mvc.perform(get(url + "&page=1").header("Authorization", token(atendente)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(first.id()))
                .andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get(url + "&page=9").header("Authorization", token(atendente)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/atendimento/fila?categoriaId=999999999").header("Authorization", token(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void shouldAssignAtomicallyAndRestrictWalletToAuthenticatedUser() throws Exception {
        Chamado chamado = abrir(categoria, PrioridadeChamado.ALTA, Instant.now());
        JsonNode assigned = body(mvc.perform(patch(assumirUrl(chamado)).header("Authorization", token(atendente)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"))
                .andExpect(jsonPath("$.atendente.id").value(atendente.id())).andReturn());
        assertThat(assigned.at("/atribuidoEm").asText()).isNotBlank();
        assertThat(assigned.at("/atendente").size()).isEqualTo(2);
        Chamado persisted = chamados.findById(chamado.id()).orElseThrow();
        assertThat(persisted.versao()).isEqualTo(1);
        assertThat(persisted.atribuidoEm()).isEqualTo(persisted.atualizadoEm());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM historico_chamados WHERE chamado_id = ? "
                + "AND tipo_evento = 'ATRIBUICAO' AND autor_id = ? AND atendente_novo_id = ? "
                + "AND status_anterior = 'ABERTO' AND status_novo = 'EM_ATENDIMENTO' "
                + "AND criado_em = ?", Long.class, chamado.id(), atendente.id(), atendente.id(), persisted.atribuidoEm()))
                .isEqualTo(1);
        String carteira = "/api/atendimento/me?categoriaId=" + categoria.id()
                + "&prioridade=ALTA&status=EM_ATENDIMENTO";
        mvc.perform(get(carteira).header("Authorization", token(atendente)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(chamado.id()));
        for (Usuario other : List.of(outroAtendente, admin)) {
            mvc.perform(get(carteira).header("Authorization", token(other)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
            mvc.perform(patch(assumirUrl(chamado)).header("Authorization", token(other)))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        }
        mvc.perform(get(carteira.replace("EM_ATENDIMENTO", "RESOLVIDO")).header("Authorization", token(atendente)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/atendimento/fila?categoriaId=" + categoria.id()).header("Authorization", token(atendente)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        Chamado adminTicket = abrir(outraCategoria, PrioridadeChamado.MEDIA, Instant.now());
        mvc.perform(patch(assumirUrl(adminTicket)).header("Authorization", token(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.atendente.id").value(admin.id()));
    }

    @Test
    void shouldRejectInvalidParametersAndMissingTicketsAndDocumentContract() throws Exception {
        for (String url : List.of("/api/atendimento/fila", "/api/atendimento/me")) {
            for (String param : List.of("page=-1", "size=0", "size=51", "page=x", "page=2147483647&size=50", "categoriaId=0",
                    "categoriaId=-1", "prioridade=URGENTE", "categoriaId=x")) {
                mvc.perform(get(url + "?" + param).header("Authorization", token(atendente)))
                        .andExpect(status().isBadRequest());
            }
        }
        mvc.perform(get("/api/atendimento/me?status=INVALIDO").header("Authorization", token(atendente)))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/chamados/999999999/assumir").header("Authorization", token(atendente)))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/chamados/0/assumir").header("Authorization", token(atendente)))
                .andExpect(status().isBadRequest());
        JsonNode spec = body(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn());
        assertThat(spec.at("/paths/~1api~1atendimento~1fila/get/responses/400").isMissingNode()).isFalse();
        assertThat(spec.at("/paths/~1api~1atendimento~1me/get/security/0/bearerAuth").isMissingNode()).isFalse();
        assertThat(spec.at("/paths/~1api~1chamados~1{id}~1assumir/patch/responses/409").isMissingNode()).isFalse();
        assertThat(spec.at("/paths/~1api~1chamados~1{id}~1assumir/patch/requestBody").isMissingNode()).isTrue();
    }

    @Test
    void shouldRollbackAssignmentWhenHistoryCannotBeWritten() throws Exception {
        Chamado chamado = abrir(categoria, PrioridadeChamado.MEDIA, Instant.now());
        jdbc.execute("ALTER TABLE historico_chamados ADD CONSTRAINT test_history_failure CHECK (chamado_id <> "
                + chamado.id() + ")");
        try {
            mvc.perform(patch(assumirUrl(chamado)).header("Authorization", token(atendente)))
                    .andExpect(status().isInternalServerError());
            Chamado persisted = chamados.findById(chamado.id()).orElseThrow();
            assertThat(persisted.status()).isEqualTo(StatusChamado.ABERTO);
            assertThat(persisted.atendente()).isNull();
            assertThat(persisted.atribuidoEm()).isNull();
            assertThat(persisted.versao()).isZero();
            assertThat(historyCount(chamado.id())).isZero();
        } finally {
            jdbc.execute("ALTER TABLE historico_chamados DROP CONSTRAINT test_history_failure");
        }
    }

    @Test
    void shouldAllowExactlyOneAssignmentWithBothTransactionsReadingTheSameVersion() throws Exception {
        Chamado chamado = abrir(categoria, PrioridadeChamado.MEDIA, Instant.now());
        clock.barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2, task -> new Thread(task, "assignment-race"))) {
            var first = pool.submit(() -> assignResult(chamado.id(), atendente));
            var second = pool.submit(() -> assignResult(chamado.id(), outroAtendente));
            List<Object> results = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThat(results).filteredOn(ChamadoAtendimentoResponse.class::isInstance).hasSize(1);
            assertThat(results).filteredOn(OptimisticLockingFailureException.class::isInstance).hasSize(1);
            ChamadoAtendimentoResponse winner = results.stream().filter(ChamadoAtendimentoResponse.class::isInstance)
                    .map(ChamadoAtendimentoResponse.class::cast).findFirst().orElseThrow();
            assertThat(jdbc.queryForObject("SELECT atendente_id FROM chamados WHERE id = ?", Long.class, chamado.id()))
                    .isEqualTo(winner.atendente().id());
            assertThat(chamados.findById(chamado.id()).orElseThrow().versao()).isEqualTo(1);
            assertThat(historyCount(chamado.id())).isEqualTo(1);
        } finally {
            clock.barrier = null;
        }
    }

    @Test
    void shouldReturnOneHttpSuccessAndOneConflictForSimultaneousRequests() throws Exception {
        Chamado chamado = abrir(categoria, PrioridadeChamado.MEDIA, Instant.now());
        String firstToken = token(atendente);
        String secondToken = token(outroAtendente);
        CyclicBarrier start = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(10, TimeUnit.SECONDS); return mvc.perform(
                    patch(assumirUrl(chamado)).header("Authorization", firstToken)).andReturn().getResponse().getStatus(); });
            var second = pool.submit(() -> { start.await(10, TimeUnit.SECONDS); return mvc.perform(
                    patch(assumirUrl(chamado)).header("Authorization", secondToken)).andReturn().getResponse().getStatus(); });
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
            assertThat(historyCount(chamado.id())).isEqualTo(1);
        }
    }

    private Object assignResult(Long id, Usuario usuario) {
        try {
            return service.assumir(id, principal(usuario));
        } catch (OptimisticLockingFailureException | ConflitoException failure) {
            return failure;
        }
    }

    private long historyCount(Long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM historico_chamados WHERE chamado_id = ?", Long.class, id);
    }

    private Usuario usuario(Perfil perfil) {
        return usuarios.save(Usuario.novo("Pessoa " + perfil, UUID.randomUUID() + "@example.com",
                "hash-ficticio", perfil, Instant.now()));
    }

    private Chamado abrir(Categoria category, PrioridadeChamado prioridade, Instant created) {
        return chamados.saveAndFlush(Chamado.abrir("RA-" + UUID.randomUUID().toString().substring(0, 22),
                "Falha de acesso", "Nao consigo acessar o recurso solicitado.", prioridade, solicitante, category, created));
    }

    private UsuarioPrincipal principal(Usuario usuario) {
        return new UsuarioPrincipal(usuario.id(), usuario.email(), usuario.perfil());
    }

    private String token(Usuario usuario) {
        return "Bearer " + jwt.generate(usuario);
    }

    private String assumirUrl(Chamado chamado) {
        return "/api/chamados/" + chamado.id() + "/assumir";
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    @TestConfiguration
    static class ConcurrencyConfiguration {
        @Bean @Primary AssignmentClock assignmentClock() { return new AssignmentClock(); }
    }

    static class AssignmentClock extends Clock {
        volatile CyclicBarrier barrier;

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() {
            CyclicBarrier current = barrier;
            if (current != null && Thread.currentThread().getName().equals("assignment-race")) {
                try {
                    // AtendimentoService calls the clock only after loading the ticket/version.
                    current.await(10, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new IllegalStateException("Nao foi possivel sincronizar as transacoes", failure);
                }
            }
            return Instant.now();
        }
    }
}
