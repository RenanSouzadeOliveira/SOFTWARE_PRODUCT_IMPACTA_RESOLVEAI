package br.edu.impacta.resolveai.shared.logging;

import java.time.Clock;

import br.edu.impacta.resolveai.usuario.Perfil;
import br.edu.impacta.resolveai.usuario.dto.UsuarioResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AuditoriaAdministrativa {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditoriaAdministrativa.class);

    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public AuditoriaAdministrativa(ApplicationEventPublisher publisher, Clock clock) {
        this.publisher = publisher;
        this.clock = clock;
    }

    public void usuario(String operacao, long atorId, UsuarioResponse usuario) {
        publisher.publishEvent(new Mudanca(operacao, atorId, "USUARIO", usuario.id(),
                usuario.perfil(), usuario.ativo()));
    }

    public void categoria(String operacao, long administradorId, long categoriaId, boolean ativa) {
        publisher.publishEvent(new Mudanca(operacao, administradorId, "CATEGORIA", categoriaId, null, ativa));
    }

    @TransactionalEventListener
    void registrarCommit(Mudanca mudanca) {
        LOGGER.info("AUDITORIA evento={} resultado=CONFIRMADO atorId={} alvoTipo={} alvoId={} perfil={} ativo={} data={}",
                mudanca.operacao(), mudanca.atorId(), mudanca.alvoTipo(), mudanca.alvoId(),
                mudanca.perfil(), mudanca.ativo(), clock.instant());
    }

    record Mudanca(String operacao, long atorId, String alvoTipo, long alvoId, Perfil perfil, boolean ativo) { }
}
