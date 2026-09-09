package br.edu.impacta.resolveai.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import br.edu.impacta.resolveai.categoria.Categoria;
import br.edu.impacta.resolveai.chamado.Chamado;
import br.edu.impacta.resolveai.chamado.PrioridadeChamado;
import br.edu.impacta.resolveai.chamado.StatusChamado;
import br.edu.impacta.resolveai.usuario.Perfil;
import br.edu.impacta.resolveai.usuario.Usuario;
import org.junit.jupiter.api.Test;

class DomainFactoryTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-08T12:00:00Z");

    @Test
    void shouldNormalizeNewUserEmail() {
        Usuario usuario = Usuario.novo(
                "Pessoa Solicitante",
                "  Pessoa.Teste@Example.COM  ",
                "hash-ficticio",
                Perfil.SOLICITANTE,
                CREATED_AT);

        assertThat(usuario.email()).isEqualTo("pessoa.teste@example.com");
        assertThat(usuario.ativo()).isTrue();
        assertThat(usuario.criadoEm()).isEqualTo(CREATED_AT);
        assertThat(usuario.atualizadoEm()).isEqualTo(CREATED_AT);
    }

    @Test
    void shouldCreateActiveCategoryAndOpenTicketWithDomainDefaults() {
        Usuario solicitante = newSolicitante();
        Categoria categoria = Categoria.nova("Suporte", "Problemas gerais", CREATED_AT);

        Chamado chamado = Chamado.abrir(
                "RES-2026-0001",
                "Falha de acesso",
                "Nao foi possivel acessar o sistema.",
                solicitante,
                categoria,
                CREATED_AT);

        assertThat(categoria.ativa()).isTrue();
        assertThat(chamado.status()).isEqualTo(StatusChamado.ABERTO);
        assertThat(chamado.prioridade()).isEqualTo(PrioridadeChamado.MEDIA);
        assertThat(chamado.solicitante()).isSameAs(solicitante);
        assertThat(chamado.categoria()).isSameAs(categoria);
        assertThat(chamado.atendente()).isNull();
        assertThat(chamado.solucao()).isNull();
        assertThat(chamado.resolvidoEm()).isNull();
        assertThat(chamado.fechadoEm()).isNull();
    }

    @Test
    void shouldRejectTicketForInactiveCategory() {
        Categoria categoria = Categoria.nova("Legado", null, CREATED_AT);
        categoria.desativar(CREATED_AT.plusSeconds(60));

        assertThatThrownBy(() -> Chamado.abrir(
                "RES-2026-0002",
                "Falha",
                "Descricao valida",
                newSolicitante(),
                categoria,
                CREATED_AT.plusSeconds(120)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("categoria inativa");
    }

    private Usuario newSolicitante() {
        return Usuario.novo(
                "Pessoa Solicitante",
                "solicitante@example.com",
                "hash-ficticio",
                Perfil.SOLICITANTE,
                CREATED_AT);
    }
}
