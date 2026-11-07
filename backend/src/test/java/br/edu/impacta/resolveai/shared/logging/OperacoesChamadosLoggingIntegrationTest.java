package br.edu.impacta.resolveai.shared.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import br.edu.impacta.resolveai.categoria.*;
import br.edu.impacta.resolveai.chamado.*;
import br.edu.impacta.resolveai.security.JwtService;
import br.edu.impacta.resolveai.usuario.*;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"logging.level.br.edu.impacta.resolveai.shared.logging.OperacoesChamadosLog=INFO",
        "app.chamados.exportacao.max-registros=2",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=br.edu.impacta.resolveai.shared.logging.OperacoesChamadosLoggingIntegrationTest$SqlStatements"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class OperacoesChamadosLoggingIntegrationTest {
    public static class SqlStatements implements org.hibernate.resource.jdbc.spi.StatementInspector {
        static final List<String> statements = new java.util.concurrent.CopyOnWriteArrayList<>();
        @Override public String inspect(String sql) { statements.add(sql); return sql; }
    }
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired CategoriaRepository categorias;
    @Autowired ChamadoRepository chamados;
    @Autowired JwtService jwt;
    final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    final Logger logger = (Logger) LoggerFactory.getLogger(OperacoesChamadosLog.class);
    Usuario own, agent, otherAgent, admin;
    Categoria cat;
    final Instant date = Instant.parse("2026-09-15T00:00:00Z");

    @BeforeEach void setup() {
        own=user(Perfil.SOLICITANTE); agent=user(Perfil.ATENDENTE); otherAgent=user(Perfil.ATENDENTE); admin=user(Perfil.ADMIN);
        cat=categorias.save(Categoria.nova("Categoria fictícia "+UUID.randomUUID(),null,date));
        ticket(own,agent); ticket(own,agent); ticket(own,null); ticket(user(Perfil.SOLICITANTE),otherAgent);
        SqlStatements.statements.clear();
        appender.start(); logger.addAppender(appender);
    }
    @AfterEach void cleanup() { logger.detachAppender(appender); appender.stop(); assertThat(MDC.get("requisicaoId")).isNull(); }
    String logs() { return appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a,b)->a+"\n"+b); }

    @Test void queryWithoutOptionalFiltersIdentifiesItExplicitly() throws Exception {
        mvc.perform(get("/api/chamados/me").header("Authorization", "Bearer " + jwt.generate(own)))
                .andExpect(status().isOk());
        assertThat(logs()).contains("escopo=SOLICITANTE_PROPRIOS filtros=nenhum", "totalElementos=3")
                .doesNotContain("status=null", "prioridade=null");
    }

    @Test void queryLogsMatchActualAuthorizedFiltersPageAndSorting() throws Exception {
        mvc.perform(get("/api/chamados/me").header("Authorization", "Bearer "+jwt.generate(own))
                .param("search","Título privado").param("status","EM_ATENDIMENTO").param("prioridade","ALTA").param("categoriaId",cat.id().toString())
                .param("dataInicial","2026-09-01").param("dataFinal","2026-09-30").param("responsavel",agent.id().toString())
                .param("page","1").param("size","1").param("sort","titulo,asc"))
                .andExpect(status().isOk());
        assertThat(logs()).contains("escopo=SOLICITANTE_PROPRIOS", "filtros=[status=EM_ATENDIMENTO prioridade=ALTA", "dataInicial=2026-09-01", "dataFinal=2026-09-30",
                "ordenacao=titulo,ASC;id,DESC", "pagina=1 tamanho=1 totalElementos=2 totalPaginas=2 registrosRetornados=1 paginaVazia=false");
        var sql = SqlStatements.statements.stream().filter(statement -> statement.contains("from chamados")).toList();
        assertThat(sql).hasSize(2); // SELECT paginado + COUNT existente; nenhum SELECT extra para logs.
        assertThat(sql).anySatisfy(statement -> assertThat(statement).contains("offset ? rows fetch first ? rows only",
                "status=?", "prioridade=?", "categoria_id=?", "atendente_id=?", "criado_em>=?", "criado_em<?", "like ?"));
        var ids=new HashSet<String>(); var matcher=Pattern.compile("requisicaoId=([0-9a-f-]{36})").matcher(logs()); while(matcher.find())ids.add(matcher.group(1));
        assertThat(ids).hasSize(1); assertThat(appender.list).hasSize(2);
        assertThat(logs()).doesNotContain(own.email(),agent.email(),"Título privado","Descrição privada",jwt.generate(own));
        appender.list.clear();
        mvc.perform(get("/api/chamados").header("Authorization","Bearer "+jwt.generate(own)).param("categoriaId",cat.id().toString()).param("page","8").param("size","1")).andExpect(status().isOk());
        assertThat(logs()).contains("pagina=8 tamanho=1 totalElementos=3 totalPaginas=3 registrosRetornados=0 paginaVazia=true");
        appender.list.clear();
        mvc.perform(get("/api/chamados").header("Authorization","Bearer "+jwt.generate(own)).param("search","segredo-injetado\r\nFORJADO")).andExpect(status().isOk());
        assertThat(logs()).contains("pesquisaAtiva=true").doesNotContain("segredo-injetado","FORJADO");
    }

    @Test void logsAllCurrentListRoutesWithTheirRealScopes() throws Exception {
        for (var entry : List.of(new Object[]{"/api/chamados",admin,"ADMIN_GLOBAL",4},
                new Object[]{"/api/chamados",agent,"ATENDENTE_ATRIBUIDOS",2},
                new Object[]{"/api/atendimento/me",agent,"CARTEIRA_PROPRIA",2},
                new Object[]{"/api/atendimento/fila",agent,"FILA_ABERTA_SEM_RESPONSAVEL",1},
                new Object[]{"/api/atendimento/todos",admin,"ADMIN_GLOBAL",4})) {
            appender.list.clear();
            mvc.perform(get((String)entry[0]).header("Authorization","Bearer "+jwt.generate((Usuario)entry[1]))
                    .param("categoriaId",cat.id().toString()).param("size","1")).andExpect(status().isOk());
            assertThat(logs()).contains("escopo="+entry[2],"totalElementos="+entry[3],"registrosRetornados=1");
            assertThat(appender.list).hasSize(2);
        }
    }

    @Test void exportLogsCountsCompletionLimitAndInvalidRejectionsWithoutDuplicates() throws Exception {
        mvc.perform(get("/api/chamados/exportacao").header("Authorization","Bearer "+jwt.generate(own))
                .param("categoriaId",cat.id().toString()).param("status","EM_ATENDIMENTO").param("page","1").param("size","1")).andExpect(status().isOk());
        assertThat(logs()).contains("INICIO", "CONTAGEM", "totalElementos=2 limite=2", "CONCLUIDA", "registrosExportados=2");
        assertThat(appender.list).hasSize(3);
        appender.list.clear();
        mvc.perform(get("/api/chamados/exportacao").header("Authorization","Bearer "+jwt.generate(own))
                .param("categoriaId",cat.id().toString())).andExpect(status().isBadRequest());
        assertThat(logs()).contains("REJEITADA", "motivo=LIMITE_EXCEDIDO", "totalElementos=3", "registrosGerados=0").doesNotContain("CONCLUIDA");
        assertThat(appender.list).hasSize(3);
        appender.list.clear();
        mvc.perform(get("/api/chamados/exportacao").header("Authorization","Bearer "+jwt.generate(own))
                .param("sort","sql-fragmento-privado,asc")).andExpect(status().isBadRequest());
        assertThat(logs()).contains("REJEITADA", "motivo=PARAMETROS_INVALIDOS").doesNotContain("sql-fragmento-privado", "INICIO", "CONCLUIDA");
        assertThat(appender.list).hasSize(1);
        appender.list.clear();
        mvc.perform(get("/api/chamados/exportacao")).andExpect(status().isUnauthorized());
        assertThat(appender.list).isEmpty();
    }
    private Usuario user(Perfil perfil) { return usuarios.save(Usuario.novo("Pessoa fictícia",UUID.randomUUID()+"@example.invalid","hash-ficticio",perfil,date)); }
    private void ticket(Usuario u,Usuario a) {
        var c=Chamado.abrir("L-"+UUID.randomUUID().toString().substring(0,24),"Título privado","Descrição privada fictícia suficiente",PrioridadeChamado.ALTA,u,cat,date);
        if(a!=null)c.assumir(a,date); chamados.saveAndFlush(c);
    }
}
