package br.edu.impacta.resolveai.chamado;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.util.*;
import br.edu.impacta.resolveai.atendimento.AtendimentoService;
import br.edu.impacta.resolveai.categoria.*;
import br.edu.impacta.resolveai.security.*;
import br.edu.impacta.resolveai.usuario.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class HistoricoChamadoIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired CategoriaRepository categorias;
    @Autowired ChamadoRepository chamados;
    @Autowired AtendimentoService atendimento;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired CicloChamadoService ciclo;
    Usuario solicitante, agente, outro, admin;
    Categoria categoria;
    Long id;

    @BeforeEach void setup() throws Exception {
        solicitante = usuario(Perfil.SOLICITANTE);
        agente = usuario(Perfil.ATENDENTE);
        outro = usuario(Perfil.SOLICITANTE);
        admin = usuario(Perfil.ADMIN);
        categoria = categorias.save(Categoria.nova("Histórico " + UUID.randomUUID(), null, Instant.now()));
        id = criar(solicitante, 201).get("id").asLong();
    }

    @Test void creationAndEveryChangedFieldAppearOnceWithSafeAuthenticatedActor() throws Exception {
        JsonNode history = historico(solicitante, 200);
        assertThat(history).hasSize(1);
        JsonNode creation = history.get(0);
        assertThat(creation.get("tipoEvento").asText()).isEqualTo("CRIACAO");
        assertThat(creation.get("autor").get("id").asLong()).isEqualTo(solicitante.id());
        assertThat(creation.get("autor")).hasSize(2);
        assertThat(creation.get("valorAnterior").isNull()).isTrue();
        assertThat(creation.at("/valorNovo/codigo").asText()).isEqualTo("ABERTO");
        assertThat(Instant.parse(creation.get("criadoEm").asText())).isEqualTo(chamados.findById(id).orElseThrow().criadoEm());
        atendimento.assumir(id, principal(agente));
        prioridade(agente, "ALTA", 200);
        mvc.perform(patch(url()+"/status").header("Authorization", token(agente))
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"status":"RESOLVIDO","justificativa":"Solução acadêmica verificada", "autorId":999999}
                """)).andExpect(status().isOk());
        history = historico(solicitante, 200);
        assertThat(history).hasSize(5);
        assertThat(history.get(0).get("tipoEvento").asText()).isEqualTo("STATUS");
        assertThat(history.get(0).at("/valorAnterior/codigo").asText()).isEqualTo("EM_ATENDIMENTO");
        assertThat(history.get(0).at("/valorNovo/codigo").asText()).isEqualTo("RESOLVIDO");
        assertThat(history.get(0).at("/autor/id").asLong()).isEqualTo(agente.id());
        var priority = event(history, "PRIORIDADE");
        assertThat(priority.at("/valorAnterior/codigo").asText()).isEqualTo("MEDIA");
        assertThat(priority.at("/valorNovo/codigo").asText()).isEqualTo("ALTA");
        var assignment = event(history, "ATRIBUICAO");
        assertThat(assignment.get("valorAnterior").isNull()).isTrue();
        assertThat(assignment.at("/valorNovo/codigo").asText()).isEqualTo(agente.id().toString());
        assertThat(assignment.at("/valorNovo/nome").asText()).isEqualTo(agente.nome());
        assertThat(assignment.get("criadoEm")).isEqualTo(history.get(2).get("criadoEm"));
        prioridade(admin, "ALTA", 409);
        mvc.perform(patch(url()+"/status").header("Authorization", token(agente))
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVIDO\",\"justificativa\":\"Solução repetida indevida\"}"))
            .andExpect(status().isConflict());
        assertThat(historico(solicitante, 200)).hasSize(5);
    }

    @Test void sameVisibilityAsDetailsAndDeactivatedActorsRemainIdentifiable() throws Exception {
        mvc.perform(get(url()+"/historico")).andExpect(status().isUnauthorized());
        historico(outro, 404);
        historico(agente, 404);
        historico(admin, 200);
        mvc.perform(get("/api/chamados/9223372036854775807/historico").header("Authorization", token(admin)))
            .andExpect(status().isNotFound());
        atendimento.assumir(id, principal(agente));
        historico(agente, 200);
        String oldToken = token(agente);
        agente.desativar(Instant.now()); usuarios.save(agente);
        mvc.perform(get(url()+"/historico").header("Authorization", oldToken)).andExpect(status().isUnauthorized());
        var assignment = event(historico(solicitante, 200), "ATRIBUICAO");
        assertThat(assignment.at("/autor/id").asLong()).isEqualTo(agente.id());
        assertThat(assignment.at("/autor/nome").asText()).isEqualTo(agente.nome());
        assertThat(assignment.at("/valorNovo/nome").asText()).isEqualTo(agente.nome());
        for (String method : List.of("patch", "delete", "post")) {
            var request = switch(method) {
                case "patch" -> patch(url()+"/historico"); case "delete" -> delete(url()+"/historico");
                default -> post(url()+"/historico");
            };
            mvc.perform(request.header("Authorization", token(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"autorId\":999999}")).andExpect(status().isMethodNotAllowed());
        }
    }

    @Test void readersNeverSeeUncommittedAssignmentOrOnlyOneOfItsTwoEvents() throws Exception {
        var ready = new java.util.concurrent.CountDownLatch(1);
        var commit = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var writer = pool.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(tx -> {
                    atendimento.assumir(id, principal(agente));
                    ready.countDown();
                    try { if (!commit.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Timeout de commit"); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                }));
            try {
                assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThat(historico(solicitante, 200)).hasSize(1);
            } finally { commit.countDown(); }
            writer.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(historico(solicitante, 200)).hasSize(3);
    }

    @Test void rollbackOfMultipleChangesPreservesOnlyPreviouslyCommittedHistory() throws Exception {
        assertThatThrownBy(() -> new org.springframework.transaction.support.TransactionTemplate(transactionManager)
            .executeWithoutResult(tx -> {
                atendimento.assumir(id, principal(agente));
                ciclo.alterarPrioridade(id, principal(agente), new br.edu.impacta.resolveai.chamado.dto.AlterarPrioridadeRequest(PrioridadeChamado.ALTA));
                throw new IllegalStateException("Falha acadêmica após alterações");
            })).isInstanceOf(IllegalStateException.class);
        var ticket = chamados.findById(id).orElseThrow();
        assertThat(ticket.status()).isEqualTo(StatusChamado.ABERTO);
        assertThat(ticket.prioridade()).isEqualTo(PrioridadeChamado.MEDIA);
        assertThat(ticket.atendente()).isNull();
        assertThat(ticket.versao()).isZero();
        assertThat(historico(solicitante, 200)).hasSize(1);
    }

    @Test void failedCreationRollsBackTicketAndItsInitialEvent() throws Exception {
        Usuario requester = usuario(Perfil.SOLICITANTE);
        jdbc.execute("ALTER TABLE historico_chamados ADD CONSTRAINT history_creation_failure CHECK (autor_id <> " + requester.id() + ")");
        try {
            criar(requester, 500);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chamados WHERE solicitante_id=?", Long.class, requester.id())).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM historico_chamados WHERE autor_id=?", Long.class, requester.id())).isZero();
        } finally { jdbc.execute("ALTER TABLE historico_chamados DROP CONSTRAINT history_creation_failure"); }
    }

    @Test void legacyDuplicatesAreReadOnceAndTiesHaveStableDescendingSourceAndId() throws Exception {
        atendimento.assumir(id, principal(agente));
        // V5 copied evidenced transitions from the general table. Keep both physical rows.
        jdbc.update("""
            INSERT INTO historico_chamados (chamado_id,autor_id,tipo_evento,status_anterior,status_novo,criado_em)
            SELECT chamado_id,autor_id,'STATUS',status_anterior,status_novo,criado_em
            FROM historico_status WHERE chamado_id=?
            """, id);
        assertThat(historico(solicitante, 200)).hasSize(3);
        Instant tie = Instant.parse("2090-01-01T10:00:00Z");
        for (int i=0;i<2;i++) jdbc.update("""
            INSERT INTO historico_chamados (chamado_id,autor_id,tipo_evento,prioridade_anterior,prioridade_nova,criado_em)
            VALUES (?,?,'PRIORIDADE','MEDIA','ALTA',?)
            """, id, admin.id(), java.sql.Timestamp.from(tie));
        JsonNode first=historico(solicitante,200), second=historico(solicitante,200);
        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(5);
        long a = Long.parseLong(first.get(0).get("id").asText().split("-")[1]);
        long b = Long.parseLong(first.get(1).get("id").asText().split("-")[1]);
        assertThat(a).isGreaterThan(b);
        for (int i=1;i<first.size();i++) assertThat(Instant.parse(first.get(i-1).get("criadoEm").asText()))
            .isAfterOrEqualTo(Instant.parse(first.get(i).get("criadoEm").asText()));
        // Actor and assigned user are independent foreign keys, including on legacy events.
        jdbc.update("""
            INSERT INTO historico_chamados (chamado_id,autor_id,tipo_evento,atendente_novo_id,criado_em)
            VALUES (?,?,'ATRIBUICAO',?,?)
            """, id,admin.id(),agente.id(),java.sql.Timestamp.from(tie.plusSeconds(1)));
        JsonNode assignment=historico(solicitante,200).get(0);
        assertThat(assignment.at("/autor/id").asLong()).isEqualTo(admin.id());
        assertThat(assignment.at("/valorNovo/codigo").asText()).isEqualTo(agente.id().toString());
    }

    @Test void emptyLegacyTicketsAndOpenApiContract() throws Exception {
        var legacy=chamados.saveAndFlush(Chamado.abrir("L-"+UUID.randomUUID().toString().substring(0,20),
            "Chamado legado", "Descrição acadêmica suficientemente longa", solicitante,categoria,Instant.now()));
        mvc.perform(get("/api/chamados/"+legacy.id()+"/historico").header("Authorization",token(solicitante)))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
        var spec=json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var operation=spec.at("/paths/~1api~1chamados~1{id}~1historico/get");
        assertThat(operation.at("/responses/200/content/application~1json/schema/type").asText()).isEqualTo("array");
        assertThat(operation.at("/responses/200/content/application~1json/schema/items/$ref").asText()).endsWith("EventoHistoricoResponse");
        for(String code:List.of("400","401","404")) assertThat(operation.at("/responses/"+code).isMissingNode()).isFalse();
        assertThat(spec.at("/components/schemas/EventoHistoricoResponse/properties/tipoEvento").isMissingNode()).isFalse();
    }

    private JsonNode event(JsonNode history,String type) { for(var e:history) if(e.get("tipoEvento").asText().equals(type)) return e; throw new AssertionError(type); }
    private JsonNode criar(Usuario u,int expected) throws Exception {
        return json.readTree(mvc.perform(post("/api/chamados").header("Authorization",token(u)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"titulo\":\"Chamado acadêmico\",\"descricao\":\"Descrição acadêmica suficientemente longa\",\"categoriaId\":"+categoria.id()+",\"prioridade\":\"MEDIA\",\"autorId\":999999}"))
            .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }
    private JsonNode historico(Usuario u,int expected) throws Exception { return json.readTree(mvc.perform(get(url()+"/historico").header("Authorization",token(u)))
        .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString()); }
    private void prioridade(Usuario u,String value,int expected) throws Exception { mvc.perform(patch(url()+"/prioridade").header("Authorization",token(u)).contentType(MediaType.APPLICATION_JSON)
        .content("{\"prioridade\":\""+value+"\"}")).andExpect(status().is(expected)); }
    private String url(){return "/api/chamados/"+id;}
    private UsuarioPrincipal principal(Usuario u){return new UsuarioPrincipal(u.id(),u.email(),u.perfil());}
    private String token(Usuario u){return "Bearer "+jwt.generate(u);}
    private Usuario usuario(Perfil p){return usuarios.save(Usuario.novo("Pessoa acadêmica "+p,UUID.randomUUID()+"@example.invalid","hash-ficticio",p,Instant.now()));}
}
