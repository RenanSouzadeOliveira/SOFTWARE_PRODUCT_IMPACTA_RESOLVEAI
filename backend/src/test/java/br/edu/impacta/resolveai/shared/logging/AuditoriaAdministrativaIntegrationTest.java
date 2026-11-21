package br.edu.impacta.resolveai.shared.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.shared.error.ConflitoException;
import br.edu.impacta.resolveai.usuario.AdministracaoUsuarioService;
import br.edu.impacta.resolveai.usuario.Perfil;
import br.edu.impacta.resolveai.usuario.Usuario;
import br.edu.impacta.resolveai.usuario.UsuarioRepository;
import br.edu.impacta.resolveai.usuario.dto.CriarUsuarioRequest;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// A-08: eventos administrativos só são registrados após o commit, com ator e alvo e sem dados pessoais.
@SpringBootTest
@ActiveProfiles("test")
class AuditoriaAdministrativaIntegrationTest {
    private static final String SENHA = "SenhaFicticia123";
    private static final String EMAIL_PESSOA = "pessoa-log@example.com";
    private static final String EMAIL_REVERTIDA = "revertida-log@example.com";
    @Autowired AdministracaoUsuarioService administracao;
    @Autowired UsuarioRepository usuarios;
    @Autowired PlatformTransactionManager transacoes;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(AuditoriaAdministrativa.class);
    private UsuarioPrincipal admin;

    @BeforeEach
    void preparar() {
        var conta = usuarios.saveAndFlush(Usuario.novo("Admin auditoria", java.util.UUID.randomUUID() + "-admin-log@example.com",
                "hash-ficticio", Perfil.ADMIN, Instant.now()));
        admin = new UsuarioPrincipal(conta.id(), conta.email(), conta.perfil(), conta.versaoSeguranca());
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void limpar() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void registraCriacaoComAtorEAlvoSomenteAposCommit() {
        var criado = administracao.criar(admin, new CriarUsuarioRequest(
                "Pessoa fictícia", EMAIL_PESSOA, Perfil.ATENDENTE, SENHA));

        assertThat(logs()).contains("evento=USUARIO_CRIADO_POR_ADMIN", "resultado=CONFIRMADO",
                "atorId=" + admin.id(), "alvoId=" + criado.id(), "perfil=ATENDENTE")
                .doesNotContain(EMAIL_PESSOA, SENHA, "Pessoa fictícia", "hash");
    }

    @Test
    void naoRegistraEventoQuandoTransacaoReverte() {
        new TransactionTemplate(transacoes).executeWithoutResult(status -> {
            administracao.criar(admin, new CriarUsuarioRequest(
                    "Revertida", EMAIL_REVERTIDA, Perfil.SOLICITANTE, SENHA));
            status.setRollbackOnly();
        });
        assertThat(usuarios.findByEmail(EMAIL_REVERTIDA)).isEmpty();

        assertThatThrownBy(() -> administracao.criar(admin, new CriarUsuarioRequest(
                "Duplicada", admin.email(), Perfil.SOLICITANTE, SENHA)))
                .isInstanceOf(ConflitoException.class);

        assertThat(logs()).doesNotContain("USUARIO_CRIADO_POR_ADMIN");
    }

    private String logs() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
    }
}
