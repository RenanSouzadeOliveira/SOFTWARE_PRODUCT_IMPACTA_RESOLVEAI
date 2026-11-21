package br.edu.impacta.resolveai.dashboard;

import static br.edu.impacta.resolveai.chamado.PrioridadeChamado.ALTA;
import static br.edu.impacta.resolveai.chamado.PrioridadeChamado.BAIXA;
import static br.edu.impacta.resolveai.chamado.PrioridadeChamado.MEDIA;
import static br.edu.impacta.resolveai.chamado.StatusChamado.ABERTO;
import static br.edu.impacta.resolveai.chamado.StatusChamado.EM_ATENDIMENTO;
import static br.edu.impacta.resolveai.chamado.StatusChamado.FECHADO;
import static br.edu.impacta.resolveai.chamado.StatusChamado.RESOLVIDO;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import br.edu.impacta.resolveai.categoria.Categoria;
import br.edu.impacta.resolveai.categoria.CategoriaRepository;
import br.edu.impacta.resolveai.chamado.Chamado;
import br.edu.impacta.resolveai.chamado.ChamadoRepository;
import br.edu.impacta.resolveai.chamado.PrioridadeChamado;
import br.edu.impacta.resolveai.dashboard.dto.DashboardResumoResponse;
import br.edu.impacta.resolveai.dashboard.dto.DashboardResumoResponse.GrupoCategoria;
import br.edu.impacta.resolveai.dashboard.dto.DashboardResumoResponse.GrupoPrioridade;
import br.edu.impacta.resolveai.dashboard.dto.DashboardResumoResponse.GrupoStatus;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.usuario.Perfil;
import br.edu.impacta.resolveai.usuario.Usuario;
import br.edu.impacta.resolveai.usuario.UsuarioRepository;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "DASHBOARD_POSTGRES17_URL", matches = "jdbc:postgresql:.+")
class DashboardSnapshotPostgresTest {
    private static final Instant AGORA = Instant.parse("2026-10-01T12:00:00Z");

    @Autowired DashboardService dashboard;
    @Autowired UsuarioRepository usuarios;
    @Autowired CategoriaRepository categorias;
    @Autowired ChamadoRepository chamados;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacoes;
    @Autowired IntercalacaoSql intercalacao;

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry propriedades) {
        propriedades.add("spring.datasource.url", () -> System.getenv("DASHBOARD_POSTGRES17_URL"));
        propriedades.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("DASHBOARD_POSTGRES17_USER", "postgres"));
        propriedades.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("DASHBOARD_POSTGRES17_PASSWORD", ""));
        propriedades.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Test
    void preservaSnapshotQuandoOutroChamadoEInseridoEntreAgregacoes() {
        assertThat(jdbc.queryForObject("SHOW server_version_num", Integer.class)).isBetween(170000, 179999);
        var solicitante = usuario(Perfil.SOLICITANTE);
        var atendente = usuario(Perfil.ATENDENTE);
        var original = categoria();
        var nova = categoria();
        chamados.saveAndFlush(abrir(solicitante, original, BAIXA));

        var principal = new UsuarioPrincipal(solicitante.id(), solicitante.email(), solicitante.perfil());
        var antes = dashboard.resumir(principal);
        assertThat(antes.porStatus()).containsExactly(
                new GrupoStatus(ABERTO, 1), new GrupoStatus(EM_ATENDIMENTO, 0),
                new GrupoStatus(RESOLVIDO, 0), new GrupoStatus(FECHADO, 0));
        assertThat(antes.porPrioridade()).containsExactly(
                new GrupoPrioridade(BAIXA, 1), new GrupoPrioridade(MEDIA, 0), new GrupoPrioridade(ALTA, 0));
        assertThat(antes.porCategoria()).contains(grupo(original, 1), grupo(nova, 0));
        assertThat(antes.total()).isEqualTo(1);
        assertThat(antes.abertos()).isEqualTo(1);
        assertThat(antes.emAtendimento()).isZero();
        assertThat(antes.concluidos()).isZero();
        verificarSomas(antes);

        var escrita = new TransactionTemplate(transacoes);
        escrita.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        intercalacao.antesDoSegundoAgrupamento = () -> escrita.executeWithoutResult(transacao -> {
            var chamado = abrir(solicitante, nova, ALTA);
            chamado.assumir(atendente, AGORA.plusSeconds(1));
            chamado.alterarStatus(atendente, RESOLVIDO, "Solução fictícia registrada", AGORA.plusSeconds(2));
            chamados.saveAndFlush(chamado);
        });

        try {
            assertThat(dashboard.resumir(principal)).isEqualTo(antes);
            assertThat(intercalacao.antesDoSegundoAgrupamento).isNull();
        } finally {
            intercalacao.antesDoSegundoAgrupamento = null;
        }

        var depois = dashboard.resumir(principal);
        assertThat(depois.total()).isEqualTo(2);
        assertThat(depois.abertos()).isEqualTo(1);
        assertThat(depois.emAtendimento()).isZero();
        assertThat(depois.concluidos()).isEqualTo(1);
        assertThat(depois.porStatus()).containsExactly(
                new GrupoStatus(ABERTO, 1), new GrupoStatus(EM_ATENDIMENTO, 0),
                new GrupoStatus(RESOLVIDO, 1), new GrupoStatus(FECHADO, 0));
        assertThat(depois.porPrioridade()).containsExactly(
                new GrupoPrioridade(BAIXA, 1), new GrupoPrioridade(MEDIA, 0), new GrupoPrioridade(ALTA, 1));
        assertThat(depois.porCategoria()).contains(grupo(original, 1), grupo(nova, 1));
        verificarSomas(depois);
    }

    private void verificarSomas(DashboardResumoResponse resumo) {
        assertThat(resumo.porStatus().stream().mapToLong(GrupoStatus::quantidade).sum()).isEqualTo(resumo.total());
        assertThat(resumo.porPrioridade().stream().mapToLong(GrupoPrioridade::quantidade).sum()).isEqualTo(resumo.total());
        assertThat(resumo.porCategoria().stream().mapToLong(GrupoCategoria::quantidade).sum()).isEqualTo(resumo.total());
    }

    private Usuario usuario(Perfil perfil) {
        return usuarios.save(Usuario.novo("Pessoa fictícia", UUID.randomUUID() + "@example.invalid",
                "hash-de-teste", perfil, AGORA));
    }

    private Categoria categoria() {
        return categorias.save(Categoria.nova("Categoria fictícia " + UUID.randomUUID(), null, AGORA));
    }

    private Chamado abrir(Usuario solicitante, Categoria categoria, PrioridadeChamado prioridade) {
        return Chamado.abrir("S-" + UUID.randomUUID().toString().substring(0, 24), "Chamado fictício",
                "Descrição fictícia do chamado", prioridade, solicitante, categoria, AGORA);
    }

    private GrupoCategoria grupo(Categoria categoria, long quantidade) {
        return new GrupoCategoria(categoria.id(), categoria.nome(), quantidade);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracaoSql {
        @Bean IntercalacaoSql intercalacaoSql() {
            return new IntercalacaoSql();
        }

        @Bean HibernatePropertiesCustomizer configurarInspecao(IntercalacaoSql intercalacao) {
            return propriedades -> propriedades.put("hibernate.session_factory.statement_inspector", intercalacao);
        }
    }

    static class IntercalacaoSql implements StatementInspector {
        private Runnable antesDoSegundoAgrupamento;
        private int agrupamentos;

        @Override
        public String inspect(String sql) {
            if (antesDoSegundoAgrupamento != null && sql.contains(" from chamados ")
                    && sql.contains(" group by ") && ++agrupamentos == 2) {
                // O primeiro SELECT já fixou o snapshot. A escrita confirma antes do próximo SELECT.
                var escrita = antesDoSegundoAgrupamento;
                antesDoSegundoAgrupamento = null;
                escrita.run();
            }
            return sql;
        }
    }
}
