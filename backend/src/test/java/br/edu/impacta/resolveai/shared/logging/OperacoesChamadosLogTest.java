package br.edu.impacta.resolveai.shared.logging;

import static org.assertj.core.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import br.edu.impacta.resolveai.chamado.*;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.usuario.Perfil;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OperacoesChamadosLogTest {
    final Logger logger = (Logger) LoggerFactory.getLogger(OperacoesChamadosLog.class);
    final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    Level previous;
    @BeforeEach void setup() { previous = logger.getLevel(); logger.setLevel(Level.INFO); appender.start(); logger.addAppender(appender); MDC.clear(); }
    @AfterEach void cleanup() { logger.detachAppender(appender); appender.stop(); logger.setLevel(previous); MDC.clear(); }
    String logs() { return appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a,b) -> a+"\n"+b); }

    @Test void filterGeneratesCorrelationAndRestoresItEvenOnFailure() throws Exception {
        var env = new MockEnvironment(); env.setActiveProfiles("dev");
        var filter = new ApiRequestLoggingFilter(env, false);
        var req = new MockHttpServletRequest("GET", "/api/chamados");
        req.addHeader("X-Request-ID", "nao-confiavel\r\nsegredo");
        filter.doFilter(req, new MockHttpServletResponse(), (request,response) -> {
            assertThatCode(() -> UUID.fromString(MDC.get("requisicaoId"))).doesNotThrowAnyException();
            assertThat(MDC.get("requisicaoId")).doesNotContain("nao-confiavel");
        });
        assertThat(MDC.get("requisicaoId")).isNull();
        MDC.put("requisicaoId", "anterior");
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("GET", "/api/chamados"),
                new MockHttpServletResponse(), (request,response) -> { throw new IllegalStateException("falha"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get("requisicaoId")).isEqualTo("anterior");
    }

    @Test void fictitiousTextRequiresOptInAndDevelopmentWithoutProduction() throws Exception {
        for (boolean enabled : new boolean[]{false, true}) {
            for (String[] profiles : new String[][]{{"dev"}, {"prod"}, {"dev", "prod"}, {"test"}}) {
                appender.list.clear();
                var env = new MockEnvironment(); env.setActiveProfiles(profiles);
                var req = new MockHttpServletRequest("GET", "/api/chamados"); req.setParameter("search", "AC3-ACADEMICO-V1");
                new ApiRequestLoggingFilter(env, enabled).doFilter(req, new MockHttpServletResponse(), (a,b) -> { });
                boolean expected = enabled && List.of(profiles).equals(List.of("dev"));
                assertThat(logs().contains("TEXTO_DEMO")).isEqualTo(expected);
            }
        }
        for (String text : List.of("email-pessoal@example.invalid", "AC3-ACADEMICO-V1\nsegredo", "' OR 1=1 --", "AC3-ACADEMICO-V1 " + "1".repeat(1000))) {
            appender.list.clear();
            var env = new MockEnvironment(); env.setActiveProfiles("dev");
            var req = new MockHttpServletRequest("GET", "/api/chamados/exportacao"); req.setParameter("search", text);
            new ApiRequestLoggingFilter(env, true).doFilter(req, new MockHttpServletResponse(), (a,b) -> { });
            assertThat(logs()).doesNotContain("TEXTO_DEMO", "segredo", "email-pessoal", "OR 1=1");
        }
    }

    @Test void recordsRealPageMetadataWithoutSearchContent() {
        var filters = new FiltrosChamados("texto privado com token", StatusChamado.ABERTO, PrioridadeChamado.ALTA, 3L, 5L,
                LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"));
        var request = PageRequest.of(2, 20, Sort.by(Sort.Order.asc("titulo"), Sort.Order.desc("id")));
        long start = OperacoesChamadosLog.inicio("CHAMADOS_CONSULTA", filters, request.getSort(), "SOLICITANTE_PROPRIOS");
        OperacoesChamadosLog.resultado(new PageImpl<>(List.of("a", "b"), request, 42), start);
        assertThat(logs()).contains("status=ABERTO", "prioridade=ALTA", "categoriaId=3", "responsavelId=5", "pesquisaAtiva=true",
                "ordenacao=titulo,ASC;id,DESC", "pagina=2 tamanho=20 totalElementos=42 totalPaginas=3 registrosRetornados=2 paginaVazia=false")
                .doesNotContain("texto privado", "token");
    }

    @Test void exportFailureIsLoggedOnceWithoutExceptionPayloadOrSuccess() {
        var calls = new AtomicInteger();
        var repo = (ChamadoRepository) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ChamadoRepository.class},
                (proxy,method,args) -> { calls.incrementAndGet(); throw new IllegalStateException("senha=segredo token=privado SQL"); });
        var service = new ExportacaoChamadosService(repo, null, 10000);
        assertThatThrownBy(() -> service.exportar(new UsuarioPrincipal(1L, "pessoa@example.invalid", Perfil.SOLICITANTE),
                new FiltrosChamados(null,null,null,null,null,null,null), Sort.by("id"))).isInstanceOf(IllegalStateException.class);
        assertThat(calls).hasValue(1);
        assertThat(logs()).contains("FALHOU", "motivo=EXECUCAO_FALHOU", "totalElementos=-1", "registrosGerados=0")
                .doesNotContain("CONCLUIDA", "segredo", "privado", "pessoa@example", "SQL");
        assertThat(appender.list.stream().filter(e -> e.getFormattedMessage().contains("FALHOU")).count()).isEqualTo(1);
    }
    @Test void exportFailureReportsRowsAlreadyGeneratedInTheCurrentBatch() {
        var date = java.time.Instant.parse("2026-09-01T00:00:00Z");
        var user = br.edu.impacta.resolveai.usuario.Usuario.novo("Pessoa fictícia", "ficticio@example.invalid", "hash", Perfil.SOLICITANTE, date);
        var category = br.edu.impacta.resolveai.categoria.Categoria.nova("Categoria fictícia", null, date);
        var valid = Chamado.abrir("LOG-001", "Título fictício", "Descrição fictícia para teste", user, category, date);
        var broken = new Chamado() {
            @Override public String protocolo() { throw new IllegalStateException("payload-privado"); }
        };
        var repo = (ChamadoRepository) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ChamadoRepository.class},
                (proxy,method,args) -> new PageImpl<>(List.of(valid, broken), (org.springframework.data.domain.Pageable) args[1], 2));
        var service = new ExportacaoChamadosService(repo, null, 10000);
        assertThatThrownBy(() -> service.exportar(new UsuarioPrincipal(1L, user.email(), Perfil.SOLICITANTE),
                new FiltrosChamados(null,null,null,null,null,null,null), Sort.by("id"))).isInstanceOf(IllegalStateException.class);
        assertThat(logs()).contains("FALHOU", "totalElementos=2", "registrosGerados=1").doesNotContain("CONCLUIDA", "payload-privado");
    }

}
