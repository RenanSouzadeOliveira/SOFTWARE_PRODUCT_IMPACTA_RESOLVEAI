package br.edu.impacta.resolveai.chamado;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import br.edu.impacta.resolveai.atendimento.AtendimentoService;
import br.edu.impacta.resolveai.categoria.Categoria;
import br.edu.impacta.resolveai.categoria.CategoriaRepository;
import br.edu.impacta.resolveai.chamado.dto.AbrirChamadoRequest;
import br.edu.impacta.resolveai.chamado.dto.AlterarPrioridadeRequest;
import br.edu.impacta.resolveai.chamado.dto.AlterarStatusRequest;
import br.edu.impacta.resolveai.comentario.ComentarioService;
import br.edu.impacta.resolveai.comentario.dto.CriarComentarioRequest;
import br.edu.impacta.resolveai.dashboard.DashboardService;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.shared.error.NaoAutorizadoException;
import br.edu.impacta.resolveai.usuario.Perfil;
import br.edu.impacta.resolveai.usuario.Usuario;
import br.edu.impacta.resolveai.usuario.UsuarioRepository;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "AUTORIZACAO_POSTGRES17_URL", matches = "jdbc:postgresql:.+")
class AutorizacaoTransacionalPostgresTest {
    @Autowired UsuarioRepository usuarios;
    @Autowired CategoriaRepository categorias;
    @Autowired ChamadoRepository chamados;
    @Autowired ChamadoService abertura;
    @Autowired CicloChamadoService ciclo;
    @Autowired AtendimentoService atendimento;
    @Autowired ComentarioService comentarios;
    @Autowired DashboardService dashboard;
    @Autowired ExportacaoChamadosService exportacao;
    @Autowired OpcoesFiltrosChamados opcoes;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacoes;
    @Autowired BarreiraSql barreira;
    private static final FiltrosChamados SEM_FILTROS = new FiltrosChamados(null, null, null, null, null, null, null);
    private static final PageRequest PAGINA = PageRequest.of(0, 100, Sort.by("id"));

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> System.getenv("AUTORIZACAO_POSTGRES17_URL"));
        p.add("spring.datasource.username", () -> System.getenv().getOrDefault("AUTORIZACAO_POSTGRES17_USER", "postgres"));
        p.add("spring.datasource.password", () -> System.getenv().getOrDefault("AUTORIZACAO_POSTGRES17_PASSWORD", ""));
        p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @ParameterizedTest
    @CsvSource({"ABRIR,false", "ASSUMIR,false", "STATUS,false", "PRIORIDADE,false", "COMENTAR,false",
                "ABRIR,true", "ASSUMIR,true", "STATUS,true", "PRIORIDADE,true", "COMENTAR,true"})
    void mutacaoIniciadaPrimeiroMantemAtorElegivelAteCommit(String operacao, boolean mudarPerfil) throws Exception {
        var caso = preparar(operacao);
        var chegou = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var pidAtor = new java.util.concurrent.atomic.AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var escrita = pool.submit(() -> {
                barreira.antesDaConsulta.set(() -> {
                    pidAtor.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    chegou.countDown();
                    aguardar(liberar);
                });
                try { return caso.executar.get(); }
                finally { barreira.antesDaConsulta.remove(); }
            });
            try {
                assertThat(chegou.await(10, TimeUnit.SECONDS)).isTrue();
                var revogacao = pool.submit(() -> revogar(caso.ator.id(), mudarPerfil));
                aguardarBloqueio(pidAtor.get());
                liberar.countDown();
                escrita.get(10, TimeUnit.SECONDS);
                revogacao.get(10, TimeUnit.SECONDS);
                verificar(caso, true);
                assertThat(jdbc.queryForObject("SELECT versao_seguranca FROM usuarios WHERE id=?", Long.class, caso.ator.id())).isEqualTo(1);
            } finally { liberar.countDown(); }
        }
    }

    @ParameterizedTest
    @CsvSource({"ABRIR,false", "ASSUMIR,false", "STATUS,false", "PRIORIDADE,false", "COMENTAR,false",
                "ABRIR,true", "ASSUMIR,true", "STATUS,true", "PRIORIDADE,true", "COMENTAR,true"})
    void revogacaoIniciadaPrimeiroImpedeMutacaoAposCommit(String operacao, boolean mudarPerfil) throws Exception {
        var caso = preparar(operacao);
        var chegou = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var pidRevogacao = new java.util.concurrent.atomic.AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var revogacao = pool.submit(() -> transacao().executeWithoutResult(tx -> {
                pidRevogacao.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                revogar(caso.ator.id(), mudarPerfil);
                chegou.countDown();
                aguardar(liberar);
            }));
            try {
                assertThat(chegou.await(10, TimeUnit.SECONDS)).isTrue();
                var escrita = pool.submit(() -> {
                    try { caso.executar.get(); return null; }
                    catch (RuntimeException ex) { return ex; }
                });
                aguardarBloqueio(pidRevogacao.get());
                liberar.countDown();
                revogacao.get(10, TimeUnit.SECONDS);
                assertThat(escrita.get(10, TimeUnit.SECONDS)).isInstanceOf(NaoAutorizadoException.class);
                verificar(caso, false);
            } finally { liberar.countDown(); }
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void aberturaEDesativacaoDaCategoriaRespeitamOrdemDeCommit(boolean aberturaPrimeiro) throws Exception {
        var caso = preparar("ABRIR");
        Long categoriaId = caso.chamado.categoria().id();
        var chegou = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var bloqueador = new java.util.concurrent.atomic.AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var primeiro = pool.submit(() -> {
                if (aberturaPrimeiro) {
                    barreira.antesDaInsercao.set(() -> {
                        bloqueador.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                        chegou.countDown();
                        aguardar(liberar);
                    });
                    try { return caso.executar.get(); }
                    finally { barreira.antesDaInsercao.remove(); }
                }
                transacao().executeWithoutResult(tx -> {
                    bloqueador.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    jdbc.update("UPDATE categorias SET ativa=false WHERE id=?", categoriaId);
                    chegou.countDown();
                    aguardar(liberar);
                });
                return null;
            });
            try {
                assertThat(chegou.await(10, TimeUnit.SECONDS)).isTrue();
                var segundo = pool.submit(() -> {
                    try {
                        if (aberturaPrimeiro) jdbc.update("UPDATE categorias SET ativa=false WHERE id=?", categoriaId);
                        else caso.executar.get();
                        return null;
                    } catch (RuntimeException ex) { return ex; }
                });
                aguardarBloqueio(bloqueador.get(), "categorias");
                liberar.countDown();
                primeiro.get(10, TimeUnit.SECONDS);
                var resultado = segundo.get(10, TimeUnit.SECONDS);
                if (aberturaPrimeiro) assertThat(resultado).isNull();
                else {
                    assertThat(resultado).isInstanceOf(br.edu.impacta.resolveai.shared.error.DadosInvalidosException.class);
                    var erro = (br.edu.impacta.resolveai.shared.error.DadosInvalidosException) resultado;
                    assertThat(erro).hasMessage("Categoria indisponível. Escolha outra categoria.");
                    assertThat(erro.violations()).containsExactly(new br.edu.impacta.resolveai.shared.error.FieldViolation(
                            "categoriaId", "Categoria indisponível. Escolha outra categoria."));
                }
                verificar(caso, aberturaPrimeiro);
                assertThat(jdbc.queryForObject("SELECT ativa FROM categorias WHERE id=?", Boolean.class, categoriaId)).isFalse();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM chamados WHERE titulo=? AND categoria_id=?", Long.class,
                        caso.titulo, categoriaId)).isEqualTo(aberturaPrimeiro ? 1L : 0L);
            } finally { liberar.countDown(); }
        }
    }

    @Test
    void principalAntigoNaoRecuperaAcessoQuandoPerfilEReestabelecido() {
        var caso = preparar("COMENTAR");
        revogar(caso.ator.id(), true);
        jdbc.update("UPDATE usuarios SET perfil=?, versao_seguranca=versao_seguranca+1 WHERE id=?",
                caso.ator.perfil().name(), caso.ator.id());
        for (var leitura : leituras(caso)) assertThatThrownBy(leitura::get).isInstanceOf(NaoAutorizadoException.class);
        assertThatThrownBy(caso.executar::get).isInstanceOf(NaoAutorizadoException.class);
        verificar(caso, false);
    }

    @Test
    void leiturasUsamSnapshotDeVisibilidadeSemBloquearRevogacaoConcorrente() {
        // Cada serviço revalida o ator, depois compartilha o snapshot com suas consultas de dados.
        for (int i = 0; i < 10; i++) {
            var caso = preparar("COMENTAR");
            final int indice = i;
            barreira.antesDaConsulta.set(() -> {
                var tx = transacao();
                tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                tx.executeWithoutResult(status -> revogar(caso.ator.id(), false));
            });
            try {
                assertThatCode(() -> leituras(caso).get(indice).get()).doesNotThrowAnyException();
                assertThat(barreira.antesDaConsulta.get()).isNull();
            } finally { barreira.antesDaConsulta.remove(); }
            assertThatThrownBy(() -> leituras(caso).get(indice).get()).isInstanceOf(NaoAutorizadoException.class);
            assertThat(jdbc.queryForObject("SELECT ativo FROM usuarios WHERE id=?", Boolean.class, caso.ator.id())).isFalse();
        }
    }

    private java.util.List<Supplier<?>> leituras(Caso c) {
        var p = principal(c.ator);
        return java.util.List.of(
                () -> abertura.listar(p, PAGINA, SEM_FILTROS), () -> abertura.listarMeus(p, PAGINA),
                () -> ciclo.detalhar(c.chamado.id(), p), () -> ciclo.historico(c.chamado.id(), p),
                () -> comentarios.listar(c.chamado.id(), p), () -> dashboard.resumir(p),
                () -> exportacao.exportar(p, SEM_FILTROS, Sort.by("id")), () -> opcoes.listar(p),
                () -> atendimento.todos(p, PAGINA, SEM_FILTROS),
                () -> atendimento.listarFiltrados(p, PAGINA, SEM_FILTROS, false));
    }

    private void revogar(Long id, boolean mudarPerfil) {
        jdbc.update(mudarPerfil
                ? "UPDATE usuarios SET perfil=CASE WHEN perfil='ADMIN' THEN 'SOLICITANTE' ELSE 'ADMIN' END, versao_seguranca=versao_seguranca+1 WHERE id=?"
                : "UPDATE usuarios SET ativo=false, versao_seguranca=versao_seguranca+1 WHERE id=?", id);
    }

    private void aguardarBloqueio(int bloqueador) throws Exception {
        aguardarBloqueio(bloqueador, "usuarios");
    }

    private void aguardarBloqueio(int bloqueador, String tabela) throws Exception {
        assertThat(jdbc.queryForObject("SHOW server_version_num", Integer.class)).isBetween(170000, 179999);
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < limite) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid)) AND query ILIKE ?)", Boolean.class, bloqueador, "%" + tabela + "%"))) return;
            Thread.sleep(10);
        }
        fail("Não houve bloqueio SQL do ator dentro do prazo");
    }

    private void aguardar(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Barreira SQL não liberada"); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
    }

    private Caso preparar(String operacao) {
        var ator = usuario(operacao.equals("ABRIR") ? Perfil.SOLICITANTE : operacao.equals("ASSUMIR") ? Perfil.ATENDENTE : Perfil.ADMIN);
        var categoria = categorias.save(Categoria.nova("Categoria " + UUID.randomUUID(), null, Instant.now()));
        var chamado = Chamado.abrir("T-" + UUID.randomUUID().toString().substring(0, 24), "Título fictício",
                "Descrição suficientemente longa", PrioridadeChamado.MEDIA, usuario(Perfil.SOLICITANTE), categoria, Instant.now());
        if (operacao.equals("STATUS")) chamado.assumir(ator, Instant.now());
        chamados.saveAndFlush(chamado);
        var titulo = "Concorrência " + UUID.randomUUID();
        var p = principal(ator);
        Supplier<?> executar = switch (operacao) {
            case "ABRIR" -> () -> abertura.abrir(p, new AbrirChamadoRequest(titulo, "Descrição suficientemente longa", categoria.id(), PrioridadeChamado.MEDIA));
            case "ASSUMIR" -> () -> atendimento.assumir(chamado.id(), p);
            case "STATUS" -> () -> ciclo.alterarStatus(chamado.id(), p, new AlterarStatusRequest(StatusChamado.RESOLVIDO, "Solução fictícia registrada"));
            case "PRIORIDADE" -> () -> ciclo.alterarPrioridade(chamado.id(), p, new AlterarPrioridadeRequest(PrioridadeChamado.ALTA));
            default -> () -> comentarios.criar(chamado.id(), p, new CriarComentarioRequest("Mensagem fictícia concorrente"));
        };
        return new Caso(ator, chamado, operacao, titulo, executar);
    }

    private void verificar(Caso c, boolean persistiu) {
        long esperado = persistiu ? 1 : 0;
        if (c.operacao.equals("ABRIR")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM chamados WHERE solicitante_id=? AND titulo=?", Long.class, c.ator.id(), c.titulo)).isEqualTo(esperado);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM historico_chamados h JOIN chamados c ON c.id=h.chamado_id WHERE c.titulo=?", Long.class, c.titulo)).isEqualTo(esperado);
        } else {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM comentarios WHERE chamado_id=?", Long.class, c.chamado.id())).isEqualTo(c.operacao.equals("COMENTAR") ? esperado : 0);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM historico_status WHERE chamado_id=?", Long.class, c.chamado.id())).isEqualTo(c.operacao.equals("STATUS") || c.operacao.equals("ASSUMIR") ? esperado : 0);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM historico_chamados WHERE chamado_id=?", Long.class, c.chamado.id())).isEqualTo(c.operacao.equals("PRIORIDADE") || c.operacao.equals("ASSUMIR") ? esperado : 0);
            var salvo = chamados.findById(c.chamado.id()).orElseThrow();
            assertThat(salvo.status()).isEqualTo(persistiu && c.operacao.equals("STATUS") ? StatusChamado.RESOLVIDO : persistiu && c.operacao.equals("ASSUMIR") || c.operacao.equals("STATUS") ? StatusChamado.EM_ATENDIMENTO : StatusChamado.ABERTO);
            assertThat(salvo.prioridade()).isEqualTo(persistiu && c.operacao.equals("PRIORIDADE") ? PrioridadeChamado.ALTA : PrioridadeChamado.MEDIA);
        }
    }

    private Usuario usuario(Perfil perfil) {
        return usuarios.save(Usuario.novo("Pessoa fictícia", UUID.randomUUID() + "@example.invalid", "hash-fictício", perfil, Instant.now()));
    }
    private UsuarioPrincipal principal(Usuario u) { return new UsuarioPrincipal(u.id(), u.email(), u.perfil()); }
    private TransactionTemplate transacao() { return new TransactionTemplate(transacoes); }
    record Caso(Usuario ator, Chamado chamado, String operacao, String titulo, Supplier<?> executar) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuracao {
        @Bean BarreiraSql barreiraSql() { return new BarreiraSql(); }
        @Bean HibernatePropertiesCustomizer inspecao(BarreiraSql b) { return p -> p.put("hibernate.session_factory.statement_inspector", b); }
    }
    static class BarreiraSql implements StatementInspector {
        final ThreadLocal<Runnable> antesDaConsulta = new ThreadLocal<>();
        final ThreadLocal<Runnable> antesDaInsercao = new ThreadLocal<>();
        @Override public String inspect(String sql) {
            if (antesDaInsercao.get() != null && sql.startsWith("insert into chamados ")) {
                var acao = antesDaInsercao.get();
                antesDaInsercao.remove();
                acao.run();
            }
            if (antesDaConsulta.get() != null && (sql.contains(" from chamados ") || sql.contains(" from categorias "))) {
                var acao = antesDaConsulta.get();
                antesDaConsulta.remove();
                acao.run();
            }
            return sql;
        }
    }
}
