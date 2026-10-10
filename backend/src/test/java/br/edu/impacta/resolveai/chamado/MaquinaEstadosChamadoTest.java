package br.edu.impacta.resolveai.chamado;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import br.edu.impacta.resolveai.categoria.Categoria;
import br.edu.impacta.resolveai.shared.error.ConflitoException;
import br.edu.impacta.resolveai.shared.error.DadosInvalidosException;
import br.edu.impacta.resolveai.usuario.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class MaquinaEstadosChamadoTest {
    private final Instant agora = Instant.parse("2026-01-01T12:00:00Z");
    private final Usuario solicitante = usuario(Perfil.SOLICITANTE);
    private final Usuario atendente = usuario(Perfil.ATENDENTE);
    private final Usuario admin = usuario(Perfil.ADMIN);

    @Test void shouldEnforceEveryStatePairAndNeverBypassAssignment() {
        for (StatusChamado origem : StatusChamado.values()) {
            for (StatusChamado destino : StatusChamado.values()) {
                Chamado c = emEstado(origem);
                boolean permitido = origem == StatusChamado.EM_ATENDIMENTO && destino == StatusChamado.RESOLVIDO
                        || origem == StatusChamado.RESOLVIDO && List.of(StatusChamado.FECHADO,
                        StatusChamado.EM_ATENDIMENTO).contains(destino);
                if (permitido) {
                    c.alterarStatus(admin, destino, "Justificativa suficientemente longa", agora.plusSeconds(1));
                    assertThat(c.status()).isEqualTo(destino);
                } else {
                    assertThatThrownBy(() -> c.alterarStatus(admin, destino, "Justificativa valida", agora))
                            .isInstanceOf(ConflitoException.class);
                    assertThat(c.status()).isEqualTo(origem);
                }
            }
        }
    }

    @Test void shouldAuthorizeActorsAndCalculateActions() {
        Chamado c = emEstado(StatusChamado.EM_ATENDIMENTO);
        assertThatThrownBy(() -> c.alterarStatus(solicitante, StatusChamado.RESOLVIDO, "Solucao valida", agora))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> c.alterarStatus(usuario(Perfil.ATENDENTE), StatusChamado.RESOLVIDO, "Solucao valida", agora))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(MaquinaEstadosChamado.acoesPermitidas(c, atendente))
                .containsExactly(AcaoChamado.RESOLVER, AcaoChamado.ALTERAR_PRIORIDADE);
        c.alterarStatus(atendente, StatusChamado.RESOLVIDO, "  Solucao valida  ", agora);
        assertThat(c.solucao()).isEqualTo("Solucao valida");
        assertThat(MaquinaEstadosChamado.acoesPermitidas(c, solicitante))
                .containsExactly(AcaoChamado.REABRIR, AcaoChamado.FECHAR);
        assertThatThrownBy(() -> c.alterarStatus(atendente, StatusChamado.FECHADO, null, agora))
                .isInstanceOf(AccessDeniedException.class);
        c.alterarStatus(solicitante, StatusChamado.EM_ATENDIMENTO, "A falha continua", agora.plusSeconds(1));
        assertThat(c.solucao()).isNull();
        assertThat(c.resolvidoEm()).isNull();
        assertThat(c.fechadoEm()).isNull();
        assertThat(c.atendente()).isSameAs(atendente);
        assertThat(c.atribuidoEm()).isEqualTo(agora);
    }

    @Test void shouldValidateTrimmedJustificationAndKeepInvalidMutationUnchanged() {
        for (String texto : new String[] {null, "", "   ", "123456789", "a".repeat(501)}) {
            Chamado c = emEstado(StatusChamado.EM_ATENDIMENTO);
            assertThatThrownBy(() -> c.alterarStatus(atendente, StatusChamado.RESOLVIDO, texto, agora))
                    .isInstanceOf(DadosInvalidosException.class);
            assertThat(c.status()).isEqualTo(StatusChamado.EM_ATENDIMENTO);
            assertThat(c.solucao()).isNull();
        }
        for (int tamanho : List.of(10, 500)) {
            Chamado c = emEstado(StatusChamado.EM_ATENDIMENTO);
            c.alterarStatus(atendente, StatusChamado.RESOLVIDO, "  " + "a".repeat(tamanho) + "  ", agora);
            assertThat(c.solucao()).hasSize(tamanho);
        }
    }

    @Test void shouldEnforcePriorityAndInactiveActors() {
        Chamado c = emEstado(StatusChamado.EM_ATENDIMENTO);
        c.alterarPrioridade(atendente, PrioridadeChamado.ALTA, agora);
        assertThatThrownBy(() -> c.alterarPrioridade(atendente, PrioridadeChamado.ALTA, agora))
                .isInstanceOf(ConflitoException.class);
        atendente.desativar(agora);
        assertThat(MaquinaEstadosChamado.acoesPermitidas(c, atendente)).isEmpty();
        assertThatThrownBy(() -> c.alterarPrioridade(atendente, PrioridadeChamado.BAIXA, agora))
                .isInstanceOf(AccessDeniedException.class);
        Chamado fechado = emEstado(StatusChamado.ABERTO);
        fechado.assumir(admin, agora);
        fechado.alterarStatus(admin, StatusChamado.RESOLVIDO, "Solucao definitiva", agora);
        fechado.alterarStatus(admin, StatusChamado.FECHADO, null, agora);
        assertThat(MaquinaEstadosChamado.acoesPermitidas(fechado, admin)).isEmpty();
        assertThatThrownBy(() -> fechado.alterarPrioridade(admin, PrioridadeChamado.ALTA, agora))
                .isInstanceOf(ConflitoException.class);
    }

    private Chamado emEstado(StatusChamado status) {
        Chamado c = Chamado.abrir("RA-TESTE", "Titulo valido", "Descricao com tamanho adequado", solicitante,
                Categoria.nova("Categoria", null, agora), agora);
        if (status != StatusChamado.ABERTO) c.assumir(atendente, agora);
        if (status == StatusChamado.RESOLVIDO || status == StatusChamado.FECHADO)
            c.alterarStatus(atendente, StatusChamado.RESOLVIDO, "Solucao definitiva", agora);
        if (status == StatusChamado.FECHADO) c.alterarStatus(solicitante, StatusChamado.FECHADO, null, agora);
        return c;
    }
    private Usuario usuario(Perfil perfil) {
        return Usuario.novo("Pessoa", "pessoa@example.com", "hash-ficticio", perfil, agora);
    }
}
