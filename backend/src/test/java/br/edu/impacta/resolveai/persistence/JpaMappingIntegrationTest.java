package br.edu.impacta.resolveai.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import br.edu.impacta.resolveai.categoria.Categoria;
import br.edu.impacta.resolveai.categoria.CategoriaRepository;
import br.edu.impacta.resolveai.chamado.Chamado;
import br.edu.impacta.resolveai.chamado.ChamadoRepository;
import br.edu.impacta.resolveai.chamado.PrioridadeChamado;
import br.edu.impacta.resolveai.chamado.StatusChamado;
import br.edu.impacta.resolveai.usuario.Perfil;
import br.edu.impacta.resolveai.usuario.Usuario;
import br.edu.impacta.resolveai.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class JpaMappingIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-08T12:00:00Z");

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ChamadoRepository chamadoRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldRoundTripTicketWithEnumsTimestampsDefaultsAndLazyRelationships() {
        Usuario solicitante = usuarioRepository.save(newUser("Roundtrip@Example.COM", Perfil.SOLICITANTE));
        Categoria categoria = categoriaRepository.save(Categoria.nova("Acesso", "Acessos internos", CREATED_AT));
        Chamado chamado = chamadoRepository.save(Chamado.abrir(
                "RES-2026-1001",
                "Acesso indisponivel",
                "O acesso parou de funcionar.",
                solicitante,
                categoria,
                CREATED_AT));
        entityManager.flush();
        Long chamadoId = chamado.id();
        Long solicitanteId = solicitante.id();
        Long categoriaId = categoria.id();
        entityManager.clear();

        Chamado persisted = chamadoRepository.findById(chamadoId).orElseThrow();

        assertThat(persisted.status()).isEqualTo(StatusChamado.ABERTO);
        assertThat(persisted.prioridade()).isEqualTo(PrioridadeChamado.MEDIA);
        assertThat(persisted.criadoEm()).isEqualTo(CREATED_AT);
        assertThat(persisted.atualizadoEm()).isEqualTo(CREATED_AT);
        assertThat(persisted.atendente()).isNull();
        assertThat(persisted.solucao()).isNull();
        assertThat(persisted.resolvidoEm()).isNull();
        assertThat(persisted.fechadoEm()).isNull();
        assertThat(persisted.versao()).isZero();
        assertThat(Hibernate.isInitialized(persisted.solicitante())).isFalse();
        assertThat(Hibernate.isInitialized(persisted.categoria())).isFalse();
        assertThat(persisted.solicitante().id()).isEqualTo(solicitanteId);
        assertThat(persisted.categoria().id()).isEqualTo(categoriaId);
    }

    @Test
    void shouldPersistLogicalDeactivationAndAdvanceVersions() {
        Usuario usuario = usuarioRepository.save(newUser("desativar@example.com", Perfil.ADMIN));
        Categoria categoria = categoriaRepository.save(Categoria.nova("Temporaria", null, CREATED_AT));
        entityManager.flush();

        Instant deactivatedAt = CREATED_AT.plusSeconds(300);
        usuario.desativar(deactivatedAt);
        categoria.desativar(deactivatedAt);
        entityManager.flush();
        Long usuarioId = usuario.id();
        Long categoriaId = categoria.id();
        entityManager.clear();

        Usuario persistedUser = usuarioRepository.findById(usuarioId).orElseThrow();
        Categoria persistedCategory = categoriaRepository.findById(categoriaId).orElseThrow();
        assertThat(persistedUser.ativo()).isFalse();
        assertThat(persistedUser.atualizadoEm()).isEqualTo(deactivatedAt);
        assertThat(persistedUser.versao()).isOne();
        assertThat(persistedCategory.ativa()).isFalse();
        assertThat(persistedCategory.atualizadoEm()).isEqualTo(deactivatedAt);
        assertThat(persistedCategory.versao()).isOne();
    }

    @Test
    void shouldEnforceNormalizedEmailUniqueness() {
        usuarioRepository.save(newUser("UNICO@example.com", Perfil.SOLICITANTE));
        entityManager.flush();

        assertThatThrownBy(() -> {
            usuarioRepository.save(newUser("  unico@EXAMPLE.com ", Perfil.ATENDENTE));
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldEnforceCategoryNameUniqueness() {
        categoriaRepository.save(Categoria.nova("Categoria unica", null, CREATED_AT));
        entityManager.flush();

        assertThatThrownBy(() -> {
            categoriaRepository.save(Categoria.nova(
                    "Categoria unica",
                    "Outra categoria com o mesmo nome",
                    CREATED_AT.plusSeconds(1)));
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldEnforceTicketProtocolUniqueness() {
        Usuario solicitante = usuarioRepository.save(newUser("protocolo@example.com", Perfil.SOLICITANTE));
        Categoria categoria = categoriaRepository.save(Categoria.nova("Protocolo", null, CREATED_AT));
        chamadoRepository.save(Chamado.abrir(
                "RES-2026-DUP",
                "Primeiro chamado",
                "Primeira descricao valida",
                solicitante,
                categoria,
                CREATED_AT));
        entityManager.flush();

        assertThatThrownBy(() -> {
            chamadoRepository.save(Chamado.abrir(
                    "RES-2026-DUP",
                    "Segundo chamado",
                    "Segunda descricao valida",
                    solicitante,
                    categoria,
                    CREATED_AT.plusSeconds(1)));
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectInvalidRequesterWithValidCategory() {
        Categoria categoria = categoriaRepository.save(Categoria.nova("FK Solicitante", null, CREATED_AT));
        entityManager.flush();

        assertThatThrownBy(() -> insertTicket(
                "RES-2026-FK-SOL", 999_991L, null, categoria.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectInvalidCategoryWithValidRequester() {
        Usuario solicitante = usuarioRepository.save(newUser("fk-categoria@example.com", Perfil.SOLICITANTE));
        entityManager.flush();

        assertThatThrownBy(() -> insertTicket(
                "RES-2026-FK-CAT", solicitante.id(), null, 999_992L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectInvalidOptionalAttendantWhenPresent() {
        Usuario solicitante = usuarioRepository.save(newUser("fk-atendente@example.com", Perfil.SOLICITANTE));
        Categoria categoria = categoriaRepository.save(Categoria.nova("FK Atendente", null, CREATED_AT));
        entityManager.flush();

        assertThatThrownBy(() -> insertTicket(
                "RES-2026-FK-ATE", solicitante.id(), 999_993L, categoria.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Usuario newUser(String email, Perfil perfil) {
        return Usuario.novo("Pessoa de Teste", email, "hash-ficticio", perfil, CREATED_AT);
    }

    private void insertTicket(String protocolo, long solicitanteId, Long atendenteId, long categoriaId) {
        jdbcTemplate.update("""
                INSERT INTO chamados (
                    protocolo, titulo, descricao, status, prioridade,
                    solicitante_id, atendente_id, categoria_id, criado_em, atualizado_em, versao
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                protocolo, "Teste de FK", "Descricao valida para teste", "ABERTO", "MEDIA",
                solicitanteId, atendenteId, categoriaId, CREATED_AT, CREATED_AT, 0L);
    }
}
