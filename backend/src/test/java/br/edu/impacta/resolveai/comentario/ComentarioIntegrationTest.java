package br.edu.impacta.resolveai.comentario;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import br.edu.impacta.resolveai.atendimento.AtendimentoService;
import br.edu.impacta.resolveai.categoria.Categoria;
import br.edu.impacta.resolveai.categoria.CategoriaRepository;
import br.edu.impacta.resolveai.chamado.Chamado;
import br.edu.impacta.resolveai.chamado.ChamadoRepository;
import br.edu.impacta.resolveai.chamado.CicloChamadoService;
import br.edu.impacta.resolveai.chamado.StatusChamado;
import br.edu.impacta.resolveai.chamado.dto.AlterarStatusRequest;
import br.edu.impacta.resolveai.comentario.dto.CriarComentarioRequest;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class ComentarioIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private CategoriaRepository categorias;
    @Autowired private ChamadoRepository chamados;
    @Autowired private AtendimentoService atribuicao;
    @Autowired private CicloChamadoService ciclo;
    @Autowired private ComentarioService service;
    @Autowired private JwtService jwt;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    private Usuario owner, attendant, otherRequester, otherAttendant, admin;
    private Chamado ticket;

    @BeforeEach void setup() {
        owner = usuario(Perfil.SOLICITANTE);
        attendant = usuario(Perfil.ATENDENTE);
        otherRequester = usuario(Perfil.SOLICITANTE);
        otherAttendant = usuario(Perfil.ATENDENTE);
        admin = usuario(Perfil.ADMIN);
        Categoria category = categorias.save(Categoria.nova("Categoria " + UUID.randomUUID(), null, Instant.now()));
        ticket = chamados.saveAndFlush(Chamado.abrir("RA-" + UUID.randomUUID().toString().substring(0, 22),
                "Falha de acesso", "Descricao suficientemente longa para o chamado", owner, category, Instant.now()));
    }

    @Test void shouldRestrictVisibilityAndWritingBeforeAndAfterAssignment() throws Exception {
        JsonNode empty = listar(owner, 200);
        assertThat(empty.get("content")).isEmpty();
        assertThat(empty.get("podeComentar").asBoolean()).isTrue();
        criar(owner, "Mensagem do solicitante", 201);
        criar(admin, "Mensagem do administrador", 201);
        for (Usuario outsider : List.of(otherRequester, attendant, otherAttendant)) {
            listar(outsider, 404);
            criar(outsider, "Mensagem proibida", 403);
        }
        atribuicao.assumir(ticket.id(), principal(attendant));
        criar(attendant, "Mensagem do responsavel", 201);
        assertThat(listar(attendant, 200).get("content")).hasSize(3);
        for (Usuario outsider : List.of(otherRequester, otherAttendant)) {
            listar(outsider, 404);
            criar(outsider, "Mensagem proibida", 403);
        }
        assertThat(listar(admin, 200).get("content")).hasSize(3);
    }

    @Test void shouldValidateTrimmedContentAtEveryBoundaryAndIgnoreForgedAuthor() throws Exception {
        for (String invalid : new String[] {null, "", " \t\n ", "\u00A0", "\u2003", "\uFEFF", "a".repeat(2001)}) {
            JsonNode error = criar(owner, invalid, 400);
            assertThat(error.at("/violations/0/field").asText()).isEqualTo("conteudo");
        }
        mvc.perform(post(url()).header("Authorization", token(owner)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.violations[0].field").value("conteudo"));
        for (int length : new int[] {1, 2000}) {
            JsonNode result = criar(owner, " \u00A0\u2003\uFEFF" + "x".repeat(length) + "\u00A0\u2003\uFEFF ", 201);
            assertThat(result.get("conteudo").asText()).hasSize(length);
            assertThat(result).hasSize(4);
            assertThat(result.get("autor")).hasSize(3);
        }
        mvc.perform(post(url()).header("Authorization", token(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"conteudo\":\"Mensagem autentica\",\"autorId\":" + admin.id()
                        + ",\"autor\":{\"id\":" + admin.id() + "},\"criadoEm\":\"1900-01-01T00:00:00Z\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.autor.id").value(owner.id()))
                .andExpect(jsonPath("$.autor.perfil").value("SOLICITANTE"));
        assertThat(count()).isEqualTo(3);
    }

    @Test void shouldKeepClosedDiscussionReadableAndRejectNewMessagesForAllParticipants() throws Exception {
        criar(owner, "Mensagem anterior ao fechamento", 201);
        fechar();
        for (Usuario actor : List.of(owner, attendant, admin)) {
            JsonNode result = listar(actor, 200);
            assertThat(result.get("content")).hasSize(1);
            assertThat(result.get("podeComentar").asBoolean()).isFalse();
            criar(actor, "Mensagem apos fechamento", 409);
        }
        criar(otherRequester, "Mensagem apos fechamento", 403);
        listar(otherRequester, 404);
        assertThat(count()).isEqualTo(1);
    }

    @Test void shouldRequireAuthenticationRejectInactiveAndHandleAbsentTickets() throws Exception {
        mvc.perform(get(url())).andExpect(status().isUnauthorized());
        mvc.perform(post(url()).contentType(MediaType.APPLICATION_JSON).content("{\"conteudo\":\"Teste\"}"))
                .andExpect(status().isUnauthorized());
        String inactiveToken = token(owner);
        owner.desativar(Instant.now());
        usuarios.save(owner);
        mvc.perform(get(url()).header("Authorization", inactiveToken)).andExpect(status().isUnauthorized());
        mvc.perform(post(url()).header("Authorization", inactiveToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"conteudo\":\"Teste\"}")).andExpect(status().isUnauthorized());
        for (String suffix : List.of("0", "999999999")) {
            int expected = suffix.equals("0") ? 400 : 404;
            String missing = "/api/chamados/" + suffix + "/comentarios";
            mvc.perform(get(missing).header("Authorization", token(admin))).andExpect(status().is(expected));
            mvc.perform(post(missing).header("Authorization", token(admin)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"conteudo\":\"Teste\"}")).andExpect(status().is(expected));
        }
    }

    @Test void shouldSortTimestampTiesByIdAndReturnCurrentAuthorProfileWithoutDuplicatedData() throws Exception {
        long first = criar(owner, "Primeiro comentario", 201).get("id").asLong();
        long second = criar(admin, "Segundo comentario", 201).get("id").asLong();
        long third = criar(owner, "Terceiro comentario", 201).get("id").asLong();
        jdbc.update("UPDATE comentarios SET criado_em=TIMESTAMP WITH TIME ZONE '2026-01-01 12:00:00+00' WHERE chamado_id=?", ticket.id());
        jdbc.update("UPDATE usuarios SET perfil='ADMIN',nome='Nome atualizado' WHERE id=?", owner.id());
        JsonNode result = listar(admin, 200);
        assertThat(result.at("/content/0/id").asLong()).isEqualTo(first);
        assertThat(result.at("/content/1/id").asLong()).isEqualTo(second);
        assertThat(result.at("/content/2/id").asLong()).isEqualTo(third);
        assertThat(result.at("/content/0/autor/perfil").asText()).isEqualTo("ADMIN");
        assertThat(result.at("/content/0/autor/nome").asText()).isEqualTo("Nome atualizado");
        assertThat(result.at("/content/0/autor")).hasSize(3);
        assertThat(result.at("/content/0/autor/email").isMissingNode()).isTrue();
    }

    @Test void shouldNotOfferEditOrDeleteAndNeverMutateTicketOrHistory() throws Exception {
        atribuicao.assumir(ticket.id(), principal(attendant));
        Chamado before = chamados.findById(ticket.id()).orElseThrow();
        long id = criar(owner, "Conteudo imutavel", 201).get("id").asLong();
        for (String suffix : List.of("", "/" + id)) {
            int expected = suffix.isEmpty() ? 405 : 404;
            for (org.springframework.http.HttpMethod method : List.of(org.springframework.http.HttpMethod.PUT,
                    org.springframework.http.HttpMethod.PATCH, org.springframework.http.HttpMethod.DELETE))
                mvc.perform(request(method, url() + suffix).header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"conteudo\":\"Tentativa de edicao\"}"))
                        .andExpect(status().is(expected));
        }
        assertThat(count()).isEqualTo(1);
        assertThat(listar(owner, 200).at("/content/0/conteudo").asText()).isEqualTo("Conteudo imutavel");
        Chamado after = chamados.findById(ticket.id()).orElseThrow();
        assertThat(after.versao()).isEqualTo(before.versao());
        assertThat(after.status()).isEqualTo(before.status());
        assertThat(after.atualizadoEm()).isEqualTo(before.atualizadoEm());
        assertThat(after.atribuidoEm()).isEqualTo(before.atribuidoEm());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM historico_status WHERE chamado_id=?", Long.class, ticket.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM historico_chamados WHERE chamado_id=?", Long.class, ticket.id())).isEqualTo(1);
    }

    @Test void shouldWaitForConcurrentClosingAndRejectCommentAfterItsCommit() throws Exception {
        atribuicao.assumir(ticket.id(), principal(attendant));
        ciclo.alterarStatus(ticket.id(), principal(attendant), new AlterarStatusRequest(StatusChamado.RESOLVIDO, "Solucao verificada"));
        CountDownLatch closedRowLocked = new CountDownLatch(1);
        CountDownLatch releaseCommit = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var closing = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                Chamado c = chamados.findById(ticket.id()).orElseThrow();
                c.alterarStatus(owner, StatusChamado.FECHADO, null, Instant.now());
                chamados.saveAndFlush(c);
                closedRowLocked.countDown();
                try { if (!releaseCommit.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Commit nao liberado"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }));
            assertThat(closedRowLocked.await(10, TimeUnit.SECONDS)).isTrue();
            var commenting = pool.submit(() -> {
                try { return service.criar(ticket.id(), principal(owner), new CriarComentarioRequest("Comentario concorrente")); }
                catch (ConflitoException e) { return e; }
            });
            try {
                assertThatThrownBy(() -> commenting.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally { releaseCommit.countDown(); }
            closing.get(10, TimeUnit.SECONDS);
            assertThat(commenting.get(10, TimeUnit.SECONDS)).isInstanceOf(ConflitoException.class);
            assertThat(count()).isZero();
            assertThat(chamados.findById(ticket.id()).orElseThrow().status()).isEqualTo(StatusChamado.FECHADO);
        } finally { releaseCommit.countDown(); }
    }

    @Test void shouldRollbackFailedInsertionAndDocumentOnlyGetAndPost() throws Exception {
        jdbc.execute("ALTER TABLE comentarios ADD CONSTRAINT test_comment_failure CHECK (chamado_id <> " + ticket.id() + ")");
        try { criar(owner, "Mensagem com falha simulada", 500); assertThat(count()).isZero(); }
        finally { jdbc.execute("ALTER TABLE comentarios DROP CONSTRAINT test_comment_failure"); }
        assertThat(chamados.findById(ticket.id()).orElseThrow().versao()).isZero();
        JsonNode spec = json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode path = spec.at("/paths/~1api~1chamados~1{id}~1comentarios");
        assertThat(path.has("get")).isTrue();
        assertThat(path.has("post")).isTrue();
        assertThat(path.has("put") || path.has("patch") || path.has("delete")).isFalse();
        for (String code : List.of("201", "400", "401", "403", "404", "409"))
            assertThat(path.at("/post/responses/" + code).isMissingNode()).isFalse();
    }

    private void fechar() {
        atribuicao.assumir(ticket.id(), principal(attendant));
        ciclo.alterarStatus(ticket.id(), principal(attendant), new AlterarStatusRequest(StatusChamado.RESOLVIDO, "Solucao verificada"));
        ciclo.alterarStatus(ticket.id(), principal(owner), new AlterarStatusRequest(StatusChamado.FECHADO, null));
    }
    private JsonNode criar(Usuario actor, String text, int expected) throws Exception {
        return json.readTree(mvc.perform(post(url()).header("Authorization", token(actor)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CriarComentarioRequest(text)))).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString());
    }
    private JsonNode listar(Usuario actor, int expected) throws Exception {
        return json.readTree(mvc.perform(get(url()).header("Authorization", token(actor))).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString());
    }
    private long count() { return jdbc.queryForObject("SELECT COUNT(*) FROM comentarios WHERE chamado_id=?", Long.class, ticket.id()); }
    private String url() { return "/api/chamados/" + ticket.id() + "/comentarios"; }
    private String token(Usuario actor) { return "Bearer " + jwt.generate(actor); }
    private UsuarioPrincipal principal(Usuario actor) { return new UsuarioPrincipal(actor.id(), actor.email(), actor.perfil()); }
    private Usuario usuario(Perfil role) { return usuarios.save(Usuario.novo("Pessoa " + role, UUID.randomUUID() + "@example.com", "hash-ficticio", role, Instant.now())); }
}
