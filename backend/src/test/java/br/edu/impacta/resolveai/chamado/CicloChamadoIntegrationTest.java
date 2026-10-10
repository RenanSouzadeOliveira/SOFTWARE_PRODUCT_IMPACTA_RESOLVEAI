package br.edu.impacta.resolveai.chamado;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import br.edu.impacta.resolveai.atendimento.AtendimentoService;
import br.edu.impacta.resolveai.categoria.*;
import br.edu.impacta.resolveai.security.*;
import br.edu.impacta.resolveai.usuario.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
@Import(CicloChamadoIntegrationTest.ConcurrencyConfiguration.class)
class CicloChamadoIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private CategoriaRepository categorias;
    @Autowired private ChamadoRepository chamados;
    @Autowired private AtendimentoService atribuicao;
    @Autowired private JwtService jwt;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransitionClock clock;
    private Usuario solicitante, atendente, outroAtendente, outroSolicitante, admin;
    private Chamado chamado;

    @BeforeEach void setup() {
        solicitante = usuario(Perfil.SOLICITANTE);
        atendente = usuario(Perfil.ATENDENTE);
        outroAtendente = usuario(Perfil.ATENDENTE);
        outroSolicitante = usuario(Perfil.SOLICITANTE);
        admin = usuario(Perfil.ADMIN);
        Categoria categoria = categorias.save(Categoria.nova("Categoria " + UUID.randomUUID(), null, Instant.now()));
        chamado = chamados.saveAndFlush(Chamado.abrir("RA-" + UUID.randomUUID().toString().substring(0, 22),
                "Falha de acesso", "Nao consigo acessar o recurso solicitado", solicitante, categoria, Instant.now()));
    }

    @Test void shouldRunFullLifecycleWithExactlyOneOrderedEventPerTransition() throws Exception {
        atribuicao.assumir(chamado.id(), principal(atendente));
        JsonNode resolved = alterarStatus(atendente, "RESOLVIDO", "  Acesso foi restabelecido  ", 200);
        assertThat(resolved.at("/solucao").asText()).isEqualTo("Acesso foi restabelecido");
        assertThat(resolved.at("/resolvidoEm").isTextual()).isTrue();
        assertThat(resolved.at("/acoesPermitidas")).isEmpty();
        JsonNode owner = detalhe(solicitante, 200);
        assertThat(owner.at("/acoesPermitidas").toString()).isEqualTo("[\"REABRIR\",\"FECHAR\"]");
        JsonNode reopened = alterarStatus(solicitante, "EM_ATENDIMENTO", "Ainda nao consigo acessar", 200);
        for (String field : List.of("solucao", "resolvidoEm", "fechadoEm")) assertThat(reopened.get(field).isNull()).isTrue();
        assertThat(reopened.at("/atendente/id").asLong()).isEqualTo(atendente.id());
        assertThat(reopened.at("/atribuidoEm")).isEqualTo(resolved.at("/atribuidoEm"));
        alterarStatus(atendente, "RESOLVIDO", "Resolucao definitiva agora", 200);
        JsonNode closed = alterarStatus(solicitante, "FECHADO", null, 200);
        assertThat(closed.at("/fechadoEm").isTextual()).isTrue();
        assertThat(closed.at("/acoesPermitidas")).isEmpty();
        assertThat(closed.at("/historicoStatus")).hasSize(5);
        Instant previous = Instant.MIN;
        long previousId = 0;
        for (JsonNode evento : closed.at("/historicoStatus")) {
            Instant date = Instant.parse(evento.get("criadoEm").asText());
            assertThat(date).isAfterOrEqualTo(previous);
            if (date.equals(previous)) assertThat(evento.get("id").asLong()).isGreaterThan(previousId);
            assertThat(evento.get("autor")).hasSize(2);
            previous = date;
            previousId = evento.get("id").asLong();
        }
        assertThat(closed.at("/historicoStatus/0/observacao").isNull()).isTrue();
        assertThat(closed.at("/historicoStatus/1/observacao").asText()).isEqualTo("Acesso foi restabelecido");
        assertThat(statusCount()).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM historico_chamados WHERE chamado_id=? AND tipo_evento='STATUS'",
                Long.class, chamado.id())).isZero();
        alterarStatus(admin, "EM_ATENDIMENTO", "Reabrir apos fechamento", 409);
        prioridade(admin, "BAIXA", 409);
        assertThat(statusCount()).isEqualTo(5);
    }

    @Test void shouldEnforceVisibilityMutationRolesAndActiveAccounts() throws Exception {
        mvc.perform(get(url())).andExpect(status().isUnauthorized());
        mvc.perform(patch(url() + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVIDO\",\"justificativa\":\"Solucao valida\"}"))
                .andExpect(status().isUnauthorized());
        detalhe(solicitante, 200);
        detalhe(admin, 200);
        detalhe(atendente, 404);
        detalhe(outroSolicitante, 404);
        alterarStatus(outroAtendente, "RESOLVIDO", "Solucao indevida", 403);
        atribuicao.assumir(chamado.id(), principal(atendente));
        detalhe(atendente, 200);
        detalhe(outroAtendente, 404);
        alterarStatus(outroSolicitante, "RESOLVIDO", "Solucao indevida", 403);
        alterarStatus(outroAtendente, "RESOLVIDO", "Solucao indevida", 403);
        alterarStatus(solicitante, "RESOLVIDO", "Solucao indevida", 403);
        alterarStatus(admin, "RESOLVIDO", "Resolucao do administrador", 200);
        alterarStatus(atendente, "FECHADO", null, 403);
        alterarStatus(outroSolicitante, "FECHADO", null, 403);
        alterarStatus(admin, "EM_ATENDIMENTO", "Reabertura do administrador", 200);
        String token = token(atendente);
        atendente.desativar(Instant.now());
        usuarios.save(atendente);
        mvc.perform(get(url()).header("Authorization", token)).andExpect(status().isUnauthorized());
        mvc.perform(patch(url() + "/status").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVIDO\",\"justificativa\":\"Solucao valida\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void shouldRejectSkippedTransitionsAndReportFieldBoundariesWithoutMutation() throws Exception {
        alterarStatus(admin, "EM_ATENDIMENTO", null, 409);
        alterarStatus(solicitante, "FECHADO", null, 409);
        assertThat(statusCount()).isZero();
        assertThat(chamados.findById(chamado.id()).orElseThrow().versao()).isZero();
        atribuicao.assumir(chamado.id(), principal(atendente));
        for (String value : new String[] {null, "", "    ", "123456789", "x".repeat(501)}) {
            JsonNode error = alterarStatus(atendente, "RESOLVIDO", value, 400);
            assertThat(error.at("/violations/0/field").asText()).isEqualTo("justificativa");
        }
        for (String body : List.of("{}", "{\"status\":\"INCORRETO\"}")) {
            mvc.perform(patch(url() + "/status").header("Authorization", token(atendente))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.violations[0].field").value("status"));
        }
        assertThat(statusCount()).isEqualTo(1);
        assertThat(chamados.findById(chamado.id()).orElseThrow().status()).isEqualTo(StatusChamado.EM_ATENDIMENTO);
        alterarStatus(atendente, "RESOLVIDO", "  " + "x".repeat(500) + "  ", 200);
        alterarStatus(solicitante, "EM_ATENDIMENTO", "  1234567890  ", 200);
        alterarStatus(atendente, "RESOLVIDO", "  1234567890  ", 200);
        alterarStatus(solicitante, "FECHADO", null, 200);
        assertThat(statusCount()).isEqualTo(5);
    }

    @Test void shouldAuditPriorityAndAllowOnlyResponsibleWhileServingOrAdminUntilClosed() throws Exception {
        prioridade(solicitante, "ALTA", 403);
        prioridade(outroAtendente, "ALTA", 403);
        prioridade(admin, "ALTA", 200);
        prioridade(admin, "ALTA", 409);
        atribuicao.assumir(chamado.id(), principal(atendente));
        prioridade(outroAtendente, "BAIXA", 403);
        prioridade(atendente, "BAIXA", 200);
        mvc.perform(patch(url() + "/prioridade").header("Authorization", token(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"prioridade\":\"URGENTE\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.violations[0].field").value("prioridade"));
        alterarStatus(atendente, "RESOLVIDO", "Solucao suficientemente longa", 200);
        prioridade(atendente, "MEDIA", 403);
        prioridade(admin, "MEDIA", 200);
        assertThat(statusCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM historico_chamados WHERE chamado_id=? AND tipo_evento='PRIORIDADE'",
                Long.class, chamado.id())).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM historico_chamados WHERE chamado_id=? AND tipo_evento='PRIORIDADE' "
                + "AND prioridade_anterior='ALTA' AND prioridade_nova='BAIXA' AND autor_id=?", Long.class,
                chamado.id(), atendente.id())).isEqualTo(1);
    }

    @Test void shouldRollbackStatusWhenHistoryFailsAndAssignmentWhenStatusHistoryFails() throws Exception {
        jdbc.execute("ALTER TABLE historico_status ADD CONSTRAINT test_status_failure CHECK (chamado_id <> " + chamado.id() + ")");
        try {
            mvc.perform(patch(url() + "/assumir").header("Authorization", token(atendente)))
                    .andExpect(status().isInternalServerError());
            assertThat(chamados.findById(chamado.id()).orElseThrow().status()).isEqualTo(StatusChamado.ABERTO);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM historico_chamados WHERE chamado_id=?", Long.class, chamado.id())).isZero();
        } finally { jdbc.execute("ALTER TABLE historico_status DROP CONSTRAINT test_status_failure"); }
        atribuicao.assumir(chamado.id(), principal(atendente));
        jdbc.execute("ALTER TABLE historico_status ADD CONSTRAINT test_status_failure CHECK (chamado_id <> "
                + chamado.id() + " OR status_novo <> 'RESOLVIDO')");
        try {
            alterarStatus(atendente, "RESOLVIDO", "Solucao deve ser revertida", 500);
            Chamado persisted = chamados.findById(chamado.id()).orElseThrow();
            assertThat(persisted.status()).isEqualTo(StatusChamado.EM_ATENDIMENTO);
            assertThat(persisted.solucao()).isNull();
            assertThat(persisted.resolvidoEm()).isNull();
            assertThat(persisted.versao()).isEqualTo(1);
            assertThat(statusCount()).isEqualTo(1);
        } finally { jdbc.execute("ALTER TABLE historico_status DROP CONSTRAINT test_status_failure"); }
    }

    @Test void shouldAllowExactlyOneConcurrentResolutionAndItsHistory() throws Exception {
        atribuicao.assumir(chamado.id(), principal(atendente));
        String token = token(atendente);
        clock.barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2, task -> new Thread(task, "transition-race"))) {
            Callable<Integer> request = () -> mvc.perform(patch(url() + "/status").header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"RESOLVIDO\",\"justificativa\":\"Solucao concorrente valida\"}"))
                    .andReturn().getResponse().getStatus();
            var a = pool.submit(request);
            var b = pool.submit(request);
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
            assertThat(statusCount()).isEqualTo(2);
            assertThat(chamados.findById(chamado.id()).orElseThrow().versao()).isEqualTo(2);
        } finally { clock.barrier = null; }
    }

    @Test void shouldRollbackPriorityWhenItsAuditCannotBeWritten() throws Exception {
        atribuicao.assumir(chamado.id(), principal(atendente));
        jdbc.execute("ALTER TABLE historico_chamados ADD CONSTRAINT test_priority_failure CHECK (chamado_id <> "
                + chamado.id() + " OR tipo_evento <> 'PRIORIDADE')");
        try {
            prioridade(atendente, "ALTA", 500);
            Chamado persisted = chamados.findById(chamado.id()).orElseThrow();
            assertThat(persisted.prioridade()).isEqualTo(PrioridadeChamado.MEDIA);
            assertThat(persisted.versao()).isEqualTo(1);
            assertThat(statusCount()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM historico_chamados WHERE chamado_id=? AND tipo_evento='PRIORIDADE'",
                    Long.class, chamado.id())).isZero();
        } finally { jdbc.execute("ALTER TABLE historico_chamados DROP CONSTRAINT test_priority_failure"); }
    }

    @Test void shouldExposeOpenApiContractAndIgnoreClientControlledIdentifiersAndDates() throws Exception {
        atribuicao.assumir(chamado.id(), principal(atendente));
        mvc.perform(patch(url() + "/status").header("Authorization", token(atendente))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"status":"RESOLVIDO","justificativa":"Solucao validada corretamente",
                         "autorId":999999,"atendenteId":999999,"resolvidoEm":"1900-01-01T00:00:00Z"}
                        """)).andExpect(status().isOk()).andExpect(jsonPath("$.atendente.id").value(atendente.id()));
        JsonNode detail = detalhe(solicitante, 200);
        assertThat(detail.at("/historicoStatus/1/autor/id").asLong()).isEqualTo(atendente.id());
        assertThat(Instant.parse(detail.at("/resolvidoEm").asText())).isAfter(Instant.parse("2026-01-01T00:00:00Z"));
        JsonNode spec = json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (String endpoint : List.of("status", "prioridade")) {
            for (String response : List.of("200", "400", "401", "403", "404", "409"))
                assertThat(spec.at("/paths/~1api~1chamados~1{id}~1" + endpoint + "/patch/responses/" + response).isMissingNode()).isFalse();
        }
    }

    private JsonNode alterarStatus(Usuario actor, String status, String justificativa, int expected) throws Exception {
        String body = json.writeValueAsString(new br.edu.impacta.resolveai.chamado.dto.AlterarStatusRequest(
                StatusChamado.valueOf(status), justificativa));
        return json.readTree(mvc.perform(patch(url() + "/status").header("Authorization", token(actor))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString());
    }
    private void prioridade(Usuario actor, String value, int expected) throws Exception {
        mvc.perform(patch(url() + "/prioridade").header("Authorization", token(actor))
                .contentType(MediaType.APPLICATION_JSON).content("{\"prioridade\":\"" + value + "\"}"))
                .andExpect(status().is(expected));
    }
    private JsonNode detalhe(Usuario actor, int expected) throws Exception {
        return json.readTree(mvc.perform(get(url()).header("Authorization", token(actor)))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }
    private long statusCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM historico_status WHERE chamado_id=?", Long.class, chamado.id()); }
    private String url() { return "/api/chamados/" + chamado.id(); }
    private UsuarioPrincipal principal(Usuario u) { return new UsuarioPrincipal(u.id(), u.email(), u.perfil()); }
    private String token(Usuario u) { return "Bearer " + jwt.generate(u); }
    private Usuario usuario(Perfil p) { return usuarios.save(Usuario.novo("Pessoa " + p, UUID.randomUUID() + "@example.com", "hash-ficticio", p, Instant.now())); }

    @TestConfiguration static class ConcurrencyConfiguration {
        @Bean @Primary TransitionClock transitionClock() { return new TransitionClock(); }
    }
    static class TransitionClock extends Clock {
        volatile CyclicBarrier barrier;
        private final ThreadLocal<Boolean> synchronizedOnce = ThreadLocal.withInitial(() -> false);
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() {
            if (barrier != null && Thread.currentThread().getName().equals("transition-race") && !synchronizedOnce.get()) {
                synchronizedOnce.set(true);
                try { barrier.await(10, TimeUnit.SECONDS); }
                catch (Exception e) { throw new IllegalStateException("Falha ao sincronizar transicoes", e); }
            }
            return Instant.now();
        }
    }
}
